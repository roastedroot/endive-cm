package io.roastedroot.typewire.types;

import java.util.Objects;

public final class ResultType extends DefValType implements Specialized<VariantType> {

    private final ValType ok;
    private final ValType error;

    private ResultType(ValType ok, ValType error) {
        super(Kind.RESULT);
        this.ok = ok;
        this.error = error;
    }

    public ValType ok() {
        return ok;
    }

    public ValType error() {
        return error;
    }

    public boolean hasOk() {
        return ok != null;
    }

    public boolean hasError() {
        return error != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public VariantType despecialize() {
        var okCase = Case.builder().withLabel("ok");
        if (ok != null) {
            okCase.withValType(ok);
        }
        var errorCase = Case.builder().withLabel("error");
        if (error != null) {
            errorCase.withValType(error);
        }
        return VariantType.builder().addCase(okCase.build()).addCase(errorCase.build()).build();
    }

    public static final class Builder {
        private ValType ok;
        private ValType error;

        private Builder() {}

        public Builder withOk(ValType ok) {
            this.ok = ok;
            return this;
        }

        public Builder withError(ValType error) {
            this.error = error;
            return this;
        }

        public ResultType build() {
            return new ResultType(ok, error);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ResultType)) {
            return false;
        }
        ResultType that = (ResultType) o;
        return Objects.equals(ok, that.ok) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, error);
    }

    @Override
    public String toString() {
        return "ResultType{" + "ok=" + ok + ", error=" + error + '}';
    }
}
