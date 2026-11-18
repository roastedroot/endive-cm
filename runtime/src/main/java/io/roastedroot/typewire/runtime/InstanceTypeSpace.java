package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.ResourceTypeRef;
import io.roastedroot.typewire.types.CoreType;
import io.roastedroot.typewire.types.ResolvedFuncType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.TypeSpace;
import io.roastedroot.typewire.types.ValType;

/**
 * The type index space an {@code instance} type declaration builds up as it is read.
 *
 * <p>An instance type is a scope of its own, numbered from zero. Type declarations, type-kinded
 * exports and type aliases occupy the space in the order they appear.
 *
 * <p>A declaration only names a resource type, and matching against a real instance is what settles
 * which. Because a declaration can only refer to earlier ones, each slot is resolved as it is
 * added, by which point every resource type it mentions has been bound.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#type-definitions">Explainer.md, instance types</a>
 */
final class InstanceTypeSpace implements TypeSpace {

    /**
     * The space of the instance type inside which this one is declared, or {@code null} when the
     * declaration sits directly in a component. An {@code alias outer} count is spent on these
     * before it walks the chain of instantiations.
     */
    private final InstanceTypeSpace enclosing;

    private final IndexSpace<Slot> slots = new IndexSpace<>(IndexSpace.Kind.INSTANCE_DECL_TYPE);
    private final IndexSpace<CoreType> coreTypes =
            new IndexSpace<>(IndexSpace.Kind.INSTANCE_DECL_CORE_TYPE);

    InstanceTypeSpace() {
        this(null);
    }

    InstanceTypeSpace(InstanceTypeSpace enclosing) {
        this.enclosing = enclosing;
    }

    /** How many instance type declarations this one is nested inside. */
    int enclosingDepth() {
        int depth = 0;
        for (InstanceTypeSpace s = enclosing; s != null; s = s.enclosing) {
            depth++;
        }
        return depth;
    }

    /** The {@code count}-th enclosing declaration, counting outwards from one. */
    InstanceTypeSpace enclosingAt(int count) {
        InstanceTypeSpace scope = enclosing;
        for (int i = 1; i < count; i++) {
            scope = scope.enclosing;
        }
        return scope;
    }

    @Override
    public ResolvedType resolve(ValType valType) {
        if (valType.primValType() != null) {
            return ResolvedType.of(valType.primValType(), this);
        }
        Slot slot = slotAt(valType.typeIdx());
        if (slot.value == null) {
            throw new LinkageException(
                    "Instance type index "
                            + valType.typeIdx()
                            + " must resolve to a value type but got "
                            + slot.type);
        }
        return slot.value;
    }

    @Override
    public ResourceTypeRef resourceType(int typeIdx) {
        Slot slot = slotAt(typeIdx);
        if (slot.resourceType == null) {
            throw new LinkageException(
                    "Instance type index " + typeIdx + " does not name a resource type");
        }
        return slot.resourceType;
    }

    Slot slotAt(int index) {
        return slots.at(index);
    }

    /** Core types occupy an index space of their own, which a {@code core module} export uses. */
    void addCoreType(CoreType coreType) {
        coreTypes.add(coreType);
    }

    CoreType coreTypeAt(int index) {
        return coreTypes.at(index);
    }

    /** Appends a type the instance type declares itself, whose indices count in this space. */
    void addLocalType(Type type) {
        slots.add(slot(type, this, null));
    }

    /** Appends a type drawn from another space, which is resolved there rather than here. */
    void addForeignType(Type type, TypeSpace space, ResourceTypeRef resourceType) {
        slots.add(slot(type, space, resourceType));
    }

    /** Appends a slot taken whole from another space, which needs no re-resolution. */
    void addSlot(ResolvedTypeSlot slot) {
        slots.add(new Slot(slot.type(), slot.resourceType(), slot.value(), slot.func()));
    }

    private static Slot slot(Type type, TypeSpace space, ResourceTypeRef resourceType) {
        return new Slot(
                type,
                resourceType,
                type.defValType() == null ? null : ResolvedType.of(type.defValType(), space),
                ResolvedFuncType.of(type.funcType(), space));
    }

    /** One numbered slot: the declaration, what it may name, and the declaration resolved. */
    static final class Slot implements ResolvedTypeSlot {

        private final Type type;
        private final ResourceTypeRef resourceType;
        private final ResolvedType value;
        private final ResolvedFuncType func;

        private Slot(
                Type type,
                ResourceTypeRef resourceType,
                ResolvedType value,
                ResolvedFuncType func) {
            this.type = type;
            this.resourceType = resourceType;
            this.value = value;
            this.func = func;
        }

        @Override
        public Type type() {
            return type;
        }

        /** The resource type this slot names, or {@code null} if it holds an ordinary type. */
        @Override
        public ResourceTypeRef resourceType() {
            return resourceType;
        }

        /** The resolved value type, or {@code null} if this slot holds something else. */
        @Override
        public ResolvedType value() {
            return value;
        }

        /** The resolved function type, or {@code null} if this slot holds something else. */
        @Override
        public ResolvedFuncType func() {
            return func;
        }
    }
}
