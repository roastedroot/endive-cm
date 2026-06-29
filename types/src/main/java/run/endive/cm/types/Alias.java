package run.endive.cm.types;

import java.util.Objects;

public abstract class Alias {

    private final Kind kind;
    private final Sort sort;

    protected Alias(Kind kind, Sort sort) {
        Objects.requireNonNull(sort, "sort");
        this.kind = kind;
        this.sort = sort;
    }

    public Kind kind() {
        return kind;
    }

    public Sort sort() {
        return sort;
    }

    public enum Kind {
        EXPORT(0x00),
        CORE_EXPORT(0x01),
        OUTER(0x02);

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
