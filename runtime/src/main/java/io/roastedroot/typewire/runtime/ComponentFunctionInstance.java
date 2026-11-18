package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.LiftLowerContext;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.ResolvedFuncType;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.ValType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import run.endive.wasm.WasmEngineException;

public final class ComponentFunctionInstance implements ComponentFunction {

    private final ComponentInstance instance;
    private final FuncType funcType;
    private final TypeResolver typeResolver;
    private final ComponentFunctionCall call;
    private final LiftLowerContext liftLowerContext;
    private final CoreFunction<?> liftedFunction;
    private final boolean hostProvided;
    private ResolvedFuncType resolvedFuncType;

    private ComponentFunctionInstance(
            ComponentInstance instance,
            FuncType funcType,
            TypeResolver typeResolver,
            ComponentFunctionCall call,
            LiftLowerContext liftLowerContext,
            CoreFunction<?> liftedFunction,
            boolean hostProvided) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(funcType, "funcType");
        Objects.requireNonNull(typeResolver, "typeResolver");
        Objects.requireNonNull(call, "call");
        this.instance = instance;
        this.funcType = funcType;
        this.typeResolver = typeResolver;
        this.call = call;
        this.liftLowerContext = liftLowerContext;
        this.liftedFunction = liftedFunction;
        this.hostProvided = hostProvided;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ComponentInstance instance;
        private FuncType funcType;
        private TypeResolver typeResolver;
        private ComponentFunctionCall call;
        private LiftLowerContext liftLowerContext;
        private CoreFunction<?> liftedFunction;
        private boolean hostProvided;

        public Builder withInstance(ComponentInstance instance) {
            this.instance = instance;
            return this;
        }

        public Builder withFuncType(FuncType funcType) {
            this.funcType = funcType;
            return this;
        }

