package run.endive.cm.types;

import java.util.Objects;

public abstract class Canon {

    private final Kind kind;

    protected Canon(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        LIFT(0x00),
        LOWER(0x01),
        RESOURCE_NEW(0x02),
        RESOURCE_DROP(0x03),
        RESOURCE_REP(0x04);

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
        if (!(o instanceof Canon)) {
            return false;
        }
        Canon canon = (Canon) o;
        return kind == canon.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(kind);
    }

    @Override
    public String toString() {
        return "Canon{" + "kind=" + kind + '}';
    }
}
