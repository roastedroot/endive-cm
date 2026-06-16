package run.endive.cm.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.Supplier;

import run.endive.cm.tools.ComponentValidate;
import run.endive.cm.types.SectionId;
import run.endive.cm.types.WasmComponent;
import run.endive.wasm.MalformedException;
import run.endive.wasm.io.InputStreams;
import run.endive.cm.types.Section;

import static java.util.Objects.requireNonNull;
import static run.endive.wasm.Encoding.readVarUInt32;

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
                    throw new UnsupportedOperationException("Custom section is not supported yet");
                }
                case SectionId.CORE_MODULE:
                {
                    throw new UnsupportedOperationException("Core module section is not supported yet");
                }
                case SectionId.CORE_INSTANCE:
                {
                    throw new UnsupportedOperationException("Core instance section is not supported yet");
                }
                case SectionId.CORE_TYPE:
                {
                    throw new UnsupportedOperationException("Core type section is not supported yet");
                }
                case SectionId.COMPONENT:
                {
                    throw new UnsupportedOperationException("Component section is not supported yet");
                }
                case SectionId.INSTANCE:
                {
                    throw new UnsupportedOperationException("Instance section is not supported yet");
                }
                case SectionId.ALIAS:
                {
                    throw new UnsupportedOperationException("Alias section is not supported yet");
                }
                case SectionId.TYPE:
                {
                    throw new UnsupportedOperationException("Type section is not supported yet");
                }
                case SectionId.CANON:
                {
                    throw new UnsupportedOperationException("Canon section is not supported yet");
                }
                case SectionId.START:
                {
                    throw new UnsupportedOperationException("Start section is not supported yet");
                }
                case SectionId.IMPORT:
                {
                    throw new UnsupportedOperationException("Import section is not supported yet");
                }
                case SectionId.EXPORT:
                {
                    throw new UnsupportedOperationException("Export section is not supported yet");
                }
                case SectionId.VALUE:
                {
                    throw new UnsupportedOperationException("Value section is not supported yet");
                }
                default:
                {
                    throw new MalformedException(
                            "section size mismatch, malformed section id " + sectionId);
                }
            }

//            if (sectionByteBuffer.hasRemaining()) {
//                throw new MalformedException("section size mismatch");
//            }
        }
    }

    private static void onSection(WasmComponent.Builder module, Section s) {

    }

    static int readInt(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            throw new MalformedException("length out of bounds");
        }
        return buffer.getInt();
    }

    static byte readByte(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new MalformedException("length out of bounds");
        }
        return buffer.get();
    }

    static void readBytes(ByteBuffer buffer, byte[] dest) {
        if (buffer.remaining() < dest.length) {
            throw new MalformedException("length out of bounds");
        }
        buffer.get(dest);
    }
}
