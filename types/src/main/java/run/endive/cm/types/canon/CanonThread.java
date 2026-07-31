package run.endive.cm.types.canon;

import java.util.List;

public final class CanonThread extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.THREAD_INDEX, Kind.THREAD_RESUME_LATER);

    private CanonThread(Kind kind) {
        super(kind);
    }

    public static CanonThread of(Kind kind) {
        if (!VALID_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Invalid kind for canon thread: " + kind);
        }
        return new CanonThread(kind);
    }

    @Override
    public String toString() {
        return "CanonThread{" + "kind=" + kind() + '}';
    }
}
