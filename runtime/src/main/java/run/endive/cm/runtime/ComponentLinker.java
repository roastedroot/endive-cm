package run.endive.cm.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.endive.cm.abi.BuiltinFunctionTypes;
import run.endive.cm.abi.CanonicalAbi;
import run.endive.cm.abi.Direction;
import run.endive.cm.abi.LiftLowerContext;
import run.endive.cm.abi.PostReturn;
import run.endive.cm.abi.ResourceTypeRef;
import run.endive.cm.abi.StringEncoding;
import run.endive.cm.abi.ValueTransfer;
import run.endive.cm.types.Alias;
import run.endive.cm.types.AliasSection;
import run.endive.cm.types.CanonSection;
import run.endive.cm.types.ComponentSection;
import run.endive.cm.types.ComponentType;
import run.endive.cm.types.CoreExportAlias;
import run.endive.cm.types.CoreInlineExport;
import run.endive.cm.types.CoreInlineExportInstanceExpr;
import run.endive.cm.types.CoreInstance;
import run.endive.cm.types.CoreInstanceExpr;
import run.endive.cm.types.CoreInstanceSection;
import run.endive.cm.types.CoreInstantiateArg;
import run.endive.cm.types.CoreInstantiateInstanceExpr;
import run.endive.cm.types.CoreModuleSection;
import run.endive.cm.types.CoreSort;
import run.endive.cm.types.CoreType;
import run.endive.cm.types.CoreTypeSection;
import run.endive.cm.types.CustomSection;
import run.endive.cm.types.Export;
import run.endive.cm.types.ExportAlias;
import run.endive.cm.types.ExportDecl;
import run.endive.cm.types.ExportSection;
import run.endive.cm.types.ExternDesc;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.Import;
import run.endive.cm.types.ImportSection;
import run.endive.cm.types.InlineExport;
import run.endive.cm.types.InlineExportInstanceExpr;
import run.endive.cm.types.Instance;
import run.endive.cm.types.InstanceDecl;
import run.endive.cm.types.InstanceSection;
import run.endive.cm.types.InstanceType;
import run.endive.cm.types.InstantiateArg;
import run.endive.cm.types.InstantiateInstanceExpr;
import run.endive.cm.types.OuterAlias;
import run.endive.cm.types.ResolvedFuncType;
import run.endive.cm.types.ResolvedType;
import run.endive.cm.types.Section;
import run.endive.cm.types.Sort;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeBound;
import run.endive.cm.types.TypeSection;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.types.canon.Canon;
import run.endive.cm.types.canon.CanonLift;
import run.endive.cm.types.canon.CanonLower;
import run.endive.cm.types.canon.CanonOpt;
import run.endive.cm.types.canon.CanonResource;
import run.endive.runtime.GlobalInstance;
import run.endive.runtime.ImportFunction;
import run.endive.runtime.ImportGlobal;
import run.endive.runtime.ImportMemory;
import run.endive.runtime.ImportTable;
import run.endive.runtime.ImportTag;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Memory;
import run.endive.runtime.TableInstance;
import run.endive.runtime.TagInstance;
import run.endive.runtime.TrapException;
import run.endive.runtime.WasmFunctionHandle;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.FunctionType;

public final class ComponentLinker {

    private static final long[] EMPTY_CORE_VALUES = new long[0];
    private static final Object[] EMPTY_COMPONENT_VALUES = new Object[0];
    private static final Map<String, Object> EMPTY_IMPORTS =
            Collections.unmodifiableMap(new LinkedHashMap<>());

    private ComponentLinker() {}

    public static Builder builder() {
        return new Builder();
    }

    public ComponentInstance instantiate(ComponentStore store, WasmComponent component) {
        return instantiate(store, component, EMPTY_IMPORTS);
    }

    /**
     * Instantiates {@code component} into {@code store}, alongside whatever instances the store
     * already holds. Only instances sharing a store may be wired to one another.
     */
    public ComponentInstance instantiate(
            ComponentStore store, WasmComponent component, Map<String, Object> imports) {
        try {
            return instantiate(store, null, null, component, imports);
        } catch (WasmEngineException e) {
            throw new LinkageException("Failed to instantiate component: " + e.getMessage(), e);
        }
    }

    /**
     * @param parent the instance inside which this one is being instantiated, {@code null} at the
     *     top of a store
     * @param lexicalScope the instantiation inside which this component was written, against which
     *     its outer aliases resolve, {@code null} for a component the host supplied
     */
    private ComponentInstance instantiate(
            ComponentStore store,
            ComponentInstance parent,
            ComponentInstance lexicalScope,
            WasmComponent component,
            Map<String, Object> imports) {
        // Held closed until build() returns, because a core `start` function can reach back
        // into it.
        var builder =
                ComponentInstance.builder(store).withParent(parent).withLexicalScope(lexicalScope);

        if (!imports.isEmpty()) {
            builder.addImports(imports);
        }

        for (Section section : component.sections()) {
            if (section instanceof CoreModuleSection) {
                processCoreModuleSection(builder, (CoreModuleSection) section);
            } else if (section instanceof CoreInstanceSection) {
                processCoreInstanceSection(builder, (CoreInstanceSection) section);
            } else if (section instanceof AliasSection) {
                processAliasSection(builder, (AliasSection) section);
            } else if (section instanceof TypeSection) {
                processTypeSection(builder, (TypeSection) section);
            } else if (section instanceof CanonSection) {
                processCanonSection(builder, (CanonSection) section);
            } else if (section instanceof ExportSection) {
                processExportSection(builder, (ExportSection) section);
            } else if (section instanceof ComponentSection) {
                processComponentSection(builder, (ComponentSection) section);
            } else if (section instanceof CoreTypeSection) {
                processCoreTypeSection(builder, (CoreTypeSection) section);
            } else if (section instanceof CustomSection) {
                // Custom sections are ignored
            } else if (section instanceof ImportSection) {
                processImportSection(builder, (ImportSection) section);
            } else if (section instanceof InstanceSection) {
                processInstanceSection(builder, (InstanceSection) section);
            } else {
                throw new LinkageException("Unknown section type: " + section.getClass().getName());
            }
        }

        return builder.build();
    }

    private void processCoreModuleSection(
            ComponentInstance.Builder builder, CoreModuleSection section) {
        builder.addCoreModule(section.module());
    }

    private void processCoreInstanceSection(
            ComponentInstance.Builder builder, CoreInstanceSection section) {
        for (CoreInstance coreInstance : section.instances()) {
            CoreInstanceExpr expr = coreInstance.expr();
            if (expr instanceof CoreInstantiateInstanceExpr) {
                instantiateCoreModule(builder, (CoreInstantiateInstanceExpr) expr);
            } else if (expr instanceof CoreInlineExportInstanceExpr) {
                instantiateCoreInlineInstance(builder, (CoreInlineExportInstanceExpr) expr);
            } else {
                throw new UnsupportedOperationException(
                        "Core instance expression kind " + expr.kind() + " not yet supported");
            }
        }
    }

    private void processAliasSection(ComponentInstance.Builder builder, AliasSection section) {
        for (Alias alias : section.aliases()) {
            processAlias(builder, alias);
        }
    }

    private void processTypeSection(ComponentInstance.Builder builder, TypeSection section) {
        for (Type type : section.types()) {
            // Distinct from the type any other instantiation of this component declares.
            builder.addType(
                    type, type.resourceType() == null ? null : builder.declareResourceType(type));
        }
    }

    private void processCanonSection(ComponentInstance.Builder builder, CanonSection section) {
        for (Canon canon : section.canons()) {
            if (canon instanceof CanonLift) {
                processCanonLift(builder, (CanonLift) canon);
            } else if (canon instanceof CanonLower) {
                processCanonLower(builder, (CanonLower) canon);
            } else if (canon instanceof CanonResource) {
                processCanonResource(builder, (CanonResource) canon);
            } else {
                throw new UnsupportedOperationException(
                        "Canon kind " + canon.kind() + " not yet supported");
            }
        }
    }

