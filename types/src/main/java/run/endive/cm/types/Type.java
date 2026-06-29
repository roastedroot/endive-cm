package run.endive.cm.types;

import java.util.Objects;

public final class Type {

    private final DefValType defValType;

    private final FuncType funcType;

    private final ComponentType componentType;

    private final InstanceType instanceType;

    private final ResourceType resourceType;

    private Type(
            DefValType defValType,
            FuncType funcType,
            ComponentType componentType,
            InstanceType instanceType,
            ResourceType resourceType) {
        this.defValType = defValType;
        this.funcType = funcType;
        this.componentType = componentType;
        this.instanceType = instanceType;
        this.resourceType = resourceType;
    }

    public static Type of(DefValType defValType) {
        return new Type(defValType, null, null, null, null);
    }

    public static Type of(FuncType funcType) {
        return new Type(null, funcType, null, null, null);
    }

    public static Type of(ComponentType componentType) {
        return new Type(null, null, componentType, null, null);
    }

    public static Type of(InstanceType instanceType) {
        return new Type(null, null, null, instanceType, null);
    }

    public static Type of(ResourceType resourceType) {
        return new Type(null, null, null, null, resourceType);
    }

    public DefValType defValType() {
        return defValType;
    }

    public FuncType funcType() {
        return funcType;
    }

    public ComponentType componentType() {
        return componentType;
    }

    public InstanceType instanceType() {
        return instanceType;
    }

    public ResourceType resourceType() {
        return resourceType;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Type)) {
            return false;
        }
        Type type = (Type) o;
        return Objects.equals(defValType, type.defValType)
                && Objects.equals(funcType, type.funcType)
                && Objects.equals(componentType, type.componentType)
                && Objects.equals(instanceType, type.instanceType)
                && Objects.equals(resourceType, type.resourceType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defValType, funcType, componentType, instanceType, resourceType);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Type{");
        if (defValType != null) {
            builder.append("defValType=").append(defValType);
        }
        if (funcType != null) {
            builder.append("funcType=").append(funcType);
        }
        if (componentType != null) {
            builder.append("componentType=").append(componentType);
        }
        if (instanceType != null) {
            builder.append("instanceType=").append(instanceType);
        }
        if (resourceType != null) {
            builder.append("resourceType=").append(resourceType);
        }
        builder.append('}');
        return builder.toString();
    }
}
