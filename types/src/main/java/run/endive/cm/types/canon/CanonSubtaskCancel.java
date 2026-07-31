package run.endive.cm.types.canon;

import java.util.Objects;

public final class CanonSubtaskCancel extends Canon {

    private final boolean async;

    private CanonSubtaskCancel(boolean async) {
        super(Kind.SUBTASK_CANCEL);
        this.async = async;
    }

    public boolean isAsync() {
        return async;
    }

    public static CanonSubtaskCancel of(boolean async) {
        return new CanonSubtaskCancel(async);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonSubtaskCancel)) {
            return false;
        }
        CanonSubtaskCancel that = (CanonSubtaskCancel) o;
        return async == that.async;
    }

    @Override
    public int hashCode() {
        return Objects.hash(async);
    }

    @Override
    public String toString() {
        return "CanonSubtaskCancel{" + "async=" + async + '}';
    }
}
