package run.endive.cm.runtime;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import run.endive.cm.abi.BorrowScope;
import run.endive.cm.abi.HandleTable;
import run.endive.cm.abi.ResourceTypeRef;
import run.endive.cm.types.CoreType;
import run.endive.cm.types.ResolvedFuncType;
import run.endive.cm.types.ResolvedType;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeResolver;
import run.endive.cm.types.TypeSpace;
import run.endive.runtime.GlobalInstance;
import run.endive.runtime.Memory;
import run.endive.runtime.TableInstance;
import run.endive.runtime.TagInstance;
import run.endive.runtime.TrapException;
import run.endive.wasm.WasmModule;

/**
 * One instantiation of a component, holding its index spaces, its exports, and the runtime state a
 * call into it touches.
 *
 * <p>Every instance belongs to a {@link ComponentStore}, which is what lets two of them interact.
 * Nothing here is externally mutable. Definitions go in through {@link Builder} and the index
 * spaces are sealed once {@link Builder#build()} returns.
 */
public final class ComponentInstance implements TypeResolver {

    private final ComponentStore store;
    private final ComponentInstance parent;
    private final ComponentInstance lexicalScope;
    private final TypeSpace typeSpace = new InstanceSpace();

    private final IndexSpace<WasmModule> coreModules =
            new IndexSpace<>(IndexSpace.Kind.CORE_MODULE);
    private final IndexSpace<CoreType> coreTypes = new IndexSpace<>(IndexSpace.Kind.CORE_TYPE);
    private final IndexSpace<CoreModuleInstance> coreInstances =
            new IndexSpace<>(IndexSpace.Kind.CORE_INSTANCE);
    private final IndexSpace<CoreFunction<?>> coreFunctions =
            new IndexSpace<>(IndexSpace.Kind.CORE_FUNCTION);
    private final IndexSpace<Memory> coreMemories = new IndexSpace<>(IndexSpace.Kind.CORE_MEMORY);
    private final IndexSpace<TableInstance> coreTables =
            new IndexSpace<>(IndexSpace.Kind.CORE_TABLE);
    private final IndexSpace<GlobalInstance> coreGlobals =
            new IndexSpace<>(IndexSpace.Kind.CORE_GLOBAL);
    private final IndexSpace<TagInstance> coreTags = new IndexSpace<>(IndexSpace.Kind.CORE_TAG);
    private final IndexSpace<ComponentFunction> functions =
            new IndexSpace<>(IndexSpace.Kind.FUNCTION);
    private final IndexSpace<TypeSlot> typeSlots = new IndexSpace<>(IndexSpace.Kind.TYPE);
    private final IndexSpace<ComponentClosure> childComponents =
            new IndexSpace<>(IndexSpace.Kind.COMPONENT);
    private final IndexSpace<ComponentInstance> childInstances =
            new IndexSpace<>(IndexSpace.Kind.INSTANCE);

    private final Map<String, Object> exports = new LinkedHashMap<>();
    private final Map<String, Object> imports = new LinkedHashMap<>();

    private final Table handles = new Table();
    private boolean mayEnter;

    private ComponentInstance(
            ComponentStore store, ComponentInstance parent, ComponentInstance lexicalScope) {
        this.store = Objects.requireNonNull(store, "store");
        this.parent = parent;
        this.lexicalScope = lexicalScope;
        store.register(this);
    }

    public static Builder builder(ComponentStore store) {
        return new Builder(store);
    }

    // ---------------------------------------------------------------- public surface

    /** The store owning this instance, shared with every instance it can reach. */
    public ComponentStore store() {
        return store;
    }

    /** The instance this one was instantiated inside, or {@code null} at the top of a store. */
    public ComponentInstance parent() {
        return parent;
    }

    public ComponentFunction export(String name) {
        Object value = getExport(name);
        if (!(value instanceof ComponentFunction)) {
            throw new LinkageException(
                    "Export \""
                            + name
                            + "\" is not a function (got "
                            + value.getClass().getName()
                            + ")");
        }
        return (ComponentFunction) value;
    }

    /**
     * The instance exported under {@code name}, which is what an exported interface is.
     *
     * @throws LinkageException if the export is something other than an instance
     */
    public ComponentInstance exportedInstance(String name) {
        Object value = getExport(name);
        if (!(value instanceof ComponentInstance)) {
            throw new LinkageException(
                    "Export \""
                            + name
                            + "\" is not an instance (got "
                            + value.getClass().getName()
                            + ")");
        }
        return (ComponentInstance) value;
    }

