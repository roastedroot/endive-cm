package run.endive.cm.types.canon;

import java.util.List;

public final class CanonBackpressure extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.BACKPRESSURE_SET, Kind.BACKPRESSURE_INC, Kind.BACKPRESSURE_DEC);

    private CanonBackpressure(Kind kind) {
        super(kind);
    }

    public static CanonBackpressure of(Kind kind) {
        if (!VALID_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Invalid kind for canon backpressure: " + kind);
        }
        return new CanonBackpressure(kind);
    }

    @Override
    public String toString() {
        return "CanonBackpressure{" + "kind=" + kind() + '}';
    }
}
