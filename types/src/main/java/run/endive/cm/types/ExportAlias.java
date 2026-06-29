package run.endive.cm.types;

import java.util.Objects;

public final class ExportAlias extends Alias {

    private final long instanceIdx;
    private final String name;

    private ExportAlias(Sort sort, long instanceIdx, String name) {
        super(Kind.EXPORT, sort);
        Objects.requireNonNull(name, "name");
        this.instanceIdx = instanceIdx;
        this.name = name;
    }

    public long instanceIdx() {
        return instanceIdx;
    }

    public String name() {
        return name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Sort sort;
        private long instanceIdx;
        private String name;

        private Builder() {}

        public Builder withSort(Sort sort) {
            this.sort = sort;
            return this;
        }

        public Builder withInstanceIdx(long instanceIdx) {
            this.instanceIdx = instanceIdx;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public ExportAlias build() {
            return new ExportAlias(sort, instanceIdx, name);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExportAlias)) {
            return false;
        }
        ExportAlias that = (ExportAlias) o;
        return instanceIdx == that.instanceIdx
                && Objects.equals(sort(), that.sort())
                && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sort(), instanceIdx, name);
    }

    @Override
    public String toString() {
        return "ExportAlias{"
                + "sort="
                + sort()
                + ", instanceIdx="
                + instanceIdx
                + ", name="
                + name
                + '}';
    }
}
