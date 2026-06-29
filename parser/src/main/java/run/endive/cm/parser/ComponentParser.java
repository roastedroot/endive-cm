package run.endive.cm.parser;

import static java.util.Objects.requireNonNull;
import static run.endive.cm.parser.CoreParser.parseCustomSection;
import static run.endive.cm.parser.CoreParser.parseImport;
import static run.endive.cm.parser.CoreParser.parseRecType;
import static run.endive.wasm.Encoding.readVarUInt32;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;
import run.endive.cm.types.Alias;
import run.endive.cm.types.BorrowType;
import run.endive.cm.types.Case;
import run.endive.cm.types.ComponentDecl;
import run.endive.cm.types.ComponentSection;
import run.endive.cm.types.ComponentType;
import run.endive.cm.types.CoreAlias;
import run.endive.cm.types.CoreExportAlias;
import run.endive.cm.types.CoreExportDecl;
import run.endive.cm.types.CoreImportDecl;
import run.endive.cm.types.CoreSort;
import run.endive.cm.types.CoreType;
import run.endive.cm.types.CoreTypeSection;
import run.endive.cm.types.CustomSection;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.ExportAlias;
import run.endive.cm.types.ExportDecl;
import run.endive.cm.types.ExternDesc;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.FutureType;
import run.endive.cm.types.ImportDecl;
import run.endive.cm.types.InstanceDecl;
import run.endive.cm.types.InstanceType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.MapType;
import run.endive.cm.types.ModuleDecl;
import run.endive.cm.types.ModuleType;
import run.endive.cm.types.OptionType;
import run.endive.cm.types.OuterAlias;
import run.endive.cm.types.OwnType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.RecordType;
import run.endive.cm.types.ResultType;
import run.endive.cm.types.Section;
import run.endive.cm.types.SectionId;
import run.endive.cm.types.Sort;
import run.endive.cm.types.StreamType;
import run.endive.cm.types.TupleType;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeBound;
import run.endive.cm.types.TypeSection;
import run.endive.cm.types.ValType;
import run.endive.cm.types.ValueBound;
import run.endive.cm.types.VariantType;
import run.endive.cm.types.WasmComponent;
import run.endive.wasm.MalformedException;
import run.endive.wasm.io.InputStreams;

public final class ComponentParser {

    static final byte[] MAGIC_BYTES = {0x00, 0x61, 0x73, 0x6D};
    static final byte[] VERSION_BYTES = {0x0d, 0x00};
    static final byte[] LAYER_BYTES = {0x01, 0x00};

    private ComponentParser() {}

    private static ByteBuffer readByteBuffer(InputStream is) {
        try {
            var buffer = ByteBuffer.wrap(InputStreams.readAllBytes(is));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return buffer;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read wasm bytes.", e);
        }
    }

    public static ComponentParser.Builder builder() {
        return new ComponentParser.Builder();
    }

    public static final class Builder {

        private Builder() {}

        public ComponentParser build() {
            return new ComponentParser();
        }
    }

    private static void onSection(WasmComponent.Builder module, Section s) {
        switch (s.sectionId()) {
            case SectionId.CUSTOM:
                var coreCustomSection = (CustomSection) s;
                module.addCoreCustomSection(coreCustomSection);
                break;
            case SectionId.CORE_TYPE:
                var coreTypeSection = (CoreTypeSection) s;
                module.addCoreTypeSection(coreTypeSection);
                break;
            case SectionId.COMPONENT:
                var componentSection = (ComponentSection) s;
                module.addComponentSection(componentSection);
                break;
            case SectionId.TYPE:
                var typeSection = (TypeSection) s;
                module.addTypeSection(typeSection);
                break;
            default:
                throw new MalformedException("unsupported section id " + s.sectionId());
        }
    }

    public WasmComponent parse(Supplier<InputStream> inputStreamSupplier) {
        WasmComponent.Builder componentBuilder = WasmComponent.builder();
        parse(inputStreamSupplier.get(), s -> onSection(componentBuilder, s));
        return componentBuilder.build();
    }

