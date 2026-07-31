package run.endive.cm.types.canon;

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
        RESOURCE_REP(0x04),
        BACKPRESSURE_SET(0x08),
        BACKPRESSURE_INC(0x24),
        BACKPRESSURE_DEC(0x25),
        TASK_RETURN(0x09),
        TASK_CANCEL(0x05),
        CONTEXT_GET(0x0a),
        CONTEXT_SET(0x0b),
        SUBTASK_CANCEL(0x06),
        SUBTASK_DROP(0x0d),
        STREAM_NEW(0x0e),
        STREAM_READ(0x0f),
        STREAM_WRITE(0x10),
        STREAM_CANCEL_READ(0x11),
        STREAM_CANCEL_WRITE(0x12),
        STREAM_DROP_READABLE(0x13),
        STREAM_DROP_WRITABLE(0x14),
        FUTURE_NEW(0x15),
        FUTURE_READ(0x16),
        FUTURE_WRITE(0x17),
        FUTURE_CANCEL_READ(0x18),
        FUTURE_CANCEL_WRITE(0x19),
        FUTURE_DROP_READABLE(0x1a),
        FUTURE_DROP_WRITABLE(0x1b),
        ERROR_CONTEXT_NEW(0x1c),
        ERROR_CONTEXT_DEBUG_MESSAGE(0x1d),
        ERROR_CONTEXT_DROP(0x1e),
        WAITABLE_SET_NEW(0x1f),
        WAITABLE_SET_WAIT(0x20),
        WAITABLE_SET_POLL(0x21),
        WAITABLE_SET_DROP(0x22),
        WAITABLE_JOIN(0x23),
        THREAD_INDEX(0x26),
        THREAD_NEW_INDIRECT(0x27),
        THREAD_RESUME_LATER(0x28),
        THREAD_SUSPEND(0x29),
        THREAD_YIELD(0x0c),
        THREAD_SUSPEND_THEN_RESUME(0x2a),
        THREAD_YIELD_THEN_RESUME(0x2b),
        THREAD_SUSPEND_THEN_PROMOTE(0x2c),
        THREAD_YIELD_THEN_PROMOTE(0x2d),
        THREAD_SPAWN_REF(0x40),
        THREAD_SPAWN_INDIRECT(0x41),
        THREAD_AVAILABLE_PARALLELISM(0x42);

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