    private void processExportSection(ComponentInstance.Builder builder, ExportSection section) {
        for (Export export : section.exports()) {
            String name = export.name();
            Sort sort = export.sortIdx().sort();
            int idx = (int) export.sortIdx().idx();

            switch (sort.kind()) {
                case CORE:
                    switch (sort.coreSort()) {
                        case MODULE:
                            WasmModule module = builder.instance().getCoreModule(idx);
                            builder.addCoreModule(module);
                            builder.addExport(name, module);
                            break;
                        case FUNC:
                            CoreFunction<?> coreFunc = builder.instance().getCoreFunction(idx);
                            builder.addCoreFunction(coreFunc);
                            builder.addExport(name, coreFunc);
                            break;
                        case MEMORY:
                            Memory memory = builder.instance().getCoreMemory(idx);
                            builder.addCoreMemory(memory);
                            builder.addExport(name, memory);
                            break;
                        default:
                            throw new UnsupportedOperationException(
                                    "Export of core sort "
                                            + sort.coreSort()
                                            + " not yet supported");
                    }
                    break;
                case FUNC:
                    ComponentFunction func = builder.instance().getFunction(idx);
                    builder.addFunction(func);
                    builder.addExport(name, func);
                    break;
                case TYPE:
                    {
                        ComponentInstance.TypeSlot slot = builder.instance().slotAt(idx);
                        builder.addType(slot);
                        // A resource type travels as its identity, anything else as the slot.
                        builder.addExport(
                                name, slot.resourceType() != null ? slot.resourceType() : slot);
                        break;
                    }
                case INSTANCE:
                    ComponentInstance instance = builder.instance().getChildInstance(idx);
                    builder.addChildInstance(instance);
                    builder.addExport(name, instance);
                    break;
                case COMPONENT:
                    ComponentClosure component = builder.instance().getChildComponent(idx);
                    builder.addComponent(component);
                    builder.addExport(name, component);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Export of sort " + sort.kind() + " not yet supported");
            }
        }
    }

    private void processComponentSection(
            ComponentInstance.Builder builder, ComponentSection section) {
        builder.addComponent(new ComponentClosure(section.component(), builder.instance()));
    }

    private void processCoreTypeSection(
            ComponentInstance.Builder builder, CoreTypeSection section) {
        for (CoreType type : section.coreTypes()) {
            builder.addCoreType(type);
        }
    }

    private void processImportSection(ComponentInstance.Builder builder, ImportSection section) {
        for (Import imp : section.imports()) {
            Map.Entry<String, Object> resolved = builder.instance().resolveImport(imp.name());
            try {
                processImport(builder, imp, resolved);
            } catch (LinkageException e) {
                if (resolved == null || resolved.getKey().equals(imp.name())) {
                    throw e;
                }
                throw new LinkageException(
                        "Import \""
                                + imp.name()
                                + "\" resolved to compatible \""
                                + resolved.getKey()
                                + "\" - "
                                + e.getMessage(),
                        e);
            }
        }
    }

    /**
     * @param resolved what satisfies {@code imp}, keyed by the name it was supplied under, or
     *     {@code null} when nothing was supplied
     */
    private void processImport(
            ComponentInstance.Builder builder, Import imp, Map.Entry<String, Object> resolved) {
        switch (imp.externDesc().kind()) {
            case TYPE:
                processTypeImport(builder, imp, resolved);
                break;
            case CORE_MODULE:
                processCoreModuleImport(builder, imp, resolved);
                break;
            case INSTANCE:
                processInstanceImport(builder, imp, resolved);
                break;
            case COMPONENT:
                processComponentImport(builder, imp, resolved);
                break;
            case FUNC:
                processFunctionImport(builder, imp, resolved);
                break;
            default:
                throw new LinkageException(
                        "Import type " + imp.externDesc().kind() + " not supported yet");
        }
    }

    private void processInstanceSection(
            ComponentInstance.Builder builder, InstanceSection section) {
        for (Instance instance : section.instances()) {
            switch (instance.expr().kind()) {
                case INSTANTIATE:
                    instantiateComponent(builder, (InstantiateInstanceExpr) instance.expr());
                    break;
                case INLINE_EXPORT:
                    instantiateInlineComponent(builder, (InlineExportInstanceExpr) instance.expr());
                    break;
                default:
                    throw new LinkageException(
                            "Unknown instance expression kind: " + instance.expr().kind());
            }
        }
    }

    private void instantiateCoreModule(
            ComponentInstance.Builder builder, CoreInstantiateInstanceExpr expr) {
        int moduleIdx = (int) expr.moduleIdx();
        var module = builder.instance().getCoreModule(moduleIdx);

        ImportValues importValues;
        if (expr.instantiateArgs().isEmpty()) {
            importValues = ImportValues.empty();
        } else {
            var imports = ImportValues.builder();
            for (CoreInstantiateArg arg : expr.instantiateArgs()) {
                if (arg.sortIdx().sort() != CoreSort.INSTANCE) {
                    throw new LinkageException("core instantiate args must be of instance sort");
                }
                CoreModuleInstance coreInstance =
                        builder.instance().getCoreInstance((int) arg.sortIdx().idx());
                if (coreInstance instanceof CoreEndiveInstance) {
                    var moduleInstance = ((CoreEndiveInstance) coreInstance).getModuleInstance();
                    module.importSection().stream()
                            .filter(i -> i.module().equals(arg.name()))
                            .forEach(
                                    i -> {
                                        switch (i.importType()) {
                                            case FUNCTION:
                                                ImportFunction func =
                                                        ImportFunction.exportAsImport(
                                                                arg.name(),
                                                                i.name(),
                                                                moduleInstance,
                                                                i.name());
                                                imports.addFunction(func);
                                                break;
                                            case MEMORY:
                                                var memory =
                                                        moduleInstance.exports().memory(i.name());
                                                imports.addMemory(
                                                        new ImportMemory(
                                                                arg.name(), i.name(), memory));
                                                break;
                                            case GLOBAL:
                                                var global =
                                                        moduleInstance.exports().global(i.name());
                                                imports.addGlobal(
                                                        new ImportGlobal(
                                                                arg.name(), i.name(), global));
                                                break;
                                            case TABLE:
                                                var table =
                                                        moduleInstance.exports().table(i.name());
                                                imports.addTable(
                                                        new ImportTable(
                                                                arg.name(), i.name(), table));
                                                break;
                                            case TAG:
                                                var tag = moduleInstance.exports().tag(i.name());
                                                imports.addTag(
                                                        new ImportTag(arg.name(), i.name(), tag));
                                                break;
                                            default:
                                                throw new LinkageException(
                                                        "core instantiate module import of type "
                                                                + i.importType()
                                                                + " not yet supported");
                                        }
                                    });
                } else if (coreInstance instanceof CoreInlineInstance) {
                    var inlineInstance = ((CoreInlineInstance) coreInstance);
                    module.importSection().stream()
                            .filter(i -> i.module().equals(arg.name()))
                            .forEach(
                                    i -> {
                                        Object exported = inlineInstance.getExport(i.name());
                                        switch (i.importType()) {
                                            case FUNCTION:
                                                imports.addFunction(
                                                        ((CoreFunction<?>) exported)
                                                                .importFunction(
                                                                        arg.name(), i.name()));
                                                break;
                                            case MEMORY:
                                                imports.addMemory(
                                                        new ImportMemory(
                                                                arg.name(),
                                                                i.name(),
                                                                (Memory) exported));
                                                break;
                                            case GLOBAL:
                                                imports.addGlobal(
                                                        new ImportGlobal(
                                                                arg.name(),
                                                                i.name(),
                                                                (GlobalInstance) exported));
                                                break;
                                            case TABLE:
                                                imports.addTable(
                                                        new ImportTable(
                                                                arg.name(),
                                                                i.name(),
                                                                (TableInstance) exported));
                                                break;
                                            case TAG:
                                                imports.addTag(
                                                        new ImportTag(
                                                                arg.name(),
                                                                i.name(),
                                                                (TagInstance) exported));
                                                break;
                                            default:
                                                throw new LinkageException(
                                                        "core instantiate module import of type "
                                                                + i.importType()
                                                                + " not yet supported");
                                        }
                                    });
                }
            }
            importValues = imports.build();
        }

        var store = builder.instance().store();
        run.endive.runtime.Instance core =
                store.coreInstanceBuilder(module).withImportValues(importValues).build();
        store.registerCore(core);
        builder.addCoreInstance(new CoreEndiveInstance(core));
    }

