package run.endive.cm.types;

public final class CoreOuterAliasTarget {

    private final long typeIndex;
    private final long sortIndex;

    private CoreOuterAliasTarget(long typeIndex, long sortIndex) {
        this.typeIndex = typeIndex;
        this.sortIndex = sortIndex;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long typeIndex() {
        return typeIndex;
    }

    public long sortIndex() {
        return sortIndex;
    }

    public static final class Builder {

        private long typeIndex;
        private long sortIndex;

        private Builder() {}

        public Builder withTypeIndex(long typeIndex) {
            this.typeIndex = typeIndex;
            return this;
        }

        public Builder withSortIndex(long sortIndex) {
            this.sortIndex = sortIndex;
            return this;
        }

        public CoreOuterAliasTarget build() {
            return new CoreOuterAliasTarget(typeIndex, sortIndex);
        }
    }
}