    public Set<String> exportNames() {
        return Collections.unmodifiableSet(exports.keySet());
    }

    // ---------------------------------------------------------------- runtime state

    /**
     * The handles this instance holds. Not exposed by this class, because minting and destroying
     * handles is the Canonical ABI's business rather than an embedder's.
     */
    HandleTable handles() {
        return handles;
    }

    Object getHandle(int index) {
        return handles.get(index);
    }

    Object removeHandle(int index) {
        return handles.remove(index);
    }

    /**
     * Whether a call may enter this instance right now. False only while the instance is being
     * instantiated. Simpler than the specification's model, as described in {@code docs/misc.md}.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#component-invariants">Explainer.md, component invariants</a>
     */
    boolean mayEnter() {
        return mayEnter;
    }

    // ---------------------------------------------------------------- type index space

    /** This instance's type index space, for resolving types written against it. */
    TypeSpace typeSpace() {
        return typeSpace;
    }

    /** The index space that {@code resolver} names. */
    static TypeSpace spaceOf(TypeResolver resolver) {
        return resolver instanceof ComponentInstance
                ? ((ComponentInstance) resolver).typeSpace()
                : TypeSpace.of(resolver);
    }

    /**
     * Resolves this instance's type indices. Every slot was resolved as it was added, against
     * whichever space its type was written into, so this hands back what the slot already holds.
     */
    private final class InstanceSpace implements TypeSpace {

        @Override
        public ResolvedType resolve(run.endive.cm.types.ValType valType) {
            if (valType.primValType() != null) {
                return ResolvedType.of(valType.primValType(), this);
            }
            int index = valType.typeIdx();
            ResolvedType resolved = slotAt(index).value();
            if (resolved == null) {
                throw new LinkageException(
                        "Type index "
                                + index
                                + " must resolve to a value type but got "
                                + slotAt(index).type());
            }
            return resolved;
        }

        @Override
        public ResourceTypeRef resourceType(int typeIdx) {
            return requireResourceType(typeIdx);
        }
    }

    /** The runtime identity of the resource type at {@code typeIdx} in this index space. */
    ResourceTypeInstance requireResourceType(int typeIdx) {
        ResourceTypeInstance resourceType = resourceType(typeIdx);
        if (resourceType == null) {
            throw new TrapException("Type at index " + typeIdx + " is not a resource type");
        }
        return resourceType;
    }

    /**
     * The runtime identity at {@code typeIdx}, or {@code null} if that slot holds an ordinary type.
     * Identity lives on the slot rather than the declaration, because one declaration can reach a
     * space more than once carrying different identities.
     */
    ResourceTypeInstance resourceType(int typeIdx) {
        return slotAt(typeIdx).resourceType();
    }

    /**
     * One numbered slot of the type index space, holding the declaration, the runtime resource
     * identity it names, and the resolved form.
     *
     * <p>Resolving happens when the slot is filled, against the space the type was written in,
     * which is not always this one. Instance and component types have no resolved form, because
     * their declarations introduce abstract types that only a provider settles.
     */
    static final class TypeSlot implements ResolvedTypeSlot {

        private final Type type;
        private final ResourceTypeInstance resourceType;
        private final ResolvedType value;
        private final ResolvedFuncType func;

