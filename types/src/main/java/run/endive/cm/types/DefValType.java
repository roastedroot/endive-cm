package run.endive.cm.types;

public abstract class DefValType {

    private final Kind kind;

    protected DefValType(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        BOOL(0x7F),
        S8(0x7E),
        U8(0x7D),
        S16(0x7C),
        U16(0x7B),
        S32(0x7A),
        U32(0x79),
        S64(0x78),
        U64(0x77),
        F32(0x76),
        F64(0x75),
        CHAR(0x74),
        STRING(0x73),
        ERROR_CONTEXT(0x64),
        RECORD(0x72),
        VARIANT(0x71),
        LIST(0x70),
        SIZED_LIST(0x67),
        TUPLE(0x6F),
        FLAGS(0x6E),
        ENUM(0x6D),
        OPTION(0x6B),
        RESULT(0x6A),
        OWN(0x69),
        BORROW(0x68),
        STREAM(0x66),
        FUTURE(0x65),
        MAP(0x63);

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
}
