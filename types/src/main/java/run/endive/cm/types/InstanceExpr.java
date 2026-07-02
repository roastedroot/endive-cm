package run.endive.cm.types;

public abstract class InstanceExpr {

    private final Kind kind;

    protected InstanceExpr(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INSTANTIATE(0x00),
        INLINE_EXPORT(0x01);

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