    private void instantiateCoreInlineInstance(
            ComponentInstance.Builder builder, CoreInlineExportInstanceExpr expr) {
        var inline = CoreInlineInstance.builder();
        for (CoreInlineExport export : expr.inlineExports()) {
            int idx = (int) export.sortIdx().idx();
            switch (export.sortIdx().sort()) {
                case FUNC:
                    inline.addExport(export.name(), builder.instance().getCoreFunction(idx));
                    break;
                case MEMORY:
                    inline.addExport(export.name(), builder.instance().getCoreMemory(idx));
                    break;
                case MODULE:
                    inline.addExport(export.name(), builder.instance().getCoreModule(idx));
                    break;
                case TABLE:
                    inline.addExport(export.name(), builder.instance().getCoreTable(idx));
                    break;
                case GLOBAL:
                    inline.addExport(export.name(), builder.instance().getCoreGlobal(idx));
                    break;
                case TAG:
                    inline.addExport(export.name(), builder.instance().getCoreTag(idx));
                    break;
                default:
                    throw new LinkageException(
                            ("core inline export of sort "
                                    + export.sortIdx().sort()
                                    + " not yet supported"));
            }
        }
        builder.addCoreInstance(inline.build());
    }

    private void processAlias(ComponentInstance.Builder builder, Alias alias) {
        if (alias instanceof CoreExportAlias) {
            processCoreExportAlias(builder, (CoreExportAlias) alias);
        } else if (alias instanceof ExportAlias) {
            processExportAlias(builder, (ExportAlias) alias);
        } else if (alias instanceof OuterAlias) {
            processOuterAlias(builder, (OuterAlias) alias);
        } else {
            throw new UnsupportedOperationException(
                    "Alias kind " + alias.kind() + " not yet supported");
        }
    }

    private void processCanonLift(ComponentInstance.Builder builder, CanonLift lift) {
        int coreFuncIdx = (int) lift.funcIdx().idx();

        CoreFunction<?> coreFunc = builder.instance().getCoreFunction(coreFuncIdx);
        if (coreFunc == null) {
            throw new LinkageException("Core function at index " + coreFuncIdx + " not found");
        }
        ResolvedFuncType liftedType =
                resolveComponentFuncType(builder.instance(), (int) lift.typeIdx());
        // The core function is taken to have the type produced by flattening this signature.
        // Checking it
        // is a validation rule, so it belongs to whoever validated the binary. See
        // https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/CanonicalABI.md#canon-lift

        List<ResolvedType> componentFuncParams = CanonicalAbi.paramTypesOf(liftedType);
        ResolvedType resultValType = liftedType.hasResult() ? liftedType.result() : null;

        LiftLowerContext context = processCanonOpts(builder.instance(), lift.opts());

        // Lowering a `borrow` charges it to this call, which may not return until dropped.
        boolean scopesBorrows = containsBorrow(liftedType);

        ComponentFunctionCall call =
                (args) -> {
                    Task task = scopesBorrows ? new Task() : null;
                    LiftLowerContext callContext =
                            task == null ? context : context.withBorrowScope(task);

                    long[] loweredArgs =
                            CanonicalAbi.lowerFlatParams(
                                    callContext, Arrays.asList(args), componentFuncParams);
                    long[] result = coreFunc.apply(loweredArgs);

                    // Lift before post_return, which may release the buffers holding the
                    // results.
                    Object[] lifted =
                            resultValType == null
                                    ? EMPTY_COMPONENT_VALUES
                                    : CanonicalAbi.liftFlatResults(
                                                    callContext, result, List.of(resultValType))
                                            .toArray();

                    if (task != null) {
                        task.requireBorrowsReleased();
                    }
                    if (context.postReturn() != null) {
                        context.postReturn().apply(result != null ? result : EMPTY_CORE_VALUES);
                    }
                    return lifted;
                };
        builder.addFunction(
                ComponentFunctionInstance.builder()
                        .withInstance(builder.instance())
                        .withFuncType(liftedType.node())
                        .withTypeResolver(builder.instance())
                        .withCall(call)
                        .withLiftLowerContext(context)
                        .withLiftedFunction(coreFunc)
                        .build());
    }

    private void processCanonLower(ComponentInstance.Builder builder, CanonLower lower) {
        int funcIdx = (int) lower.funcIdx().idx();
        ComponentFunction func = builder.instance().getFunction(funcIdx);
        if (func == null) {
            throw new LinkageException("Function at index " + funcIdx + " not found");
        }
        ResolvedFuncType componentFuncType = func.resolvedFuncType();

        LiftLowerContext callerContext = processCanonOpts(builder.instance(), lower.opts());

        FunctionType coreFuncType =
                CanonicalAbi.flattenFuncType(callerContext, componentFuncType, Direction.LOWER);

        // The instance entered by a call through this lowered function.
        ComponentInstance target = func.definingInstance();

        // Direct call path
        if (func.isLifted()
                && ValueTransfer.isIdentityTransfer(
                        callerContext, func.context(), componentFuncType)
                && coreFuncType.equals(func.liftedFunction().funcType())) {
            CoreFunction<?> callee = func.liftedFunction();
            PostReturn postReturn = func.context().postReturn();
            if (postReturn == null) {
                // The crossing into another instance has to be guarded even with nothing to
                // translate.
                builder.addCoreFunction(
                        new CoreImportFunction(
                                coreFuncType,
                                (instance, args) -> {
                                    requireMayEnter(target);
                                    return callee.apply(args);
                                }));
                return;
            }
            builder.addCoreFunction(
                    new CoreImportFunction(
                            coreFuncType,
                            (instance, args) -> {
                                requireMayEnter(target);
                                long[] results = callee.apply(args);
                                postReturn.apply(results != null ? results : EMPTY_CORE_VALUES);
                                return results;
                            }));
            return;
        }

        boolean resultsSpill = componentFuncType.hasResult() && coreFuncType.returns().isEmpty();
        WasmFunctionHandle coreFuncHandle;

        if (func.isLifted()
                && ValueTransfer.canTransfer(callerContext, func.context(), componentFuncType)) {
            var transfer = ValueTransfer.compile(callerContext, func.context(), componentFuncType);
            coreFuncHandle =
                    (instance, args) -> {
                        // Memory copy path
                        requireMayEnter(target);
                        long[] calleeArgs = transfer.transferParams(args);
                        long[] calleeResults = func.liftedFunction().apply(calleeArgs);

                        long[] outParam = resultsSpill ? new long[] {args[args.length - 1]} : null;
                        long[] callerResults = transfer.transferResults(calleeResults, outParam);

                        var postReturn = func.context().postReturn();
                        if (postReturn != null) {
                            postReturn.apply(
                                    calleeResults != null ? calleeResults : EMPTY_CORE_VALUES);
                        }
                        return callerResults;
                    };
        } else {
            List<ResolvedType> componentFuncParams = CanonicalAbi.paramTypesOf(componentFuncType);
            // Lifting a `borrow` argument holds the caller's handle lent for the call.
            boolean scopesBorrows = containsBorrow(componentFuncType);
            coreFuncHandle =
                    (instance, args) -> {
                        // Full trampoline path
                        requireMayEnter(target);
                        Subtask subtask = scopesBorrows ? new Subtask() : null;
                        LiftLowerContext callContext =
                                subtask == null
                                        ? callerContext
                                        : callerContext.withBorrowScope(subtask);
                        try {
                            Object[] liftedArgValues =
                                    CanonicalAbi.liftFlatParams(
                                                    callContext, args, componentFuncParams)
                                            .toArray();
                            Object[] results = func.apply(liftedArgValues);
                            if (!componentFuncType.hasResult()) {
                                return EMPTY_CORE_VALUES;
                            }
                            long[] outParam =
                                    resultsSpill ? new long[] {args[args.length - 1]} : null;
                            return CanonicalAbi.lowerFlatResults(
                                    callContext,
                                    Arrays.asList(results),
                                    List.of(componentFuncType.result()),
                                    outParam);
                        } finally {
                            if (subtask != null) {
                                subtask.releaseLenders();
                            }
                        }
                    };
        }
        builder.addCoreFunction(new CoreImportFunction(coreFuncType, coreFuncHandle));
    }