    private void parse(InputStream in, ComponentParserListener listener) {
        requireNonNull(listener, "listener");

        var buffer = readByteBuffer(in);

        parseComponent(listener, buffer);
    }

    private static void parseComponent(ComponentParserListener listener, ByteBuffer buffer) {
        byte[] magic = new byte[4];
        readBytes(buffer, magic);
        if (!Arrays.equals(magic, MAGIC_BYTES)) {
            throw new MalformedException(
                    "magic header not detected, found: "
                            + Arrays.toString(magic)
                            + " expected: "
                            + Arrays.toString(MAGIC_BYTES));
        }

        byte[] version = new byte[2];
        readBytes(buffer, version);
        if (!Arrays.equals(version, VERSION_BYTES)) {
            throw new MalformedException(
                    "unknown binary version, found: "
                            + Arrays.toString(version)
                            + " expected: "
                            + Arrays.toString(VERSION_BYTES));
        }

        byte[] layer = new byte[2];
        readBytes(buffer, layer);
        if (!Arrays.equals(layer, LAYER_BYTES)) {
            throw new MalformedException(
                    "unknown layer, found: "
                            + Arrays.toString(layer)
                            + " expected: "
                            + Arrays.toString(LAYER_BYTES));
        }

        while (buffer.hasRemaining()) {
            var sectionId = readByte(buffer);
            var sectionSize = readVarUInt32(buffer);

            ByteBuffer sectionByteBuffer = buffer.asReadOnlyBuffer();
            sectionByteBuffer.order(buffer.order());

            // move buffer to next section
            var sectionLimit = sectionByteBuffer.position() + (int) sectionSize;
            if (buffer.capacity() < sectionLimit) {
                throw new MalformedException("length out of bounds for section" + sectionId);
            }
            buffer.position(sectionLimit);

            sectionByteBuffer.limit(sectionLimit);

            // Process different section types based on the sectionId
            switch (sectionId) {
                case SectionId.CUSTOM:
                    {
                        var customSection =
                                parseCustomSection(sectionByteBuffer, sectionSize, true);
                        var coreCustomSection =
                                CustomSection.builder().withCustomSection(customSection).build();
                        listener.onSection(coreCustomSection);
                        break;
                    }
                case SectionId.CORE_MODULE:
                    {
                        throw new UnsupportedOperationException(
                                "Core module section is not supported yet");
                    }
                case SectionId.CORE_INSTANCE:
                    {
                        throw new UnsupportedOperationException(
                                "Core instance section is not supported yet");
                    }
                case SectionId.CORE_TYPE:
                    {
                        var coreTypeSection = parseCoreTypeSection(sectionByteBuffer);
                        listener.onSection(coreTypeSection);
                        break;
                    }
                case SectionId.COMPONENT:
                    {
                        var componentSection = parseComponentSection(sectionByteBuffer);
                        listener.onSection(componentSection);
                        break;
                    }
                case SectionId.INSTANCE:
                    {
                        throw new UnsupportedOperationException(
                                "Instance section is not supported yet");
                    }
                case SectionId.ALIAS:
                    {
                        throw new UnsupportedOperationException(
                                "Alias section is not supported yet");
                    }
                case SectionId.TYPE:
                    {
                        var typeSection = parseTypeSection(sectionByteBuffer);
                        listener.onSection(typeSection);
                        break;
                    }
                case SectionId.CANON:
                    {
                        throw new UnsupportedOperationException(
                                "Canon section is not supported yet");
                    }
                case SectionId.START:
                    {
                        throw new UnsupportedOperationException(
                                "Start section is not supported yet");
                    }
                case SectionId.IMPORT:
                    {
                        throw new UnsupportedOperationException(
                                "Import section is not supported yet");
                    }
                case SectionId.EXPORT:
                    {
                        throw new UnsupportedOperationException(
                                "Export section is not supported yet");
                    }
                case SectionId.VALUE:
                    {
                        throw new UnsupportedOperationException(
                                "Value section is not supported yet");
                    }
                default:
                    {
                        throw new MalformedException(
                                "section size mismatch, malformed section id " + sectionId);
                    }
            }

            if (sectionByteBuffer.hasRemaining()) {
                throw new MalformedException("section size mismatch");
            }
        }
    }

