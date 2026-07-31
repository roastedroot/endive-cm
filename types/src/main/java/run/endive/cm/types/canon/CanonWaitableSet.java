package run.endive.cm.types.canon;

import java.util.List;

public final class CanonWaitableSet extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.WAITABLE_SET_NEW, Kind.WAITABLE_SET_DROP);

    private CanonWaitableSet(Kind kind) {
        super(kind);
    }

    public static CanonWaitableSet of(Kind kind) {
        if (!VALID_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Invalid kind for canon waitable-set: " + kind);
        }
        return new CanonWaitableSet(kind);
    }

    @Override
    public String toString() {
        return "CanonWaitableSet{" + "kind=" + kind() + '}';
    }
}