        private TypeSlot(
                Type type,
                ResourceTypeInstance resourceType,
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
        public ResourceTypeInstance resourceType() {
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

    TypeSlot slotAt(int index) {
        return typeSlots.at(index);
    }

    int typeCount() {
        return typeSlots.size();
    }

    @Override
    public Type getType(int index) {
        return slotAt(index).type();
    }

    public List<Type> getTypes() {
        return typeSlots.all().stream()
                .map(TypeSlot::type)
                .collect(Collectors.toUnmodifiableList());
    }

    // ---------------------------------------------------------------- index space readers

    WasmModule getCoreModule(int index) {
        return coreModules.at(index);
    }

    List<WasmModule> getCoreModules() {
        return coreModules.all();
    }

    CoreType getCoreType(int index) {
        return coreTypes.at(index);
    }

    CoreModuleInstance getCoreInstance(int index) {
        return coreInstances.at(index);
    }

    CoreFunction<?> getCoreFunction(int index) {
        return coreFunctions.at(index);
    }

    ComponentFunction getFunction(int index) {
        return functions.at(index);
    }

    ComponentClosure getChildComponent(int index) {
        return childComponents.at(index);
    }

    List<ComponentClosure> getChildComponents() {
        return childComponents.all();
    }

    ComponentInstance getChildInstance(int index) {
        return childInstances.at(index);
    }

    Memory getCoreMemory(int index) {
        return coreMemories.at(index);
    }

    TableInstance getCoreTable(int index) {
        return coreTables.at(index);
    }

    GlobalInstance getCoreGlobal(int index) {
        return coreGlobals.at(index);
    }

    TagInstance getCoreTag(int index) {
        return coreTags.at(index);
    }

    /** The scope this component was written inside; {@code null} at the top of a tree. */
    ComponentInstance lexicalScope() {
        return lexicalScope;
    }

    Object getExport(String name) {
        if (!exports.containsKey(name)) {
            throw new LinkageException("Export was not found: " + name);
        }
        return exports.get(name);
    }

    boolean hasExport(String name) {
        return exports.containsKey(name);
    }

    /**
     * What was supplied for the import named {@code name}, keyed by the name it was supplied under,
     * or {@code null} when nothing was. The host may supply an interface under a compatible version
     * of the name, so at the top of a store the name is matched by {@link
     * InterfaceVersions#resolve}. Everywhere else it is matched exactly.
     *
     * @throws LinkageException when the compatible versions supplied are ambiguous
     */
    Map.Entry<String, Object> resolveImport(String name) {
        String supplied;
        if (parent == null) {
            supplied = InterfaceVersions.resolve(name, imports.keySet());
        } else {
            supplied = imports.containsKey(name) ? name : null;
        }
        return supplied == null
                ? null
                : new AbstractMap.SimpleImmutableEntry<>(supplied, imports.get(supplied));
    }

    private void seal() {
        coreModules.seal();
        coreTypes.seal();
        coreInstances.seal();
        coreFunctions.seal();
        coreMemories.seal();
        coreTables.seal();
        coreGlobals.seal();
        coreTags.seal();
        functions.seal();
        typeSlots.seal();
        childComponents.seal();
        childInstances.seal();
    }

    /**
     * The write side of an instantiation, and the only one there is.
     *
     * <p>The instance is created up front rather than at {@link #build()}, because a component's
     * definitions can refer to the instance in which they are being defined. Every index space is
     * sealed once {@code build} returns.
     */
    public static final class Builder {

        private final ComponentStore store;
        private ComponentInstance parent;
        private ComponentInstance lexicalScope;
        private ComponentInstance instance;
        private boolean built;

        private Builder(ComponentStore store) {
            this.store = Objects.requireNonNull(store, "store");
        }

        public Builder withParent(ComponentInstance parent) {
            requireUnstarted("parent");
            this.parent = parent;
            return this;
        }

        public Builder withLexicalScope(ComponentInstance lexicalScope) {
            requireUnstarted("lexicalScope");
            this.lexicalScope = lexicalScope;
            return this;
        }

        /**
         * The instance under construction, for definitions that have to capture it before it is
         * complete. Materialises it on first use, so the identity setters above may not be called
         * afterwards.
         */
        ComponentInstance instance() {
            if (instance == null) {
                instance = new ComponentInstance(store, parent, lexicalScope);
            }
            return instance;
        }

        private void requireUnstarted(String what) {
            if (instance != null) {
                throw new IllegalStateException(
                        "cannot set " + what + " after the instance has been created");
            }
        }

        /**
         * Brings a new resource type into existence, implemented by this instance. Resource types
         * are generative, so this is called once per declaration per instantiation and two
         * instantiations of one component get two types.
         */
        ResourceTypeInstance declareResourceType(Type type) {
            return new ResourceTypeInstance(type, instance(), null);
        }

        /**
         * Brings a resource type the host implements into existence, whose resources are destroyed
         * by calling {@code dtor} rather than by a core function in some component. A {@code null}
         * dtor means dropping one does nothing observable.
         */
        ResourceTypeInstance declareHostResourceType(Type type, IntConsumer dtor) {
            return new ResourceTypeInstance(type, null, dtor);
        }

        Builder addCoreModule(WasmModule module) {
            instance().coreModules.add(module);
            return this;
        }

        Builder addCoreType(CoreType type) {
            instance().coreTypes.add(type);
            return this;
        }

        Builder addCoreInstance(CoreModuleInstance coreInstance) {
            instance().coreInstances.add(coreInstance);
            return this;
        }

        Builder addCoreFunction(CoreFunction<?> function) {
            instance().coreFunctions.add(function);
            return this;
        }

        Builder addCoreMemory(Memory memory) {
            instance().coreMemories.add(memory);
            return this;
        }

        Builder addCoreTable(TableInstance table) {
            instance().coreTables.add(table);
            return this;
        }

        Builder addCoreGlobal(GlobalInstance global) {
            instance().coreGlobals.add(global);
            return this;
        }

        Builder addCoreTag(TagInstance tag) {
            instance().coreTags.add(tag);
            return this;
        }

        Builder addFunction(ComponentFunction function) {
            instance().functions.add(function);
            return this;
        }

        Builder addComponent(ComponentClosure component) {
            instance().childComponents.add(component);
            return this;
        }

        Builder addChildInstance(ComponentInstance childInstance) {
            instance().childInstances.add(childInstance);
            return this;
        }

        Builder addType(Type type) {
            return addType(type, null, null);
        }

        Builder addType(Type type, ResourceTypeInstance resourceType) {
            return addType(type, resourceType, null);
        }

        /**
         * Appends a type to the index space, together with the resource type it names, resolving it
         * against {@code origin}. That is the space in which it was written, or this instance's own
         * when it was written here.
         */
        Builder addType(Type type, ResourceTypeInstance resourceType, TypeSpace origin) {
            TypeSpace space = origin == null ? instance().typeSpace : origin;
            instance()
                    .typeSlots
                    .add(
                            new TypeSlot(
                                    type,
                                    resourceType,
                                    type.defValType() == null
                                            ? null
                                            : ResolvedType.of(type.defValType(), space),
                                    ResolvedFuncType.of(type.funcType(), space)));
            return this;
        }

        /**
         * Appends a slot taken from an index space whole. Re-exporting a type does this, as does
         * resolving an {@code eq} bound to its own bound.
         */
        Builder addType(TypeSlot slot) {
            instance().typeSlots.add(slot);
            return this;
        }

        Builder addExport(String name, Object value) {
            instance().exports.put(name, value);
            return this;
        }

        Builder addImport(String name, Object value) {
            instance().imports.put(name, value);
            return this;
        }

        Builder addImports(Map<String, Object> imports) {
            instance().imports.putAll(imports);
            return this;
        }

        /** Seals every index space and opens the instance to calls. */
        public ComponentInstance build() {
            if (built) {
                throw new IllegalStateException("instance has already been built");
            }
            built = true;
            ComponentInstance result = instance();
            result.seal();
            result.mayEnter = true;
            return result;
        }
    }

    /**
     * Index 0 is never handed out, so a zeroed {@code i32} names nothing. A missing index traps
     * rather than throwing, since guest code can reach it.
     */
    static final class Table implements HandleTable {
        private static final int MAX_SIZE = (1 << 28) - 1;

        private final Map<Integer, Object> refs = new ConcurrentHashMap<>();
        private final Deque<Integer> freeSlots = new ArrayDeque<>();

        /** Freed slots are reused before fresh ones, so this only ever grows past reuse. */
        private int next = 1;

        @Override
        public Object get(int index) {
            var ref = refs.get(index);
            if (ref == null) {
                throw new TrapException("unknown handle index " + index);
            }
            return ref;
        }

        @Override
        public int add(
                ResourceTypeRef resourceType, int rep, boolean own, BorrowScope.Callee scope) {
            return add(new ResourceHandle(resourceType, rep, own, scope));
        }

        private int add(Object ref) {
            Integer freeSlot = freeSlots.pollFirst();
            int index = freeSlot != null ? freeSlot : next++;
            if (refs.containsKey(index)) {
                throw new IllegalStateException("ref already found at index " + index);
            }
            if (index >= MAX_SIZE) {
                throw new TrapException("handle table max size exceeded");
            }
            refs.put(index, ref);
            return index;
        }

        @Override
        public Object remove(int index) {
            var ref = refs.remove(index);
            if (ref == null) {
                throw new TrapException("unknown handle index " + index);
            }
            freeSlots.addFirst(index);
            return ref;
        }
    }
}