    private static CoreTypeSection parseCoreTypeSection(ByteBuffer buffer) {
        var builder = CoreTypeSection.builder();
        var numCoreTypes = readVarUInt32(buffer);
        for (int i = 0; i < numCoreTypes && buffer.hasRemaining(); i++) {
            builder.addCoreType(parseCoreType(buffer));
        }
        return builder.build();
    }

    private static ComponentSection parseComponentSection(ByteBuffer buffer) {
        var componentBuilder = WasmComponent.builder();
        parseComponent(s -> onSection(componentBuilder, s), buffer);
        return ComponentSection.builder().withComponent(componentBuilder.build()).build();
    }

    private static CoreType parseCoreType(ByteBuffer buffer) {
        var opcode = peekByte(buffer);
        switch (opcode) {
            case 0x50:
                buffer.position(buffer.position() + 1);
                return CoreType.of(parseModuleType(buffer));
            case 0x00:
                buffer.position(buffer.position() + 1);
                return CoreType.of(parseRecType(buffer));
            default:
                return CoreType.of(parseRecType(buffer));
        }
    }

    private static ModuleType parseModuleType(ByteBuffer buffer) {
        var builder = ModuleType.builder();
        var numDecls = readVarUInt32(buffer);
        for (int i = 0; i < numDecls && buffer.hasRemaining(); i++) {
            builder.addModuleDecl(parseModuleDecl(buffer));
        }
        return builder.build();
    }

    private static ModuleDecl parseModuleDecl(ByteBuffer buffer) {
        var builder = ModuleDecl.builder();
        var opcode = readByte(buffer);
        switch (opcode) {
            case 0x00:
                builder.withImportDecl(parseCoreImportDecl(buffer));
                break;
            case 0x01:
                builder.withType(parseCoreType(buffer));
                break;
            case 0x02:
                builder.withAlias(parseCoreAlias(buffer));
                break;
            case 0x03:
                builder.withExportDecl(parseCoreExportDecl(buffer));
                break;
            default:
                throw new MalformedException("unknown opcode" + opcode + " in module decl");
        }
        return builder.build();
    }

    private static CoreImportDecl parseCoreImportDecl(ByteBuffer buffer) {
        return CoreImportDecl.builder().withCoreImport(parseImport(buffer)).build();
    }

    private static CoreAlias parseCoreAlias(ByteBuffer buffer) {
        var builder = CoreAlias.builder();
        var sortId = readByte(buffer);
        var sort = CoreSort.fromId(sortId);
        var targetOpcode = readByte(buffer);
        if (targetOpcode != 0x01) {
            throw new MalformedException("unknown target opcode " + targetOpcode + " in alias");
        }
        builder.withSort(sort).withCount(readVarUInt32(buffer)).withIndex(readVarUInt32(buffer));
        return builder.build();
    }

    private static CoreExportDecl parseCoreExportDecl(ByteBuffer buffer) {
        throw new UnsupportedOperationException("export decl parsing not implemented");
    }

    private static TypeSection parseTypeSection(ByteBuffer buffer) {
        var builder = TypeSection.builder();
        var numTypes = readVarUInt32(buffer);
        for (int i = 0; i < numTypes && buffer.hasRemaining(); i++) {
            builder.addType(parseType(buffer));
        }
        return builder.build();
    }

    private static Type parseType(ByteBuffer buffer) {
        var opcode = readByte(buffer);
        var kind = DefValType.Kind.fromOpcode(opcode);
        if (kind != null) {
            return parseDefValType(kind, buffer);
        }
        switch (opcode) {
            case 0x40:
                return Type.of(parseFuncType(buffer, false));
            case 0x43:
                return Type.of(parseFuncType(buffer, true));
            case 0x41:
                return Type.of(parseComponentType(buffer));
            case 0x42:
                return Type.of(parseInstanceType(buffer));
            default:
                throw new MalformedException("unknown type opcode: " + opcode);
        }
    }

