package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import run.endive.wasm.types.CompType;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.RecType;
import run.endive.wasm.types.SubType;

public final class CoreTypeSection extends Section {
    private final List<CoreType> coreTypes;

    private CoreTypeSection(List<CoreType> coreTypes) {
        super(SectionId.CORE_TYPE);
        this.coreTypes = List.copyOf(coreTypes);
    }

    public int numCoreTypes() {
        return coreTypes.size();
    }

    public List<CoreType> coreTypes() {
        return coreTypes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<CoreType> coreTypes = new ArrayList<>();

        private Builder() {}

        public Builder addCoreType(CoreType coreType) {
            coreTypes.add(requireNonNull(coreType, "coreType"));
            return this;
        }

        public Builder addFunctionType(FunctionType functionType) {
            Objects.requireNonNull(functionType, "functionType");
            var type =
                    RecType.builder()
                            .withSubTypes(
                                    new SubType[] {
                                        SubType.builder()
                                                .withTypeIdx(new int[] {})
                                                .withFinal(true)
                                                .withCompType(
                                                        CompType.builder()
                                                                .withFuncType(functionType)
                                                                .build())
                                                .build()
                                    })
                            .build();
            coreTypes.add(CoreType.of(type));
            return this;
        }

        public CoreTypeSection build() {
            return new CoreTypeSection(coreTypes);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreTypeSection)) {
            return false;
        }
        CoreTypeSection that = (CoreTypeSection) o;
        return Objects.equals(coreTypes, that.coreTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(coreTypes);
    }

    @Override
    public String toString() {
        return "CoreTypeSection{" + "coreTypes=" + coreTypes + '}';
    }
}
