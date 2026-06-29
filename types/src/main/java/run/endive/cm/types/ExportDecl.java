package run.endive.cm.types;

import java.util.Objects;

public final class ExportDecl {

    private final String name;
    private final ExternDesc externDesc;

    private ExportDecl(String name, ExternDesc externDesc) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(externDesc, "externDesc");
        this.name = name;
        this.externDesc = externDesc;
    }

    public String name() {
        return name;
    }

    public ExternDesc externDesc() {
        return externDesc;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private ExternDesc externDesc;

        private Builder() {}

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withExternDesc(ExternDesc externDesc) {
            this.externDesc = externDesc;
            return this;
        }

        public ExportDecl build() {
            return new ExportDecl(name, externDesc);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExportDecl)) {
            return false;
        }
        ExportDecl that = (ExportDecl) o;
        return Objects.equals(name, that.name) && Objects.equals(externDesc, that.externDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, externDesc);
    }

    @Override
    public String toString() {
        return "ExportDecl{" + "name=" + name + ", externDesc=" + externDesc + '}';
    }
}