    private static Type parseDefValType(DefValType.Kind kind, ByteBuffer buffer) {
        switch (kind) {
            case BOOL:
                return Type.of(PrimValType.BOOL);
            case S8:
                return Type.of(PrimValType.S8);
            case U8:
                return Type.of(PrimValType.U8);
            case S16:
                return Type.of(PrimValType.S16);
            case U16:
                return Type.of(PrimValType.U16);
            case S32:
                return Type.of(PrimValType.S32);
            case U32:
                return Type.of(PrimValType.U32);
            case S64:
                return Type.of(PrimValType.S64);
            case U64:
                return Type.of(PrimValType.U64);
            case F32:
                return Type.of(PrimValType.F32);
            case F64:
                return Type.of(PrimValType.F64);
            case CHAR:
                return Type.of(PrimValType.CHAR);
            case STRING:
                return Type.of(PrimValType.STRING);
            case ERROR_CONTEXT:
                return Type.of(PrimValType.ERROR_CONTEXT);
            case RECORD:
                return Type.of(parseRecordType(buffer));
            case VARIANT:
                return Type.of(parseVariantType(buffer));
            case LIST:
                return Type.of(parseListType(buffer));
            case SIZED_LIST:
                return Type.of(parseSizedListType(buffer));
            case TUPLE:
                return Type.of(parseTupleType(buffer));
            case FLAGS:
                return Type.of(parseFlagsType(buffer));
            case ENUM:
                return Type.of(parseEnumType(buffer));
            case OPTION:
                return Type.of(parseOptionType(buffer));
            case RESULT:
                return Type.of(parseResultType(buffer));
            case OWN:
                return Type.of(parseOwnType(buffer));
            case BORROW:
                return Type.of(parseBorrowType(buffer));
            case STREAM:
                return Type.of(parseStreamType(buffer));
            case FUTURE:
                return Type.of(parseFutureType(buffer));
            case MAP:
                return Type.of(parseMapType(buffer));
            default:
                throw new MalformedException("unknown DefValType kind: " + kind);
        }
    }

    public static FuncType parseFuncType(ByteBuffer buffer, boolean isAsync) {
        var builder = FuncType.builder();
        builder.withAsync(isAsync);
        var numParams = readVarUInt32(buffer);
        for (int i = 0; i < numParams && buffer.hasRemaining(); i++) {
            builder.addParam(parseLabelValType(buffer));
        }
        var resultIndicator = readByte(buffer);
        switch (resultIndicator) {
            case 0x00:
                builder.withResult(parseValType(buffer));
                break;
            case 0x01:
                var voidIndicator = readByte(buffer);
                if (voidIndicator != 0x00) {
                    throw new MalformedException(
                            "unexpected opcode value after void result indicator: "
                                    + voidIndicator);
                }
                break;
            default:
                throw new MalformedException("unknown result opcode: " + resultIndicator);
        }
        return builder.build();
    }

    public static ComponentType parseComponentType(ByteBuffer buffer) {
        var builder = ComponentType.builder();
        var numDecls = readVarUInt32(buffer);
        for (int i = 0; i < numDecls && buffer.hasRemaining(); i++) {
            builder.addComponentDecl(parseComponentDecl(buffer));
        }
        return builder.build();
    }

    private static ComponentDecl parseComponentDecl(ByteBuffer buffer) {
        var opcode = readByte(buffer);
        if (opcode == 0x03) {
            return ComponentDecl.of(parseImportDecl(buffer));
        }
        buffer.position(buffer.position() - 1);
        return ComponentDecl.of(parseInstanceDecl(buffer));
    }

    private static ImportDecl parseImportDecl(ByteBuffer buffer) {
        return ImportDecl.builder()
                .withName(parseExternName(buffer))
                .withExternDesc(parseExternDesc(buffer))
                .build();
    }

