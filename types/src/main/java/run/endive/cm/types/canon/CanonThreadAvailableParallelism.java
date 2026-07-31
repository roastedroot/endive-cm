package run.endive.cm.types.canon;

import java.util.Objects;

public final class CanonThreadAvailableParallelism extends Canon {

    private final boolean shared;

    private CanonThreadAvailableParallelism(boolean shared) {
        super(Kind.THREAD_AVAILABLE_PARALLELISM);
        this.shared = shared;
    }

    public boolean isShared() {
        return shared;
    }

    public static CanonThreadAvailableParallelism of(boolean shared) {
        return new CanonThreadAvailableParallelism(shared);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonThreadAvailableParallelism)) {
            return false;
        }
        CanonThreadAvailableParallelism that = (CanonThreadAvailableParallelism) o;
        return shared == that.shared;
    }

    @Override
    public int hashCode() {
        return Objects.hash(shared);
    }

    @Override
    public String toString() {
        return "CanonThreadAvailableParallelism{" + "shared=" + shared + '}';
    }
}
