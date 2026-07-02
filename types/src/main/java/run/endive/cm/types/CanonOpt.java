package run.endive.cm.types;

import java.util.List;
import java.util.Objects;

public final class CanonOpt {

    private final long index;

    private final Kind kind;

    private static final long UNDEFINED_INDEX = -1;

    private static final List<Kind> INDEXED_KINDS =
            List.of(Kind.MEMORY, Kind.REALLOC, Kind.POST_RETURN, Kind.CALLBACK);

    private CanonOpt(long index, Kind kind) {
        if (index == UNDEFINED_INDEX && INDEXED_KINDS.contains(kind)) {
            throw new IllegalArgumentException("index must be provided for CanonOpt " + kind);
        }
        this.index = index;
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public long index() {
        return index;
    }

    public static final class Builder {
        private long index = UNDEFINED_INDEX;
        private Kind kind;

        public Builder withIndex(long index) {
            this.index = index;
            return this;
        }

        public Builder withKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public CanonOpt build() {
            return new CanonOpt(index, kind);
        }
    }

    public enum Kind {
        STRING_ENCODING_UTF8(0x00),
        STRING_ENCODING_UTF16(0x01),
        STRING_ENCODING_LATIN1_UTF16(0x02),
        MEMORY(0x03),
        REALLOC(0x04),
        POST_RETURN(0x05),
        ASYNC(0x06),
        CALLBACK(0x07);

        private final int opcode;

        Kind(int opcode) {
            this.opcode = opcode;
        }

        public int opcode() {
            return opcode;
        }

        public static Kind fromOpcode(int opcode) {
            for (Kind kind : values()) {
                if (kind.opcode == opcode) {
                    return kind;
                }
            }
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonOpt)) {
            return false;
        }
        CanonOpt canonOpt = (CanonOpt) o;
        return index == canonOpt.index && kind == canonOpt.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, kind);
    }

    @Override
    public String toString() {
        return "CanonOpt{" + "index=" + index + ", kind=" + kind + '}';
    }
}
