package run.endive.cm.parser;

import static run.endive.cm.parser.ComponentParser.readByte;
import static run.endive.cm.parser.ComponentParser.readBytes;
import static run.endive.wasm.Encoding.readName;
import static run.endive.wasm.Encoding.readVarSInt32;
import static run.endive.wasm.Encoding.readVarUInt32;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import run.endive.wasm.InvalidException;
import run.endive.wasm.MalformedException;
import run.endive.wasm.types.ArrayType;
import run.endive.wasm.types.CompType;
import run.endive.wasm.types.CustomSection;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.FieldType;
import run.endive.wasm.types.FunctionImport;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.GlobalImport;
import run.endive.wasm.types.Import;
import run.endive.wasm.types.MemoryImport;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.PackedType;
import run.endive.wasm.types.RecType;
import run.endive.wasm.types.StorageType;
import run.endive.wasm.types.StructType;
import run.endive.wasm.types.SubType;
import run.endive.wasm.types.TableImport;
import run.endive.wasm.types.TableLimits;
import run.endive.wasm.types.TagImport;
import run.endive.wasm.types.UnknownCustomSection;
import run.endive.wasm.types.ValType;

public final class CoreParser {

    private CoreParser() {}

    static CustomSection parseCustomSection(
            ByteBuffer buffer, long sectionSize, boolean checkMalformed) {
        var sectionPos = buffer.position();
        var name = readName(buffer, checkMalformed);
        var size = (sectionSize - (buffer.position() - sectionPos));
        if (size < 0) {
            throw new MalformedException("unexpected end");
        }
        var bytes = new byte[(int) size];
        readBytes(buffer, bytes);
        return UnknownCustomSection.builder().withName(name).withBytes(bytes).build();
    }

    static FieldType parseFieldType(ByteBuffer buffer) {
        var id = (int) readVarUInt32(buffer);

        if (id == PackedType.I8.ID() || id == PackedType.I16.ID()) {
            var packedType = PackedType.fromId(id);
            var mut = MutabilityType.forId(readByte(buffer));
            return FieldType.builder()
                    .withStorageType(StorageType.builder().withPackedType(packedType).build())
                    .withMutability(mut)
                    .build();
        } else {
            var valType = readValueTypeBuilderFromOpCode(buffer, id).build();
            var mut = MutabilityType.forId(readByte(buffer));

            return FieldType.builder()
                    .withStorageType(StorageType.builder().withValType(valType).build())
                    .withMutability(mut)
                    .build();
        }
    }

    static ArrayType parseArrayType(ByteBuffer buffer) {
        return ArrayType.builder().withFieldType(parseFieldType(buffer)).build();
    }

    static StructType parseStructType(ByteBuffer buffer) {
        var count = (int) readVarUInt32(buffer);
        var builder = StructType.builder();
        for (int i = 0; i < count; i++) {
            builder.addFieldType(parseFieldType(buffer));
        }
        return builder.build();
    }

    static FunctionType parseFunctionType(ByteBuffer buffer) {
        var paramCount = (int) readVarUInt32(buffer);
        List<ValType> paramsBuilder = new ArrayList<>(paramCount);

        // Parse parameter types
        for (int j = 0; j < paramCount; j++) {
            paramsBuilder.add(readValueTypeBuilder(buffer).build());
        }

        var returnCount = (int) readVarUInt32(buffer);
        List<ValType> returnsBuilder = new ArrayList<>(returnCount);

        // Parse return types
        for (int j = 0; j < returnCount; j++) {
            returnsBuilder.add(readValueTypeBuilder(buffer).build());
        }

        return FunctionType.of(paramsBuilder, returnsBuilder);
    }

    static CompType parseCompType(int id, ByteBuffer buffer) {
        if (id > Byte.MAX_VALUE) {
            throw new MalformedException("integer representation too long");
        }

        switch (id) {
            case 0x5E:
                return CompType.builder().withArrayType(parseArrayType(buffer)).build();
            case 0x5F:
                return CompType.builder().withStructType(parseStructType(buffer)).build();
            case 0x60:
                return CompType.builder().withFuncType(parseFunctionType(buffer)).build();
            default:
                throw new MalformedException(
                        "Invalid composite type. Form "
                                + String.format("0x%02X", id)
                                + " was not 0x5E, 0x5f or 0x60");
        }
    }