    public static InstanceType parseInstanceType(ByteBuffer buffer) {
        var builder = InstanceType.builder();
        var numDecls = readVarUInt32(buffer);
        for (int i = 0; i < numDecls && buffer.hasRemaining(); i++) {
            builder.addInstanceDecl(parseInstanceDecl(buffer));
        }
        return builder.build();
    }

    private static InstanceDecl parseInstanceDecl(ByteBuffer buffer) {
        var opcode = readByte(buffer);
        switch (opcode) {
            case 0x00:
                return InstanceDecl.of(parseCoreType(buffer));
            case 0x01:
                return InstanceDecl.of(parseType(buffer));
            case 0x02:
                return InstanceDecl.of(parseAlias(buffer));
            case 0x04:
                return InstanceDecl.of(parseExportDecl(buffer));
            default:
                throw new MalformedException("unknown opcode " + opcode + " in instance decl");
        }
    }

    private static Alias parseAlias(ByteBuffer buffer) {
        var sort = parseSort(buffer);
        var opcode = readByte(buffer);
        var id = Alias.Kind.fromOpcode(opcode);
        if (id == null) {
            throw new MalformedException("unknown alias target opcode " + opcode);
        }
        switch (id) {
            case EXPORT:
                return ExportAlias.builder()
                        .withSort(sort)
                        .withInstanceIdx(readVarUInt32(buffer))
                        .withName(parseLabel(buffer))
                        .build();
            case CORE_EXPORT:
                return CoreExportAlias.builder()
                        .withSort(sort)
                        .withInstanceIdx(readVarUInt32(buffer))
                        .withName(parseLabel(buffer))
                        .build();
            case OUTER:
                return OuterAlias.builder()
                        .withSort(sort)
                        .withCount(readVarUInt32(buffer))
                        .withIndex(readVarUInt32(buffer))
                        .build();
            default:
                throw new MalformedException("unknown alias target opcode " + opcode);
        }
    }

    private static Sort parseSort(ByteBuffer buffer) {
        var builder = Sort.builder();
        var opcode = readByte(buffer);
        switch (opcode) {
            case 0x00:
                builder.withKind(Sort.Kind.CORE).withCoreSort(CoreSort.fromId(readByte(buffer)));
                break;
            case 0x01:
                builder.withKind(Sort.Kind.FUNC);
                break;
            case 0x02:
                builder.withKind(Sort.Kind.VALUE);
                break;
            case 0x03:
                builder.withKind(Sort.Kind.TYPE);
                break;
            case 0x04:
                builder.withKind(Sort.Kind.COMPONENT);
                break;
            case 0x05:
                builder.withKind(Sort.Kind.INSTANCE);
                break;
            default:
                throw new MalformedException("unknown sort opcode " + opcode);
        }
        return builder.build();
    }

    private static ExportDecl parseExportDecl(ByteBuffer buffer) {
        return ExportDecl.builder()
                .withName(parseExternName(buffer))
                .withExternDesc(parseExternDesc(buffer))
                .build();
    }

    private static String parseExternName(ByteBuffer buffer) {
        var kind = readByte(buffer);
        switch (kind) {
            case 0x00:
            case 0x01:
                return parseLabel(buffer);
            default:
                throw new MalformedException("unsupported extern name kind " + kind);
        }
    }

    private static ExternDesc parseExternDesc(ByteBuffer buffer) {
        var builder = ExternDesc.builder();
        var opcode = readByte(buffer);
        switch (opcode) {
            case 0x00:
                var coreModuleIndicator = readByte(buffer);
                if (coreModuleIndicator != 0x11) {
                    throw new MalformedException(
                            "expected 0x11 core module indicator, found " + coreModuleIndicator);
                }
                builder.withKind(ExternDesc.Kind.CORE_MODULE).withTypeIdx(readVarUInt32(buffer));
                break;
            case 0x01:
                builder.withKind(ExternDesc.Kind.FUNC).withTypeIdx(readVarUInt32(buffer));
                break;
            case 0x02:
                builder.withKind(ExternDesc.Kind.VALUE).withValueBound(parseValueBound(buffer));
                break;
            case 0x03:
                builder.withKind(ExternDesc.Kind.TYPE).withTypeBound(parseTypeBound(buffer));
                break;
            case 0x04:
                builder.withKind(ExternDesc.Kind.COMPONENT).withTypeIdx(readVarUInt32(buffer));
                break;
            case 0x05:
                builder.withKind(ExternDesc.Kind.INSTANCE).withTypeIdx(readVarUInt32(buffer));
                break;
            default:
                throw new MalformedException("unknown externdesc opcode " + opcode);
        }
        return builder.build();
    }

