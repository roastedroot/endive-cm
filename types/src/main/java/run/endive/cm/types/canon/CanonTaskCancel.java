package run.endive.cm.types.canon;

public final class CanonTaskCancel extends Canon {

    public CanonTaskCancel() {
        super(Kind.TASK_CANCEL);
    }

    @Override
    public String toString() {
        return "CanonTaskCancel{}";
    }
}