        public Builder withTypeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }

        public Builder withCall(ComponentFunctionCall call) {
            this.call = call;
            return this;
        }

        public Builder withLiftLowerContext(LiftLowerContext liftLowerContext) {
            this.liftLowerContext = liftLowerContext;
            return this;
        }

        public Builder withLiftedFunction(CoreFunction<?> liftedFunction) {
            this.liftedFunction = liftedFunction;
            return this;
        }

        /** Marks this as an embedder-supplied function; see {@link #hostProvided()}. */
        public Builder withHostProvided(boolean hostProvided) {
            this.hostProvided = hostProvided;
            return this;
        }

        public ComponentFunctionInstance build() {
            return new ComponentFunctionInstance(
                    instance,
                    funcType,
                    typeResolver,
                    call,
                    liftLowerContext,
                    liftedFunction,
                    hostProvided);
        }
    }

    @Override
    public Object[] apply(Object... args) {
        var params = funcType.params();
        if (args.length != params.size()) {
            throw new WasmEngineException(
                    "Mismatch between number of arguments ("
                            + args.length
                            + ") and number of parameters ("
                            + params.size()
                            + ") in the component function. Expected params "
                            + params);
        }

        Object[] componentArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            LabelValType param = params.get(i);
            HostTypeDescriptor descriptor = descriptorFor(arg.getClass());
            if (!isTypeCompatible(instance, descriptor, param.valType())) {
                throw new WasmEngineException(
                        "Argument '"
                                + arg
                                + "' at position "
                                + i
                                + " is not compatible with parameter "
                                + param);
            }
            componentArgs[i] = descriptor.toComponentValue(arg);
        }
        return call.apply(componentArgs);
    }

    @Override
    public FuncType funcType() {
        return funcType;
    }

    @Override
    public ResolvedFuncType resolvedFuncType() {
        if (resolvedFuncType == null) {
            resolvedFuncType =
                    ResolvedFuncType.of(funcType, ComponentInstance.spaceOf(typeResolver));
        }
        return resolvedFuncType;
    }

    @Override
    public boolean hostProvided() {
        return hostProvided;
    }

    @Override
    public ComponentInstance definingInstance() {
        return instance;
    }

    @Override
    public boolean isLifted() {
        return liftLowerContext != null;
    }

    @Override
    public LiftLowerContext context() {
        return liftLowerContext;
    }

    @Override
    public CoreFunction<?> liftedFunction() {
        return liftedFunction;
    }

    @Override
    public ComponentFunction typed(
            HostTypeDescriptor resultDescriptor, HostTypeDescriptor... paramDescriptors) {
        return new ComponentFunctionInstance.Typed(resultDescriptor, paramDescriptors);
    }

    static boolean isTypeCompatible(
            ComponentInstance instance, Class<?> hostType, ValType componentType) {
        return isTypeCompatible(instance, descriptorFor(hostType), componentType);
    }

    static boolean isTypeCompatible(
            ComponentInstance instance, HostTypeDescriptor hostType, ValType componentType) {
        return hostType.isCompatibleWith(instance, componentType);
    }

    /** The descriptor under which a value of {@code hostType} crosses into a component. */
    static HostTypeDescriptor descriptorFor(Class<?> hostType) {
        if (PrimitiveHostTypeDescriptor.supports(hostType)) {
            return PrimitiveHostTypeDescriptor.forClass(hostType);
        } else if (EnumHostTypeDescriptor.supports(hostType)) {
            return EnumHostTypeDescriptor.forClass(hostType);
        } else if (ResourceHostTypeDescriptor.supports(hostType)) {
            return ResourceHostTypeDescriptor.instance();
        } else if (hostType == byte[].class) {
            return ListHostTypeDescriptor.bytes();
        } else if (ListHostTypeDescriptor.supports(hostType)) {
            return ListHostTypeDescriptor.instance();
        } else if (RecordHostTypeDescriptor.supports(hostType)) {
            return RecordHostTypeDescriptor.instance();
        } else if (VariantHostTypeDescriptor.supports(hostType)) {
            return VariantHostTypeDescriptor.instance();
        }
        throw new IllegalArgumentException(
                "host type " + hostType.getName() + " is not yet supported");
    }

    public final class Typed implements ComponentFunction {

        private final List<HostTypeDescriptor> requiredParamTypes = new ArrayList<>();
        private final HostTypeDescriptor requiredResultType;

        private Typed(HostTypeDescriptor resultDescriptor, HostTypeDescriptor... paramDescriptors) {
            Objects.requireNonNull(resultDescriptor, "resultDescriptor");
            Objects.requireNonNull(paramDescriptors, "paramDescriptors");
            this.requiredResultType = resultDescriptor;
            this.requiredParamTypes.addAll(List.of(paramDescriptors));

            var result = funcType.result();
            if (!isTypeCompatible(instance, resultDescriptor, result)) {
                throw new LinkageException(
                        "Result type descriptor "
                                + resultDescriptor
                                + " is not compatible with the result type "
                                + result
                                + " of the component function.");
            }

            var params = funcType.params();
            if (paramDescriptors.length != params.size()) {
                throw new LinkageException(
                        "Mismatch between number of type descriptors ("
                                + paramDescriptors.length
                                + ") and number of parameters ("
                                + params.size()
                                + ") in the component function. Expected params "
                                + params);
            }

            for (int i = 0; i < paramDescriptors.length; i++) {
                LabelValType param = params.get(i);
                if (!isTypeCompatible(instance, paramDescriptors[i], param.valType())) {
                    throw new LinkageException(
                            "Type descriptor "
                                    + paramDescriptors[i]
                                    + " at position "
                                    + i
                                    + " is not compatible with parameter "
                                    + param);
                }
            }
        }

        @Override
        public Object[] apply(Object... args) {
            if (args.length != requiredParamTypes.size()) {
                throw new WasmEngineException(
                        "Mismatch between number of arguments ("
                                + args.length
                                + ") and number of parameter descriptors ("
                                + requiredParamTypes.size()
                                + ")");
            }
            for (int i = 0; i < requiredParamTypes.size(); i++) {
                var descriptor = requiredParamTypes.get(i);
                if (!descriptor.matches(classOf(args[i]))) {
                    throw new WasmEngineException(
                            "Argument "
                                    + i
                                    + " does not match the type expected by parameter descriptor "
                                    + descriptor
                                    + " for parameter "
                                    + funcType.params().get(i));
                }
            }
            Object[] results = ComponentFunctionInstance.this.apply(args);
            // A component function has at most one result, so the descriptor describes that one
            // value, or its absence, which is what VoidHostTypeDescriptor matches.
            if (results.length == 0) {
                requireResultMatches(null);
                return results;
            }
            Object result = requiredResultType.toHostValue(results[0]);
            requireResultMatches(result);
            return new Object[] {result};
        }

        private void requireResultMatches(Object result) {
            if (!requiredResultType.matches(classOf(result))) {
                throw new WasmEngineException(
                        "Result type "
                                + (result == null ? "none" : result.getClass())
                                + " does not match the type expected by result descriptor "
                                + requiredResultType);
            }
        }

        /** The class to match a value against, {@code null} standing for no value at all. */
        private Class<?> classOf(Object value) {
            return value == null ? null : value.getClass();
        }

        @Override
        public FuncType funcType() {
            return funcType;
        }

        @Override
        public ResolvedFuncType resolvedFuncType() {
            return ComponentFunctionInstance.this.resolvedFuncType();
        }

        @Override
        public ComponentInstance definingInstance() {
            return instance;
        }

        @Override
        public boolean isLifted() {
            return false;
        }

        @Override
        public LiftLowerContext context() {
            return null;
        }

        @Override
        public CoreFunction<?> liftedFunction() {
            return null;
        }

        @Override
        public ComponentFunction typed(
                HostTypeDescriptor resultsDescriptor, HostTypeDescriptor... paramDescriptors) {
            throw new UnsupportedOperationException("A typed function cannot be re-cast");
        }
    }
}
