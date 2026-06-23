package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ModuleType {

    private final List<ModuleDecl> moduleDecls;

    private ModuleType(List<ModuleDecl> moduleDecls) {
        this.moduleDecls = List.copyOf(moduleDecls);
    }

    public List<ModuleDecl> getModuleDecls() {
        return moduleDecls;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ModuleDecl> moduleDecls = new ArrayList<>();

        public Builder addModuleDecl(ModuleDecl moduleDecl) {
            moduleDecls.add(moduleDecl);
            return this;
        }

        public ModuleType build() {
            return new ModuleType(moduleDecls);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModuleType)) {
            return false;
        }
        ModuleType that = (ModuleType) o;
        return Objects.equals(moduleDecls, that.moduleDecls);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(moduleDecls);
    }

    @Override
    public String toString() {
        return "ModuleType{" + "moduleDecls=" + moduleDecls + '}';
    }
}