    private void processCanonResource(ComponentInstance.Builder builder, CanonResource canon) {
        Type type = builder.instance().getType((int) canon.typeIdx());
        if (type.resourceType() == null) {
            throw new LinkageException(
                    "Type at index " + canon.typeIdx() + " is not a resource type");
        }

        var repType = type.resourceType().rep();
        if (!run.endive.wasm.types.ValType.I32.equals(repType)) {
            throw new LinkageException("Resource rep type " + repType + " is not supported");
        }

        ResourceTypeInstance resourceType =
                builder.instance().requireResourceType((int) canon.typeIdx());
        switch (canon.kind()) {
            case RESOURCE_NEW:
                processCanonResourceNew(builder, resourceType);
                break;
            case RESOURCE_REP:
                processCanonResourceRep(builder, resourceType);
                break;
            case RESOURCE_DROP:
                processCanonResourceDrop(builder, resourceType);
                break;
            default:
                throw new LinkageException("Canon kind " + canon.kind() + " is not supported yet");
        }
    }

    private void processTypeImport(
            ComponentInstance.Builder builder, Import imp, Map.Entry<String, Object> resolved) {
        TypeBound typeBound = imp.externDesc().typeBound();
        if (typeBound.kind() == TypeBound.Kind.EQ) {
            Type bound = builder.instance().getType((int) typeBound.typeIdx());
            if (resolved == null) {
                // An `eq` bound says which type this is, so there is nothing left for anyone
                // to decide and nothing to supply. The import resolves to the bound itself.
                builder.addType(builder.instance().slotAt((int) typeBound.typeIdx()));
                return;
            }
            // Supplying one anyway is allowed, but then it has to agree.
            Object suppliedValue = resolved.getValue();
            Type supplied = typeOf(suppliedValue);
            if (supplied == null) {
                throw new LinkageException(
                        "Import \""
                                + imp.name()
                                + "\" does not match - expected type found "
                                + sortOf(suppliedValue));
            }
            // A resource type is only ever itself, so two agree only when they are the same
            ResourceTypeInstance boundResource =
                    builder.instance().resourceType((int) typeBound.typeIdx());
            ResourceTypeInstance suppliedResource = resourceOf(suppliedValue);
            if (boundResource != null || suppliedResource != null) {
                if (boundResource != suppliedResource) {
                    throw new LinkageException("mismatched resource types");
                }
            } else if (!TypeMatcher.slotsMatch(
                    builder.instance().slotAt((int) typeBound.typeIdx()), slotOf(suppliedValue))) {
                throw new LinkageException(
                        "Type eq bound check failed on import '"
                                + imp.name()
                                + "' - expected "
                                + bound
                                + ", got "
                                + supplied);
            }
            ComponentInstance.TypeSlot suppliedSlot = slotOf(suppliedValue);
            if (suppliedSlot != null) {
                builder.addType(suppliedSlot);
            } else {
                builder.addType(supplied, resourceOf(suppliedValue));
            }
        } else if (typeBound.kind() == TypeBound.Kind.SUB_RESOURCE) {
            var importValue = requireSupplied(imp, resolved);
            ResourceTypeInstance resourceType = resourceOf(importValue);
            if (resourceType == null) {
                throw new LinkageException(
                        "Type sub-resource bound check failed on import '"
                                + imp.name()
                                + "' - expected resource type, got "
                                + sortOf(importValue));
            }
            builder.addType(resourceType.type(), resourceType);
        } else {
            throw new LinkageException(
                    "Type bound kind " + typeBound.kind() + " not supported yet");
        }
    }

    private void processCoreModuleImport(
            ComponentInstance.Builder builder, Import imp, Map.Entry<String, Object> resolved) {
        var coreType = builder.instance().getCoreType((int) imp.externDesc().typeIdx());
        if (coreType.moduleType() == null) {
            throw new LinkageException(
                    "Core module type not found at index "
                            + imp.externDesc().typeIdx()
                            + " for import '"
                            + imp.name()
                            + "'");
        }
        var moduleImport =
                requireImportSort(imp, requireSupplied(imp, resolved), WasmModule.class, "module");
        if (atHostBoundary(builder)) {
            // The module came from the embedder, so the declared type is the only statement of
            // what it has to be.
            CoreModuleMatcher.requireSubtype(coreType.moduleType(), moduleImport);
        }
        builder.addCoreModule(moduleImport);
    }

    private void processInstanceImport(
            ComponentInstance.Builder builder, Import imp, Map.Entry<String, Object> resolved) {
        var type = builder.instance().getType((int) imp.externDesc().typeIdx());
        if (type.instanceType() == null) {
            throw new LinkageException(
                    "Instance type not found at index "
                            + imp.externDesc().typeIdx()
                            + " for import '"
                            + imp.name()
                            + "'");
        }

        ComponentInstance instanceImport;
        if (resolved != null) {
            instanceImport =
                    requireImportSort(
                            imp, resolved.getValue(), ComponentInstance.class, "instance");
        } else if (demandsNothing(type.instanceType())) {
            instanceImport = synthesizeEmptyInstance(builder.instance(), type.instanceType());
        } else {
            throw new LinkageException(
                    "Unable to resolve component instance import "
                            + imp.name()
                            + " with description "
                            + imp.externDesc());
        }
        // Handle indices and resource identities are store-relative.
        builder.instance().store().requireOwns(instanceImport);
        if (atHostBoundary(builder)) {
            matchInstanceType(builder.instance(), imp, type.instanceType(), instanceImport);
        }
        builder.addChildInstance(instanceImport);
    }

    private void processComponentImport(
            ComponentInstance.Builder builder, Import imp, Map.Entry<String, Object> resolved) {
        var type = builder.instance().getType((int) imp.externDesc().typeIdx());
        if (type.componentType() == null) {
            throw new LinkageException(
                    "Component type not found at index "
                            + imp.externDesc().typeIdx()
                            + " for import '"
                            + imp.name()
                            + "'");
        }
        if (resolved != null) {
            Object componentImport = resolved.getValue();
            if (componentImport instanceof WasmComponent) {
                // Supplied by the host, so it encloses nothing and closes over nothing.
                componentImport = new ComponentClosure((WasmComponent) componentImport, null);
            }
            ComponentClosure closure =
                    requireImportSort(imp, componentImport, ComponentClosure.class, "component");
            if (builder.instance().parent() == null) {
                // Unreachable while Validator refuses root level component imports, and kept so
                // that lifting that restriction does not open an unchecked boundary. See
                // docs/misc.md.
                ComponentTypeMatcher.requireSubtype(
                        type.componentType(), closure.definition(), "Import '" + imp.name() + "'");
            }
            builder.addComponent(closure);
        } else {
            throw new LinkageException(
                    "Unable to resolve component import "
                            + imp.name()
                            + " with description "
                            + imp.externDesc());
        }
    }

    private void processFunctionImport(
            ComponentInstance.Builder builder, Import imp, Map.Entry<String, Object> resolved) {
        var type = builder.instance().getType((int) imp.externDesc().typeIdx());
        if (type.funcType() == null) {
            throw new LinkageException(
                    "Function type not found at index "
                            + imp.externDesc().typeIdx()
                            + " for import '"
                            + imp.name()
                            + "'");
        }

        if (resolved != null) {
            var functionImport =
                    requireImportSort(
                            imp, resolved.getValue(), ComponentFunction.class, "function");
            if (atHostBoundary(builder)) {
                // An embedder-supplied function has no parameter names of its own to compare.
                var slot = builder.instance().slotAt((int) imp.externDesc().typeIdx());
                if (!TypeMatcher.funcTypesMatch(
                        slot.func(),
                        functionImport.resolvedFuncType(),
                        !functionImport.hostProvided())) {
                    throw new LinkageException(
                            "Import \""
                                    + imp.name()
                                    + "\" does not match - expected "
                                    + type.funcType()
                                    + ", got "
                                    + functionImport.funcType());
                }
            }
            builder.addFunction(functionImport);
        } else {
            throw new LinkageException(
                    "Unable to resolve component function import "
                            + imp.name()
                            + " with description "
                            + imp.externDesc());
        }
    }