    private static TypeBound parseTypeBound(ByteBuffer buffer) {
        var builder = TypeBound.builder();
        var opcode = readByte(buffer);
        switch (opcode) {
            case 0x00:
                builder.withKind(TypeBound.Kind.EQ).withTypeIdx(readVarUInt32(buffer));
                break;
            case 0x01:
                builder.withKind(TypeBound.Kind.SUB_RESOURCE);
                break;
            default:
                throw new MalformedException("unknown typebound opcode " + opcode);
        }
        return builder.build();
    }

    private static ValueBound parseValueBound(ByteBuffer buffer) {
        var builder = ValueBound.builder();
        var opcode = readByte(buffer);
        switch (opcode) {
            case 0x00:
                builder.withKind(ValueBound.Kind.EQ).withValueIdx(readVarUInt32(buffer));
                break;
            case 0x01:
                builder.withKind(ValueBound.Kind.VAL).withValType(parseValType(buffer));
                break;
            default:
                throw new MalformedException("unknown valuebound opcode " + opcode);
        }
        return builder.build();
    }

    private static RecordType parseRecordType(ByteBuffer buffer) {
        var builder = RecordType.builder();
        var numFields = readVarUInt32(buffer);
        for (int i = 0; i < numFields && buffer.hasRemaining(); i++) {
            builder.addField(parseLabelValType(buffer));
        }
        return builder.build();
    }

    private static VariantType parseVariantType(ByteBuffer buffer) {
        var builder = VariantType.builder();
        var numCases = readVarUInt32(buffer);
        for (int i = 0; i < numCases && buffer.hasRemaining(); i++) {
            builder.addCase(parseCase(buffer));
        }
        return builder.build();
    }

    private static ListType parseListType(ByteBuffer buffer) {
        return ListType.builder().withElementType(parseValType(buffer)).build();
    }

    private static ListType parseSizedListType(ByteBuffer buffer) {
        var elementType = parseValType(buffer);
        var size = readVarUInt32(buffer);
        return ListType.builder().withElementType(elementType).withFixedSize((int) size).build();
    }

    private static TupleType parseTupleType(ByteBuffer buffer) {
        var builder = TupleType.builder();
        var numTypes = readVarUInt32(buffer);
        for (int i = 0; i < numTypes && buffer.hasRemaining(); i++) {
            builder.addElementType(parseValType(buffer));
        }
        return builder.build();
    }

    private static FlagsType parseFlagsType(ByteBuffer buffer) {
        var builder = FlagsType.builder();
        var numLabels = readVarUInt32(buffer);
        for (int i = 0; i < numLabels && buffer.hasRemaining(); i++) {
            builder.addLabel(parseLabel(buffer));
        }
        return builder.build();
    }

    private static EnumType parseEnumType(ByteBuffer buffer) {
        var builder = EnumType.builder();
        var numLabels = readVarUInt32(buffer);
        for (int i = 0; i < numLabels && buffer.hasRemaining(); i++) {
            builder.addLabel(parseLabel(buffer));
        }
        return builder.build();
    }

    private static OptionType parseOptionType(ByteBuffer buffer) {
        return OptionType.builder().withValType(parseValType(buffer)).build();
    }

    private static ResultType parseResultType(ByteBuffer buffer) {
        var builder = ResultType.builder();
        var ok = parseOptionalValType(buffer);
        if (ok != null) {
            builder.withOk(ok);
        }
        var error = parseOptionalValType(buffer);
        if (error != null) {
            builder.withError(error);
        }
        return builder.build();
    }

