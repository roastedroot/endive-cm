package run.endive.cm.types;

public abstract class DefValType {

    private final int id;

    protected DefValType(int id) {
        this.id = id;
    }

    public static final class ID {
        private ID() {}

        public static final int BOOL = 0x7F;
        public static final int S8 = 0x7E;
        public static final int U8 = 0x7D;
        public static final int S16 = 0x7C;
        public static final int U16 = 0x7B;
        public static final int S32 = 0x7A;
        public static final int U32 = 0x79;
        public static final int S64 = 0x78;
        public static final int U64 = 0x77;
        public static final int F32 = 0x76;
        public static final int F64 = 0x75;
        public static final int CHAR = 0x74;
        public static final int STRING = 0x73;
        public static final int ERROR_CONTEXT = 0x64;
        public static final int RECORD = 0x72;
        public static final int VARIANT = 0x71;
        public static final int LIST = 0x70;
        public static final int SIZED_LIST = 0x67;
        public static final int TUPLE = 0x6F;
        public static final int FLAGS = 0x6E;
        public static final int ENUM = 0x6D;
        public static final int OPTION = 0x6B;
        public static final int RESULT = 0x6A;
        public static final int OWN = 0x69;
        public static final int BORROW = 0x68;
        public static final int STREAM = 0x66;
        public static final int FUTURE = 0x65;
        public static final int MAP = 0x63;

        public static boolean isValidOpcode(int opcode) {
            return (opcode == BOOL
                    || opcode == S8
                    || opcode == U8
                    || opcode == S16
                    || opcode == U16
                    || opcode == S32
                    || opcode == U32
                    || opcode == S64
                    || opcode == U64
                    || opcode == F32
                    || opcode == F64
                    || opcode == CHAR
                    || opcode == STRING
                    || opcode == ERROR_CONTEXT
                    || opcode == RECORD
                    || opcode == VARIANT
                    || opcode == LIST
                    || opcode == SIZED_LIST
                    || opcode == TUPLE
                    || opcode == FLAGS
                    || opcode == ENUM
                    || opcode == OPTION
                    || opcode == RESULT
                    || opcode == OWN
                    || opcode == BORROW
                    || opcode == STREAM
                    || opcode == FUTURE
                    || opcode == MAP);
        }
    }
}
