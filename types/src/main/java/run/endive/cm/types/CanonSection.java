package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CanonSection extends Section {

    private final List<Canon> canons;

    private CanonSection(List<Canon> canons) {
        super(SectionId.CANON);
        this.canons = List.copyOf(canons);
    }

    public List<Canon> canons() {
        return canons;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Canon> canons = new ArrayList<>();

        public Builder addCanon(Canon canon) {
            Objects.requireNonNull(canon, "canon");
            this.canons.add(canon);
            return this;
        }

        public CanonSection build() {
            return new CanonSection(canons);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonSection)) {
            return false;
        }
        CanonSection that = (CanonSection) o;
        return Objects.equals(canons, that.canons);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(canons);
    }

    @Override
    public String toString() {
        return "CanonSection{" + "canons=" + canons + '}';
    }
}