    private void instantiateComponent(
            ComponentInstance.Builder builder, InstantiateInstanceExpr expr) {
        ComponentClosure component =
                builder.instance().getChildComponent((int) expr.componentIdx());
        Map<String, Object> imports = new LinkedHashMap<>();
        for (InstantiateArg arg : expr.instantiateArgs()) {
            switch (arg.sortIdx().sort().kind()) {
                case CORE:
                    var coreSort = arg.sortIdx().sort().coreSort();
                    if (coreSort == CoreSort.FUNC) {
                        imports.put(
                                arg.name(),
                                builder.instance().getCoreFunction((int) arg.sortIdx().idx()));
                    } else if (coreSort == CoreSort.MODULE) {
                        imports.put(
                                arg.name(),
                                builder.instance().getCoreModule((int) arg.sortIdx().idx()));
                    } else {
                        throw new LinkageException(
                                "Import core sort " + coreSort + " not supported yet");
                    }
                    break;
                case FUNC:
                    imports.put(
                            arg.name(), builder.instance().getFunction((int) arg.sortIdx().idx()));
                    break;
                case COMPONENT:
                    imports.put(
                            arg.name(),
                            builder.instance().getChildComponent((int) arg.sortIdx().idx()));
                    break;
                case TYPE:
                    {
                        int idx = (int) arg.sortIdx().idx();
                        ResourceTypeInstance resourceType = builder.instance().resourceType(idx);
                        imports.put(
                                arg.name(),
                                resourceType != null
                                        ? resourceType
                                        : builder.instance().slotAt(idx));
                        break;
                    }
                case INSTANCE:
                    imports.put(
                            arg.name(),
                            builder.instance().getChildInstance((int) arg.sortIdx().idx()));
                    break;
                case VALUE:
                    throw new LinkageException("VALUE instantiate args not supported yet");
                default:
                    throw new LinkageException(
                            "Unknown instantiate arg sort kind: " + arg.sortIdx().sort().kind());
            }
        }
        builder.addChildInstance(
                instantiate(
                        builder.instance().store(),
                        builder.instance(),
                        component.definingScope(),
                        component.definition(),
                        imports));
    }

    private void instantiateInlineComponent(
            ComponentInstance.Builder builder, InlineExportInstanceExpr expr) {
        var inline =
                ComponentInstance.builder(builder.instance().store())
                        .withParent(builder.instance())
                        .withLexicalScope(builder.instance());
        for (InlineExport export : expr.inlineExports()) {
            switch (export.sortIdx().sort().kind()) {
                case CORE:
                    var coreSort = export.sortIdx().sort().coreSort();
                    if (coreSort == CoreSort.FUNC) {
                        inline.addExport(
                                export.name(),
                                builder.instance().getCoreFunction((int) export.sortIdx().idx()));
                    } else if (coreSort == CoreSort.MODULE) {
                        inline.addExport(
                                export.name(),
                                builder.instance().getCoreModule((int) export.sortIdx().idx()));
                    } else {
                        throw new LinkageException(
                                "Import core sort " + coreSort + " not supported yet");
                    }
                    break;
                case FUNC:
                    inline.addExport(
                            export.name(),
                            builder.instance().getFunction((int) export.sortIdx().idx()));
                    break;
                case COMPONENT:
                    inline.addExport(
                            export.name(),
                            builder.instance().getChildComponent((int) export.sortIdx().idx()));
                    break;
                case TYPE:
                    {
                        int typeIdx = (int) export.sortIdx().idx();
                        ComponentInstance.TypeSlot slot = builder.instance().slotAt(typeIdx);
                        inline.addExport(
                                export.name(),
                                slot.resourceType() != null ? slot.resourceType() : slot);
                        break;
                    }
                case INSTANCE:
                    inline.addExport(
                            export.name(),
                            builder.instance().getChildInstance((int) export.sortIdx().idx()));
                    break;
                case VALUE:
                    throw new LinkageException("VALUE instantiate args not supported yet");
                default:
                    throw new LinkageException(
                            "Unknown instantiate arg sort kind: " + export.sortIdx().sort().kind());
            }
        }
        builder.addChildInstance(inline.build());
    }

    private void processCoreExportAlias(ComponentInstance.Builder builder, CoreExportAlias alias) {
        Sort sort = alias.sort();
        if (sort.kind() != Sort.Kind.CORE) {
            throw new LinkageException(
                    "Expected CORE sort for core export alias, got " + sort.kind());
        }

        CoreSort coreSort = sort.coreSort();
        int instanceIdx = (int) alias.instanceIdx();
        String name = alias.name();
        CoreModuleInstance coreInstance = builder.instance().getCoreInstance(instanceIdx);

        // A core instance is either an instantiated module or exports gathered inline.
        if (coreInstance instanceof CoreInlineInstance) {
            aliasInlineCoreExport(builder, (CoreInlineInstance) coreInstance, coreSort, name);
            return;
        }
        run.endive.runtime.Instance instance =
                ((CoreEndiveInstance) coreInstance).getModuleInstance();

        switch (coreSort) {
            case FUNC:
                CoreExportFunction fn =
                        new CoreExportFunction(instance.exportType(name), instance.export(name));
                builder.addCoreFunction(fn);
                break;
            case MEMORY:
                Memory memory = instance.exports().memory(alias.name());
                builder.addCoreMemory(memory);
                break;
            case TABLE:
                TableInstance table = instance.exports().table(alias.name());
                builder.addCoreTable(table);
                break;
            case GLOBAL:
                GlobalInstance global = instance.exports().global(alias.name());
                builder.addCoreGlobal(global);
                break;
            case TAG:
                TagInstance tag = instance.exports().tag(alias.name());
                builder.addCoreTag(tag);
                break;
            default:
                throw new UnsupportedOperationException(
                        "Core export alias for sort " + coreSort + " not yet supported");
        }
    }

    private void processExportAlias(ComponentInstance.Builder builder, ExportAlias alias) {
        Sort sort = alias.sort();
        ComponentInstance linked = builder.instance().getChildInstance((int) alias.instanceIdx());

        switch (sort.kind()) {
            case CORE:
                if (sort.coreSort() == null || sort.coreSort() != CoreSort.MODULE) {
                    throw new LinkageException(
                            "CORE export alias only allows module sort but got " + sort.coreSort());
                }
                builder.addCoreModule((WasmModule) linked.getExport(alias.name()));
                break;
            case FUNC:
                builder.addFunction((ComponentFunction) linked.getExport(alias.name()));
                break;
            case COMPONENT:
                builder.addComponent((ComponentClosure) linked.getExport(alias.name()));
                break;
            case INSTANCE:
                builder.addChildInstance((ComponentInstance) linked.getExport(alias.name()));
                break;
            case TYPE:
                {
                    Object exported = linked.getExport(alias.name());
                    ComponentInstance.TypeSlot slot = slotOf(exported);
                    if (slot != null) {
                        builder.addType(slot);
                    } else {
                        builder.addType(typeOf(exported), resourceOf(exported), linked.typeSpace());
                    }
                    break;
                }
            case VALUE:
                throw new LinkageException("VALUE sort not yet supported for export alias");
            default:
                throw new LinkageException("Unknown Sort kind " + sort.kind());
        }
    }