    static SubType parseSubType(int id, ByteBuffer buffer) {
        if (id == 0x50 // non final typeIdx
                || id == 0x4F // final typeIdx
        ) {
            var count = (int) readVarUInt32(buffer);
            var typeIdxs = new int[count];

            for (int i = 0; i < count; i++) {
                typeIdxs[i] = (int) readVarUInt32(buffer);
            }
            return SubType.builder()
                    .withTypeIdx(typeIdxs)
                    .withFinal(id == 0x4F)
                    .withCompType(parseCompType((int) readVarUInt32(buffer), buffer))
                    .build();
        } else {
            // fallback to the compressed form
            return SubType.builder()
                    .withTypeIdx(new int[0])
                    .withFinal(true)
                    .withCompType(parseCompType(id, buffer))
                    .build();
        }
    }

    static RecType parseRecType(ByteBuffer buffer) {
        var discriminator = (int) readVarUInt32(buffer);
        if (discriminator == 0x4E) {
            var count = (int) readVarUInt32(buffer);
            var subTypes = new SubType[count];

            for (int i = 0; i < count; i++) {
                subTypes[i] = parseSubType((int) readVarUInt32(buffer), buffer);
            }
            return RecType.builder().withSubTypes(subTypes).build();
        } else {
            // fallback to the compressed form
            return RecType.builder()
                    .withSubTypes(new SubType[] {parseSubType(discriminator, buffer)})
                    .build();
        }
    }

    static Import parseImport(ByteBuffer buffer) {
        String moduleName = readName(buffer);
        String importName = readName(buffer);
        ExternalType descType;
        try {
            descType = ExternalType.byId((int) readVarUInt32(buffer));
        } catch (RuntimeException e) {
            throw new MalformedException("malformed import kind", e);
        }
        switch (descType) {
            case FUNCTION:
                {
                    return new FunctionImport(moduleName, importName, (int) readVarUInt32(buffer));
                }
            case TABLE:
                {
                    var rawTableType = readValueTypeBuilder(buffer).build();

                    var limitType = readByte(buffer);
                    var min = (int) readVarUInt32(buffer);
                    TableLimits limits = null;
                    switch (limitType) {
                        case 0x00:
                            limits = new TableLimits(min);
                            break;
                        case 0x01:
                        case 0x03:
                            limits = new TableLimits(min, readVarUInt32(buffer), limitType == 0x03);
                            break;
                        default:
                            throw new MalformedException(
                                    "integer too large, invalid table limit: " + limitType);
                    }

                    return new TableImport(moduleName, importName, rawTableType, limits);
                }
            case MEMORY:
                {
                    var limits = parseMemoryLimits(buffer);
                    return new MemoryImport(moduleName, importName, limits);
                }
            case GLOBAL:
                var globalValType = readValueTypeBuilder(buffer).build();
                var globalMut = MutabilityType.forId(readByte(buffer));
                return new GlobalImport(moduleName, importName, globalMut, globalValType);
            case TAG:
                try {
                    var attribute = readByte(buffer);
                    var tagTypeIdx = (int) readVarUInt32(buffer);
                    return new TagImport(moduleName, importName, attribute, tagTypeIdx);
                } catch (MalformedException e) {
                    throw new MalformedException("malformed import kind", e);
                }
            default:
                throw new MalformedException("malformed import kind");
        }
    }

    private static MemoryLimits parseMemoryLimits(ByteBuffer buffer) {
        var limitType = readByte(buffer);
        var initial = (int) readVarUInt32(buffer);
        switch (limitType) {
            case 0x00:
                return new MemoryLimits(initial);
            case 0x01:
            case 0x03:
                int maximum = (int) readVarUInt32(buffer);
                return new MemoryLimits(initial, maximum, limitType == 0x03);
            case 0x02:
                throw new InvalidException("shared memory must have maximum");
            default:
                if (limitType > 0) {
                    throw new MalformedException(
                            "integer too large, invalid memory limit: " + limitType);
                } else {
                    throw new MalformedException("integer representation too long: " + limitType);
                }
        }
    }

    static ValType.Builder readValueTypeBuilderFromOpCode(ByteBuffer buffer, int valueTypeOpCode) {
        var builder = ValType.builder().withOpcode(valueTypeOpCode);
        if (valueTypeOpCode == ValType.ID.Ref || valueTypeOpCode == ValType.ID.RefNull) {
            return builder.withTypeIdx((int) readVarSInt32(buffer));
        } else {
            return builder;
        }
    }

    static ValType.Builder readValueTypeBuilder(ByteBuffer buffer) {
        var valueTypeOpCode = (int) readVarUInt32(buffer);

        return readValueTypeBuilderFromOpCode(buffer, valueTypeOpCode);
    }
}