    private static OwnType parseOwnType(ByteBuffer buffer) {
        return OwnType.builder().withTypeIdx((int) readVarUInt32(buffer)).build();
    }

    private static BorrowType parseBorrowType(ByteBuffer buffer) {
        return BorrowType.builder().withTypeIdx((int) readVarUInt32(buffer)).build();
    }

    private static StreamType parseStreamType(ByteBuffer buffer) {
        var builder = StreamType.builder();
        var elementType = parseOptionalValType(buffer);
        if (elementType != null) {
            builder.withElementType(elementType);
        }
        return builder.build();
    }

    private static FutureType parseFutureType(ByteBuffer buffer) {
        var builder = FutureType.builder();
        var elementType = parseOptionalValType(buffer);
        if (elementType != null) {
            builder.withElementType(elementType);
        }
        return builder.build();
    }

    private static MapType parseMapType(ByteBuffer buffer) {
        var keyType = parseValType(buffer);
        var valueType = parseValType(buffer);
        return MapType.builder().withKeyType(keyType).withValueType(valueType).build();
    }

    private static Case parseCase(ByteBuffer buffer) {
        var builder = Case.builder();
        builder.withLabel(parseLabel(buffer));
        var valType = parseOptionalValType(buffer);
        if (valType != null) {
            builder.withValType(valType);
        }
        var terminator = readByte(buffer);
        if (terminator != 0x00) {
            throw new MalformedException("expected 0x00 terminator in case, found " + terminator);
        }
        return builder.build();
    }

    private static LabelValType parseLabelValType(ByteBuffer buffer) {
        return LabelValType.builder()
                .withLabel(parseLabel(buffer))
                .withValType(parseValType(buffer))
                .build();
    }

    private static ValType parseValType(ByteBuffer buffer) {
        var opcode = peekByte(buffer);
        var kind = DefValType.Kind.fromOpcode(opcode);
        if (kind != null) {
            var primValType = primValTypeForKind(kind);
            if (primValType != null) {
                buffer.position(buffer.position() + 1);
                return ValType.builder().withPrimValType(primValType).build();
            }
        }
        var typeIdx = readVarUInt32(buffer);
        return ValType.builder().withTypeIdx((int) typeIdx).build();
    }

    private static PrimValType primValTypeForKind(DefValType.Kind kind) {
        switch (kind) {
            case BOOL:
                return PrimValType.BOOL;
            case S8:
                return PrimValType.S8;
            case U8:
                return PrimValType.U8;
            case S16:
                return PrimValType.S16;
            case U16:
                return PrimValType.U16;
            case S32:
                return PrimValType.S32;
            case U32:
                return PrimValType.U32;
            case S64:
                return PrimValType.S64;
            case U64:
                return PrimValType.U64;
            case F32:
                return PrimValType.F32;
            case F64:
                return PrimValType.F64;
            case CHAR:
                return PrimValType.CHAR;
            case STRING:
                return PrimValType.STRING;
            case ERROR_CONTEXT:
                return PrimValType.ERROR_CONTEXT;
            default:
                return null;
        }
    }

    private static ValType parseOptionalValType(ByteBuffer buffer) {
        var present = readByte(buffer);
        switch (present) {
            case 0x00:
                return null;
            case 0x01:
                return parseValType(buffer);
            default:
                throw new MalformedException(
                        "expected 0x00 or 0x01 for optional, found " + present);
        }
    }

    private static String parseLabel(ByteBuffer buffer) {
        var length = (int) readVarUInt32(buffer);
        var bytes = new byte[length];
        readBytes(buffer, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static byte readByte(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new MalformedException("length out of bounds");
        }

        return buffer.get();
    }

    static byte peekByte(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new MalformedException("length out of bounds");
        }

        return buffer.get(buffer.position());
    }

    static void readBytes(ByteBuffer buffer, byte[] dest) {
        if (buffer.remaining() < dest.length) {
            throw new MalformedException("length out of bounds");
        }
        buffer.get(dest);
    }
}
