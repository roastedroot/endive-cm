package run.endive.cm.types;

import java.util.Objects;

public final class CoreAlias {

    private final CoreSort sort;
    private final CoreOuterAliasTarget target;

    private CoreAlias(CoreSort sort, CoreOuterAliasTarget target) {
        Objects.requireNonNull(sort, "coreSort");
        Objects.requireNonNull(target, "target");
        this.sort = sort;
        this.target = target;
    }

    public CoreSort sort() {
        return sort;
    }

    public CoreOuterAliasTarget target() {
        return target;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private CoreSort sort;
        private CoreOuterAliasTarget target;

        private Builder() {}

        public Builder withSort(CoreSort sort) {
            this.sort = sort;
            return this;
        }

        public Builder withOuterTarget(CoreOuterAliasTarget target) {
            this.target = target;
            return this;
        }

        public CoreAlias build() {
            return new CoreAlias(sort, target);
        }
    }
}
