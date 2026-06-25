package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FuncType {

    private final List<LabelValType> params;
    private final ValType result;
    private final boolean async;

    private FuncType(List<LabelValType> params, ValType result, boolean async) {
        this.params = List.copyOf(params);
        this.result = result;
        this.async = async;
    }

    public List<LabelValType> params() {
        return params;
    }

    public ValType result() {
        return result;
    }

    public boolean hasResult() {
        return result != null;
    }

    public boolean isAsync() {
        return async;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<LabelValType> params = new ArrayList<>();
        private ValType result;
        private boolean async;

        private Builder() {}

        public Builder addParam(LabelValType param) {
            params.add(requireNonNull(param, "param"));
            return this;
        }

        public Builder withResult(ValType result) {
            this.result = result;
            return this;
        }

        public Builder withAsync(boolean async) {
            this.async = async;
            return this;
        }

        public FuncType build() {
            return new FuncType(params, result, async);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FuncType)) {
            return false;
        }
        FuncType funcType = (FuncType) o;
        return async == funcType.async
                && Objects.equals(params, funcType.params)
                && Objects.equals(result, funcType.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(params, result, async);
    }

    @Override
    public String toString() {
        return "FuncType{" + "params=" + params + ", result=" + result + ", async=" + async + '}';
    }
}
