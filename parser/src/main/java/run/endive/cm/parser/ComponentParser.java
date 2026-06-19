package run.endive.cm.parser;

import static java.util.Objects.requireNonNull;
import static run.endive.cm.parser.CoreParser.parseCustomSection;
import static run.endive.cm.parser.CoreParser.parseRecType;
import static run.endive.wasm.Encoding.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

import run.endive.cm.tools.ComponentValidate;
import run.endive.cm.types.*;
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
        switch(s.sectionId())
        {
            case SectionId.CUSTOM:
                var coreCustomSection = (CustomSection) s;
                module.addCoreCustomSection(coreCustomSection);
                break;
            case SectionId.CORE_TYPE:
                var coreTypeSection = (CoreTypeSection) s;
                module.addCoreTypeSection(coreTypeSection);
                break;
            default:
                throw new MalformedException(
                        "unsupported section id " + s.sectionId());
        }
    }

    public WasmComponent parse(Supplier<InputStream> inputStreamSupplier) {
        WasmComponent.Builder componentBuilder = WasmComponent.builder();
        parse(inputStreamSupplier.get(), s -> onSection(componentBuilder, s));
        return componentBuilder.build();
    }

    private void parse(InputStream in, ComponentParserListener listener) {
        requireNonNull(listener, "listener");

        ComponentValidate.validate(in);
        try {
            in.reset();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var buffer = readByteBuffer(in);

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
                        var customSection = parseCustomSection(sectionByteBuffer, sectionSize, true);
                        var coreCustomSection = CustomSection.builder().withCustomSection(customSection).build();
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
                        throw new UnsupportedOperationException(
                                "Component section is not supported yet");
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
                        throw new UnsupportedOperationException(
                                "Type section is not supported yet");
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

    private static CoreType parseCoreType(ByteBuffer buffer) {
        var typeBuilder = CoreType.builder();
        var opcode = peekByte(buffer);
        switch(opcode) {
            case 0x50:
                buffer.position(buffer.position() + 1);
                typeBuilder.withModuleType(parseModuleType(buffer));
                break;
            case 0x00:
                buffer.position(buffer.position() + 1);
            default:
                typeBuilder.withRecType(parseRecType(buffer));
        }
        return typeBuilder.build();
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
        throw new UnsupportedOperationException("import decl parsing not implemented");
    }

    private static CoreAlias parseCoreAlias(ByteBuffer buffer) {
        var builder = CoreAlias.builder();
        var sortId = readByte(buffer);
        var sort = CoreSort.fromId(sortId);
        var targetOpcode = readByte(buffer);
        if (targetOpcode != 0x01) {
            throw new MalformedException("unknown target opcode " + targetOpcode + " in alias");
        }
        var typeIndex = readVarUInt32(buffer);
        var sortIndex = readVarUInt32(buffer);
        builder.withSort(sort);
        builder.withOuterTarget(CoreOuterAliasTarget.builder()
                .withTypeIndex(typeIndex)
                .withSortIndex(sortIndex)
                .build());
        return builder.build();
    }

    private static CoreExportDecl parseCoreExportDecl(ByteBuffer buffer) {
        throw new UnsupportedOperationException("export decl parsing not implemented");
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