    private void processOuterAlias(ComponentInstance.Builder builder, OuterAlias alias) {
        ComponentInstance containingStore = outerScope(builder.instance(), alias, 1);
        switch (alias.sort().kind()) {
            case CORE:
                CoreSort coreSort = alias.sort().coreSort();
                switch (coreSort) {
                    case MODULE:
                        WasmModule module = containingStore.getCoreModule((int) alias.index());
                        builder.addCoreModule(module);
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                "Outer alias core sort " + coreSort + " not yet supported");
                }
                break;
            case COMPONENT:
                builder.addComponent(containingStore.getChildComponent((int) alias.index()));
                break;
            case TYPE:
                builder.addType(containingStore.slotAt((int) alias.index()));
                break;
            default:
                throw new UnsupportedOperationException(
                        "Outer alias sort " + alias.sort() + " not yet supported");
        }
    }

    /** The function type at {@code funcIdx}, already resolved when the slot was filled. */
    private ResolvedFuncType resolveComponentFuncType(ComponentInstance instance, int funcIdx) {
        ResolvedFuncType funcType = instance.slotAt(funcIdx).func();
        if (funcType == null) {
            throw new LinkageException("Type at index " + funcIdx + " is not a function type");
        }
        return funcType;
    }

    private static LiftLowerContext processCanonOpts(
            ComponentInstance instance, List<CanonOpt> opts) {
        var contextBuilder =
                LiftLowerContext.builder()
                        // A handle indexes the table of the instance owning this definition.
                        .withHandles(instance.handles());
        for (var opt : opts) {
            switch (opt.kind()) {
                case STRING_ENCODING_UTF8:
                    contextBuilder.withStringEncoding(StringEncoding.UTF8);
                    break;
                case STRING_ENCODING_UTF16:
                    contextBuilder.withStringEncoding(StringEncoding.UTF16);
                    break;
                case STRING_ENCODING_LATIN1_UTF16:
                    contextBuilder.withStringEncoding(StringEncoding.LATIN1_UTF16);
                    break;
                case MEMORY:
                    contextBuilder.withMemory(instance.getCoreMemory((int) opt.index()));
                    break;
                case REALLOC:
                    CoreFunction<?> realloc = resolveCoreFunction(instance, (int) opt.index());
                    contextBuilder.withRealloc(
                            (oldPtr, oldSize, align, newSize) -> {
                                var result = realloc.apply(oldPtr, oldSize, align, newSize);
                                return (int) result[0];
                            });
                    break;
                case POST_RETURN:
                    CoreFunction<?> postReturn = resolveCoreFunction(instance, (int) opt.index());
                    contextBuilder.withPostReturn(postReturn::apply);
                    break;
                case ASYNC:
                    contextBuilder.withAsync(true);
                    break;
                case CALLBACK:
                    // TODO - Look up callback func by index
                    throw new UnsupportedOperationException("Callback canon opt not yet supported");
                default:
                    throw new IllegalArgumentException("Unknown canon opt kind " + opt.kind());
            }
        }
        return contextBuilder.build();
    }

    /** Whether any parameter or result of {@code ft} contains a {@code borrow}. */
    private static boolean containsBorrow(ResolvedFuncType ft) {
        for (ResolvedFuncType.Param p : ft.params()) {
            if (CanonicalAbi.containsBorrow(p.type())) {
                return true;
            }
        }
        return ft.hasResult() && CanonicalAbi.containsBorrow(ft.result());
    }

    /**
     * Traps if a call is not allowed to enter {@code instance} yet. Checked on the lowering side
     * because a fused path never runs the lift trampoline.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#component-invariants">Explainer.md, component invariants</a>
     */
    private static void requireMayEnter(ComponentInstance instance) {
        if (instance != null && !instance.mayEnter()) {
            throw new TrapException("cannot enter component instance");
        }
    }

    private void processCanonResourceNew(
            ComponentInstance.Builder builder, ResourceTypeInstance resourceType) {
        // The table is the component instance's, not the core instance the trampoline is handed.
        ComponentInstance owner = builder.instance();
        WasmFunctionHandle coreFunc =
                (coreInstance, args) ->
                        new long[] {owner.handles().add(resourceType, (int) args[0], true, null)};
        builder.addCoreFunction(
                new CoreImportFunction(BuiltinFunctionTypes.CANON_RESOURCE_NEW, coreFunc));
    }

    private void processCanonResourceRep(
            ComponentInstance.Builder builder, ResourceTypeInstance resourceType) {
        ComponentInstance owner = builder.instance();
        WasmFunctionHandle coreFunc =
                (coreInstance, args) -> {
                    int index = (int) args[0];
                    var handle = requireResourceHandle(owner.getHandle(index), index, resourceType);
                    return new long[] {handle.rep()};
                };
        builder.addCoreFunction(
                new CoreImportFunction(BuiltinFunctionTypes.CANON_RESOURCE_REP, coreFunc));
    }

    private void processCanonResourceDrop(
            ComponentInstance.Builder builder, ResourceTypeInstance resourceType) {
        ComponentInstance owner = builder.instance();
        WasmFunctionHandle coreFunc =
                (coreInstance, args) -> {
                    int index = (int) args[0];
                    var handle =
                            requireResourceHandle(owner.removeHandle(index), index, resourceType);
                    if (handle.numLends() != 0) {
                        throw new TrapException(
                                "cannot drop handle index " + index + " while it is lent");
                    }
                    if (handle.own()) {
                        // Dropping the last owning handle destroys the resource.
                        if (resourceType.hasDtor()) {
                            resourceType.runDtor(handle.rep());
                        }
                    } else {
                        // Dropping a borrow discharges it from the call into which it was
                        // lowered.
                        handle.borrowScope().unborrow();
                    }
                    return EMPTY_CORE_VALUES;
                };
        builder.addCoreFunction(
                new CoreImportFunction(BuiltinFunctionTypes.CANON_RESOURCE_DROP, coreFunc));
    }

    /**
     * The declaration behind a type arriving as a value. A resource type travels as its runtime
     * identity, because that identity is the type. Anything else travels as itself.
     */
    private static Type typeOf(Object value) {
        if (value instanceof ResourceTypeInstance) {
            return ((ResourceTypeInstance) value).type();
        }
        if (value instanceof ComponentInstance.TypeSlot) {
            return ((ComponentInstance.TypeSlot) value).type();
        }
        return value instanceof Type ? (Type) value : null;
    }

    /** How the Component Model's linkage diagnostics name the sort of a supplied import. */
    private static String sortOf(Object value) {
        if (value instanceof ComponentInstance) {
            return "instance";
        }
        if (value instanceof ComponentFunction) {
            return "func";
        }
        if (value instanceof WasmModule) {
            return "module";
        }
        if (value instanceof ComponentClosure || value instanceof WasmComponent) {
            return "component";
        }
        if (value instanceof ResourceTypeInstance) {
            return "resource";
        }
        if (value instanceof ComponentInstance.TypeSlot) {
            return ((ComponentInstance.TypeSlot) value).type().simpleName();
        }
        if (value instanceof Type) {
            return ((Type) value).simpleName();
        }
        return value == null ? "nothing" : value.getClass().getSimpleName();
    }

    /** The resource type a value names, or {@code null} if it names an ordinary type. */
    private static ResourceTypeInstance resourceOf(Object value) {
        return value instanceof ResourceTypeInstance ? (ResourceTypeInstance) value : null;
    }

    /** The slot a type arriving as a value came from, or {@code null} if it is not one. */
    private static ComponentInstance.TypeSlot slotOf(Object value) {
        return value instanceof ComponentInstance.TypeSlot
                ? (ComponentInstance.TypeSlot) value
                : null;
    }

    /** The value supplied for an import that cannot be linked without one. */
    private static Object requireSupplied(Import imp, Map.Entry<String, Object> resolved) {
        if (resolved == null) {
            throw new LinkageException("Import \"" + imp.name() + "\" was not found");
        }
        return resolved.getValue();
    }

    /** Narrows a supplied import to the sort its declaration calls for. */
    private static <T> T requireImportSort(
            Import imp, Object value, Class<T> type, String expectedSort) {
        if (!type.isInstance(value)) {
            throw new LinkageException(
                    "Import \""
                            + imp.name()
                            + "\" does not match - expected "
                            + expectedSort
                            + " found "
                            + sortOf(value));
        }
        return type.cast(value);
    }

    /**
     * Whether the values satisfying this instantiation's imports are arriving from the host.
     *
     * <p>A validator has already compared the declaration against the definition wherever a
     * component instantiates a child it contains, so only the host boundary is checked here. See
     * {@code docs/misc.md} for why that is sound and what it requires.
     */
    private static boolean atHostBoundary(ComponentInstance.Builder builder) {
        return builder.instance().parent() == null;
    }

    /**
     * Whether an instance type asks nothing of whoever supplies it, in which case it need not be
     * supplied. An instance exporting only empty instances is itself empty, and a declaration that
     * only defines a type demands nothing.
     */
    private static boolean demandsNothing(InstanceType type) {
        List<Type> localTypes = new ArrayList<>();
        for (var decl : type.getInstanceDecls()) {
            switch (decl.kind()) {
                case CORE_TYPE:
                    break;
                case TYPE:
                    localTypes.add(decl.type());
                    break;
                case EXPORT_DECL:
                    {
                        var externDesc = decl.exportDecl().externDesc();
                        if (externDesc.kind() == ExternDesc.Kind.TYPE) {
                            // An `eq` bound leaves the supplier nothing to choose.
                            if (externDesc.typeBound().kind() != TypeBound.Kind.EQ) {
                                return false;
                            }
                            int boundIdx = (int) externDesc.typeBound().typeIdx();
                            if (boundIdx < 0 || boundIdx >= localTypes.size()) {
                                return false;
                            }
                            localTypes.add(localTypes.get(boundIdx));
                            break;
                        }
                        if (externDesc.kind() != ExternDesc.Kind.INSTANCE) {
                            return false;
                        }
                        int idx = (int) externDesc.typeIdx();
                        if (idx < 0 || idx >= localTypes.size()) {
                            return false;
                        }
                        InstanceType nested = localTypes.get(idx).instanceType();
                        if (nested == null || !demandsNothing(nested)) {
                            return false;
                        }
                        break;
                    }
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * Builds the instance an unsupplied {@link #demandsNothing empty} instance import stands for.
     */
    private ComponentInstance synthesizeEmptyInstance(ComponentInstance parent, InstanceType type) {
        var builder =
                ComponentInstance.builder(parent.store())
                        .withParent(parent)
                        .withLexicalScope(parent);
        // The stand-in mirrors the declaration's own type index space.
        List<Type> localTypes = new ArrayList<>();
        for (var decl : type.getInstanceDecls()) {
            if (decl.kind() == InstanceDecl.Kind.TYPE) {
                localTypes.add(decl.type());
                builder.addType(decl.type());
            } else if (decl.kind() == InstanceDecl.Kind.EXPORT_DECL) {
                var exportDecl = decl.exportDecl();
                var externDesc = exportDecl.externDesc();
                if (externDesc.kind() == ExternDesc.Kind.TYPE) {
                    // An `eq` bound fixes which type this is, and an alias may retrieve it.
                    Type bound = localTypes.get((int) externDesc.typeBound().typeIdx());
                    localTypes.add(bound);
                    builder.addType(bound);
                    builder.addExport(
                            exportDecl.name(),
                            builder.instance().slotAt(builder.instance().typeCount() - 1));
                    continue;
                }
                InstanceType nested = localTypes.get((int) externDesc.typeIdx()).instanceType();
                builder.addExport(
                        exportDecl.name(), synthesizeEmptyInstance(builder.instance(), nested));
            }
        }
        return builder.build();
    }

    /**
     * Checks that {@code provider} satisfies the instance type an import declares. Declarations are
     * read in order, so a type export is bound before anything written in terms of it is checked.
     */
    private void matchInstanceType(
            ComponentInstance importer,
            Import imp,
            InstanceType instanceType,
            ComponentInstance provider) {
        matchInstanceType(importer, imp, instanceType, provider, null);
    }

    /**
     * @param enclosing the space of the instance type inside which this declaration is nested, or
     *     {@code null} when it sits directly in a component
     */
    private void matchInstanceType(
            ComponentInstance importer,
            Import imp,
            InstanceType instanceType,
            ComponentInstance provider,
            InstanceTypeSpace enclosing) {
        var space = new InstanceTypeSpace(enclosing);
        for (var decl : instanceType.getInstanceDecls()) {
            switch (decl.kind()) {
                case CORE_TYPE:
                    space.addCoreType(decl.coreType());
                    break;
                case TYPE:
                    space.addLocalType(decl.type());
                    break;
                case ALIAS:
                    matchInstanceAliasDecl(space, importer, provider, imp, decl.alias());
                    break;
                case EXPORT_DECL:
                    matchInstanceExportDecl(space, importer, provider, imp, decl.exportDecl());
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Instance import type checking for " + decl + " not supported yet");
            }
        }
    }

    private void aliasInlineCoreExport(
            ComponentInstance.Builder builder,
            CoreInlineInstance instance,
            CoreSort coreSort,
            String name) {
        Object exported = instance.getExport(name);
        switch (coreSort) {
            case FUNC:
                builder.addCoreFunction((CoreFunction<?>) exported);
                return;
            case MEMORY:
                builder.addCoreMemory((Memory) exported);
                return;
            case TABLE:
                builder.addCoreTable((TableInstance) exported);
                return;
            case GLOBAL:
                builder.addCoreGlobal((GlobalInstance) exported);
                return;
            case TAG:
                builder.addCoreTag((TagInstance) exported);
                return;
            case MODULE:
                builder.addCoreModule((WasmModule) exported);
                return;
            default:
                throw new UnsupportedOperationException(
                        "Core export alias for sort " + coreSort + " not yet supported");
        }
    }

    /**
     * Walks out {@code alias.count()} enclosing scopes, following instantiations rather than
     * definitions so that an alias lands in the instantiation through which it was reached.
     */
    private ComponentInstance outerScope(ComponentInstance from, OuterAlias alias, int firstLevel) {
        ComponentInstance scope = from;
        for (int i = firstLevel; i <= alias.count(); i++) {
            scope = scope.lexicalScope();
            if (scope == null) {
                throw new LinkageException(
                        "Outer alias count " + alias.count() + " failed to resolve");
            }
        }
        return scope;
    }

    private static CoreFunction<?> resolveCoreFunction(
            ComponentInstance instance, int coreFuncIdx) {
        CoreFunction<?> coreFunc = instance.getCoreFunction(coreFuncIdx);
        if (coreFunc == null) {
            throw new LinkageException("Core function at index " + coreFuncIdx + " not found");
        }
        return coreFunc;
    }

    /**
     * Checks that a handle table index names a resource handle of the expected type. Resource types
     * are compared by identity, so two instantiations of one declaration are distinct types.
     */
    private static ResourceHandle requireResourceHandle(
            Object handle, int index, ResourceTypeInstance expected) {
        if (!(handle instanceof ResourceHandle)) {
            throw new TrapException("handle index " + index + " is not a resource handle");
        }
        var resourceHandle = (ResourceHandle) handle;
        if (resourceHandle.resourceType() != expected) {
            throw new TrapException(
                    "handle index "
                            + index
                            + " used with the wrong type, "
                            + ResourceTypeRef.mismatch(expected, resourceHandle.resourceType()));
        }
        return resourceHandle;
    }

    private void matchInstanceAliasDecl(
            InstanceTypeSpace space,
            ComponentInstance declStore,
            ComponentInstance provider,
            Import imp,
            Alias alias) {
        if (alias.kind() != Alias.Kind.OUTER) {
            throw new LinkageException(
                    "Instance alias of kind " + alias.kind() + " not supported yet");
        }
        var outerAlias = (OuterAlias) alias;
        int index = (int) outerAlias.index();
        InstanceTypeSpace nested = enclosingDeclaration(space, outerAlias);
        // Only an alias that reaches a component can be checked against the provider.
        if (nested == null
                && !matchInstanceOuterAliasDecl(provider, space, declStore, outerAlias)) {
            throw new LinkageException(
                    "Instance alias mismatch for '"
                            + imp.name()
                            + "' with description "
                            + imp.externDesc());
        }
        if (outerAlias.sort().kind() == Sort.Kind.CORE
                && outerAlias.sort().coreSort() == CoreSort.TYPE) {
            space.addCoreType(
                    nested != null
                            ? nested.coreTypeAt(index)
                            : resolveOuterAliasStore(space, declStore, outerAlias)
                                    .getCoreType(index));
            return;
        }
        if (outerAlias.sort().kind() == Sort.Kind.TYPE) {
            // Taken whole rather than re-resolved, because it already means what it meant where
            // it was written.
            space.addSlot(
                    nested != null
                            ? nested.slotAt(index)
                            : resolveOuterAliasStore(space, declStore, outerAlias).slotAt(index));
        }
    }

    /**
     * Checks one export a declared instance type requires against what the provider actually
     * exports, binding any type it introduces into {@code space}.
     */
    private void matchInstanceExportDecl(
            InstanceTypeSpace space,
            ComponentInstance importer,
            ComponentInstance provider,
            Import imp,
            ExportDecl exportDecl) {
        String name = exportDecl.name();
        ExternDesc externDesc = exportDecl.externDesc();
        if (externDesc.kind() == ExternDesc.Kind.TYPE) {
            matchInstanceTypeExportDecl(space, provider, imp, exportDecl);
            return;
        }
        if (!provider.hasExport(name)) {
            throw instanceExportNotFound(imp, name);
        }
        Object export = provider.getExport(name);
        switch (externDesc.kind()) {
            case CORE_MODULE:
                {
                    // A core module export is the module itself, not an instance of one.
                    requireInstanceExport(export instanceof WasmModule, imp, name, export);
                    CoreType declaredModule = space.coreTypeAt((int) externDesc.typeIdx());
                    if (declaredModule.moduleType() == null) {
                        throw new LinkageException(
                                "Instance export '"
                                        + name
                                        + "' of '"
                                        + imp.name()
                                        + "' is declared with a non-module core type");
                    }
                    CoreModuleMatcher.requireSubtype(
                            declaredModule.moduleType(), (WasmModule) export);
                    return;
                }
            case COMPONENT:
                {
                    requireInstanceExport(export instanceof ComponentClosure, imp, name, export);
                    ComponentType declared =
                            space.slotAt((int) externDesc.typeIdx()).type().componentType();
                    if (declared == null) {
                        throw new LinkageException(
                                "Instance export '"
                                        + name
                                        + "' of '"
                                        + imp.name()
                                        + "' is declared with a non-component type");
                    }
                    matchComponentType(
                            declared, ((ComponentClosure) export).definition(), imp, name);
                    return;
                }
            case INSTANCE:
                {
                    requireInstanceExport(export instanceof ComponentInstance, imp, name, export);
                    InstanceType declared =
                            space.slotAt((int) externDesc.typeIdx()).type().instanceType();
                    if (declared == null) {
                        throw new LinkageException(
                                "Instance export '"
                                        + name
                                        + "' of '"
                                        + imp.name()
                                        + "' is declared with a non-instance type");
                    }
                    // The nested declaration is a scope of its own, enclosed by this one.
                    matchInstanceType(importer, imp, declared, (ComponentInstance) export, space);
                    return;
                }
            case FUNC:
                {
                    requireInstanceExport(export instanceof ComponentFunction, imp, name, export);
                    var provided = (ComponentFunction) export;
                    var slot = space.slotAt((int) externDesc.typeIdx());
                    FuncType declared = slot.type().funcType();
                    if (declared == null) {
                        throw new LinkageException(
                                "Instance export '"
                                        + name
                                        + "' of '"
                                        + imp.name()
                                        + "' is declared with a non-function type");
                    }
                    if (!TypeMatcher.funcTypesMatch(
                            slot.func(), provided.resolvedFuncType(), !provided.hostProvided())) {
                        throw new LinkageException(
                                "Instance export '"
                                        + name
                                        + "' of '"
                                        + imp.name()
                                        + "' does not match - expected "
                                        + declared
                                        + ", got "
                                        + provided.funcType());
                    }
                    return;
                }
            default:
                throw new IllegalArgumentException("Unhandled export kind: " + externDesc.kind());
        }
    }

    /**
     * The enclosing instance type declaration an {@code alias outer} names, or {@code null} when
     * its count reaches past them all and into the components.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#alias-definitions">Explainer.md, outer alias scopes</a>
     */
    private static InstanceTypeSpace enclosingDeclaration(
            InstanceTypeSpace space, OuterAlias alias) {
        int count = (int) alias.count();
        return count <= space.enclosingDepth() ? space.enclosingAt(count) : null;
    }

    private boolean matchInstanceOuterAliasDecl(
            ComponentInstance instanceStore,
            InstanceTypeSpace space,
            ComponentInstance declStore,
            OuterAlias alias) {
        ComponentInstance containingStore = resolveOuterAliasStore(space, declStore, alias);
        switch (alias.sort().kind()) {
            case CORE:
                CoreSort coreSort = alias.sort().coreSort();
                switch (coreSort) {
                    case MODULE:
                        WasmModule declaredModule =
                                containingStore.getCoreModule((int) alias.index());
                        return instanceStore.getCoreModules().stream()
                                .anyMatch(module -> module.equals(declaredModule));
                    case TYPE:
                        // Naming a core type asks the provider for nothing.
                        containingStore.getCoreType((int) alias.index());
                        return true;
                    default:
                        throw new UnsupportedOperationException(
                                "Outer alias core sort " + coreSort + " not yet supported");
                }
            case COMPONENT:
                WasmComponent declaredComponent =
                        containingStore.getChildComponent((int) alias.index()).definition();
                return instanceStore.getChildComponents().stream()
                        .anyMatch(component -> component.definition().equals(declaredComponent));
            case TYPE:
                ComponentInstance.TypeSlot declared = containingStore.slotAt((int) alias.index());
                for (int i = 0; i < instanceStore.typeCount(); i++) {
                    if (sameType(declared, instanceStore.slotAt(i))) {
                        return true;
                    }
                }
                return false;
            default:
                throw new UnsupportedOperationException(
                        "Outer alias sort " + alias.sort() + " not yet supported");
        }
    }

    /**
     * Whether two type slots from different index spaces describe the same type.
     * A declaration that names other types does so by index,
     * so two identical records written in different spaces differ as parsed
     * and are compared in their resolved forms instead.
     */
    private static boolean sameType(
            ComponentInstance.TypeSlot declared, ComponentInstance.TypeSlot provided) {
        if (declared.type().equals(provided.type())) {
            return true;
        }
        if (declared.resourceType() != null || provided.resourceType() != null) {
            return declared.resourceType() == provided.resourceType();
        }
        try {
            return TypeMatcher.slotsMatch(declared, provided);
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * The component an {@code alias outer} reaches, once its count has been spent on whatever
     * instance type declarations enclose {@code space}.
     */
    private ComponentInstance resolveOuterAliasStore(
            InstanceTypeSpace space, ComponentInstance declStore, OuterAlias alias) {
        if (alias.count() == 0) {
            throw new LinkageException(
                    "Outer alias count 0 could not be resolved for instance outer alias decl");
        }
        // A count of one already means the enclosing component, so the walk starts one level
        // inward.
        return outerScope(declStore, alias, space.enclosingDepth() + 2);
    }

    /**
     * Matches a {@code type} export, which both constrains the provider and introduces a name the
     * rest of the instance type can use. What gets recorded is the runtime resource type, which is
     * what checks every later {@code own} and {@code borrow}.
     */
    private void matchInstanceTypeExportDecl(
            InstanceTypeSpace space,
            ComponentInstance provider,
            Import imp,
            ExportDecl exportDecl) {
        String name = exportDecl.name();
        TypeBound typeBound = exportDecl.externDesc().typeBound();
        switch (typeBound.kind()) {
            case SUB_RESOURCE:
                {
                    if (!provider.hasExport(name)) {
                        throw instanceExportNotFound(imp, name);
                    }
                    Object export = provider.getExport(name);
                    ResourceTypeInstance resourceType = resourceOf(export);
                    if (resourceType == null) {
                        throw new LinkageException(
                                "instance exports do not match - expected resource found "
                                        + sortOf(export));
                    }
                    space.addForeignType(resourceType.type(), provider.typeSpace(), resourceType);
                    return;
                }
            case EQ:
                {
                    var bound = space.slotAt((int) typeBound.typeIdx());
                    if (!provider.hasExport(name)) {
                        space.addSlot(bound);
                        return;
                    }
                    Object export = provider.getExport(name);
                    Type exported = typeOf(export);
                    requireInstanceExport(exported != null, imp, name, export);
                    ComponentInstance.TypeSlot exportedSlot = slotOf(export);
                    if (bound.resourceType() != null) {
                        if (resourceOf(export) != bound.resourceType()) {
                            throw new LinkageException("mismatched resource types");
                        }
                        space.addForeignType(exported, provider.typeSpace(), bound.resourceType());
                        return;
                    }
                    if (!TypeMatcher.slotsMatch(bound, exportedSlot)) {
                        throw new LinkageException(
                                "Instance export '"
                                        + name
                                        + "' of '"
                                        + imp.name()
                                        + "' does not match - expected "
                                        + bound.type()
                                        + ", got "
                                        + exported);
                    }
                    space.addForeignType(exported, provider.typeSpace(), null);
                    return;
                }
            default:
                throw new IllegalArgumentException(
                        "Unhandled type bound kind: " + typeBound.kind());
        }
    }

    private static LinkageException instanceExportNotFound(Import imp, String name) {
        return new LinkageException(
                "Instance export '"
                        + name
                        + "' was not found for '"
                        + imp.name()
                        + "' with description "
                        + imp.externDesc());
    }

    private static void requireInstanceExport(
            boolean condition, Import imp, String name, Object export) {
        if (!condition) {
            throw new LinkageException(
                    "Instance export '"
                            + name
                            + "' of '"
                            + imp.name()
                            + "' is of the wrong sort - got "
                            + (export == null ? "nothing" : export.getClass().getSimpleName()));
        }
    }

    /** Checks a component against the {@code (component ...)} type an instance type declares. */
    private void matchComponentType(
            ComponentType declared, WasmComponent actual, Import imp, String name) {
        ComponentTypeMatcher.requireSubtype(
                declared, actual, "Instance export '" + name + "' of '" + imp.name() + "'");
    }

    public static final class Builder {

        private Builder() {}

        public ComponentLinker build() {
            return new ComponentLinker();
        }
    }
}
