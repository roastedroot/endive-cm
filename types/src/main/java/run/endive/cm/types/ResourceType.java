package run.endive.cm.types;

import java.util.Objects;
import run.endive.wasm.types.ValType;

public final class ResourceType {

    private final ValType rep;
    private final long dtor;
    private static final long UNDEFINED_DTOR = -1;

    private ResourceType(ValType rep, long dtor) {
        this.rep = Objects.requireNonNull(rep, "rep");
        this.dtor = dtor;
    }

    public ValType rep() {
        return rep;
    }

    public boolean hasDtor() {
        return dtor != UNDEFINED_DTOR;
    }

    public long dtor() {
        return dtor;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ValType rep;
        private long dtor = UNDEFINED_DTOR;

        private Builder() {}

        public Builder withRep(ValType rep) {
            this.rep = rep;
            return this;
        }

        public Builder withDtor(long dtor) {
            this.dtor = dtor;
            return this;
        }

        public ResourceType build() {
            return new ResourceType(rep, dtor);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ResourceType)) {
            return false;
        }
        ResourceType that = (ResourceType) o;
        return dtor == that.dtor && Objects.equals(rep, that.rep);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rep, dtor);
    }

    @Override
    public String toString() {
        return "ResourceType{" + "rep=" + rep + ", dtor=" + dtor + '}';
    }
}
