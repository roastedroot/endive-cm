package run.endive.cm.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import run.endive.cm.types.Alias;
import run.endive.cm.types.AliasSection;
import run.endive.cm.types.Canon;
import run.endive.cm.types.CanonLift;
import run.endive.cm.types.CanonLower;
import run.endive.cm.types.CanonSection;
import run.endive.cm.types.ComponentSection;
import run.endive.cm.types.CoreExportAlias;
import run.endive.cm.types.CoreInstance;
import run.endive.cm.types.CoreInstanceExpr;
import run.endive.cm.types.CoreInstanceSection;
import run.endive.cm.types.CoreInstantiateInstanceExpr;
import run.endive.cm.types.CoreModuleSection;
import run.endive.cm.types.CoreSort;
import run.endive.cm.types.CoreTypeSection;
import run.endive.cm.types.CustomSection;
import run.endive.cm.types.Export;
import run.endive.cm.types.ExportAlias;
import run.endive.cm.types.ExportSection;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.ImportSection;
import run.endive.cm.types.InstanceSection;
import run.endive.cm.types.Section;
import run.endive.cm.types.Sort;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeSection;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.Machine;

public final class ComponentLinker {

    private final Function<Instance, Machine> machineFactory;

    private ComponentLinker(Function<Instance, Machine> machineFactory) {
        this.machineFactory = machineFactory;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ComponentInstance instantiate(WasmComponent component) {
        ComponentStore store = new ComponentStore();
        Map<String, Object> exports = new LinkedHashMap<>();

        for (Section section : component.sections()) {
            if (section instanceof CoreModuleSection) {
                processCoreModuleSection(store, (CoreModuleSection) section);
            } else if (section instanceof CoreInstanceSection) {
                processCoreInstanceSection(store, (CoreInstanceSection) section);
            } else if (section instanceof AliasSection) {
                processAliasSection(store, (AliasSection) section);
            } else if (section instanceof TypeSection) {
                processTypeSection(store, (TypeSection) section);
            } else if (section instanceof CanonSection) {
                processCanonSection(store, (CanonSection) section);
            } else if (section instanceof ExportSection) {
                processExportSection(store, (ExportSection) section, exports);
            } else if (section instanceof ComponentSection) {
                processComponentSection(store, (ComponentSection) section);
            } else if (section instanceof CoreTypeSection) {
                // Core types are stored but not used for instantiation in MVP
            } else if (section instanceof CustomSection) {
                // Custom sections are ignored
            } else if (section instanceof ImportSection) {
                throw new UnsupportedOperationException(
                        "Import section not yet supported in component instantiation");
            } else if (section instanceof InstanceSection) {
                throw new UnsupportedOperationException(
                        "Instance section not yet supported in component instantiation");
            } else {
                throw new LinkageException("Unknown section type: " + section.getClass().getName());
            }
        }

        return new ComponentInstance(exports);
    }

    public static final class Builder {

        private Function<Instance, Machine> machineFactory;

        private Builder() {}

        public Builder withMachineFactory(Function<Instance, Machine> machineFactory) {
            this.machineFactory = machineFactory;
            return this;
        }

        public ComponentLinker build() {
            return new ComponentLinker(machineFactory);
        }
    }

    private void processCoreModuleSection(ComponentStore store, CoreModuleSection section) {
        store.addCoreModule(section.module());
    }

    private void processCoreInstanceSection(ComponentStore store, CoreInstanceSection section) {
        for (CoreInstance coreInstance : section.instances()) {
            CoreInstanceExpr expr = coreInstance.expr();
            if (expr instanceof CoreInstantiateInstanceExpr) {
                instantiateCoreModule(store, (CoreInstantiateInstanceExpr) expr);
            } else {
                throw new UnsupportedOperationException(
                        "Core instance expression kind " + expr.kind() + " not yet supported");
            }
        }
    }

    private void instantiateCoreModule(ComponentStore store, CoreInstantiateInstanceExpr expr) {
        int moduleIdx = (int) expr.moduleIdx();
        var module = store.getCoreModule(moduleIdx);

        ImportValues importValues;
        if (expr.instantiateArgs().isEmpty()) {
            importValues = ImportValues.empty();
        } else {
            throw new UnsupportedOperationException(
                    "Core module instantiation with import arguments not yet supported");
        }

        Instance.Builder builder = Instance.builder(module).withImportValues(importValues);
        if (machineFactory != null) {
            builder.withMachineFactory(machineFactory);
        }
        store.addCoreInstance(builder.build());
    }

    private void processAliasSection(ComponentStore store, AliasSection section) {
        for (Alias alias : section.aliases()) {
            if (alias instanceof CoreExportAlias) {
                processCoreExportAlias(store, (CoreExportAlias) alias);
            } else if (alias instanceof ExportAlias) {
                throw new UnsupportedOperationException(
                        "Export alias not yet supported in component instantiation");
            } else {
                throw new UnsupportedOperationException(
                        "Alias kind " + alias.kind() + " not yet supported");
            }
        }
    }

    private void processCoreExportAlias(ComponentStore store, CoreExportAlias alias) {
        Sort sort = alias.sort();
        if (sort.kind() != Sort.Kind.CORE) {
            throw new LinkageException(
                    "Expected CORE sort for core export alias, got " + sort.kind());
        }

        CoreSort coreSort = sort.coreSort();
        int instanceIdx = (int) alias.instanceIdx();
        String name = alias.name();
        Instance instance = store.getCoreInstance(instanceIdx);

        if (coreSort == CoreSort.FUNC) {
            ExportFunction fn = instance.export(name);
            store.addCoreFunction(fn);
        } else {
            throw new UnsupportedOperationException(
                    "Core export alias for sort " + coreSort + " not yet supported");
        }
    }

    private void processTypeSection(ComponentStore store, TypeSection section) {
        for (Type type : section.types()) {
            store.addType(type);
        }
    }

    private void processCanonSection(ComponentStore store, CanonSection section) {
        for (Canon canon : section.canons()) {
            if (canon instanceof CanonLift) {
                processCanonLift(store, (CanonLift) canon);
            } else if (canon instanceof CanonLower) {
                throw new UnsupportedOperationException(
                        "Canon lower not yet supported in component instantiation");
            } else {
                throw new UnsupportedOperationException(
                        "Canon kind " + canon.kind() + " not yet supported");
            }
        }
    }

    private static final Object[] EMPTY = new Object[0];

    private void processCanonLift(ComponentStore store, CanonLift lift) {
        int coreFuncIdx = (int) lift.funcIdx().idx();
        ExportFunction coreFunc = store.getCoreFunction(coreFuncIdx);

        Type type = store.getType((int) lift.typeIdx());
        FuncType funcType = type.funcType();
        if (funcType == null) {
            throw new LinkageException(
                    "canon lift type index " + lift.typeIdx() + " is not a FuncType");
        }

        if (!funcType.params().isEmpty()) {
            throw new UnsupportedOperationException("canon lift with parameters not yet supported");
        }
        if (funcType.hasResult()) {
            throw new UnsupportedOperationException("canon lift with result not yet supported");
        }

        // TODO: implement proper lifting/lowering in the canonical ABI module
        ComponentFunction componentFunc =
                (args) -> {
                    coreFunc.apply();
                    return EMPTY;
                };
        store.addFunction(componentFunc);
    }

    private void processExportSection(
            ComponentStore store, ExportSection section, Map<String, Object> exports) {
        for (Export export : section.exports()) {
            String name = export.name();
            Sort sort = export.sortIdx().sort();
            int idx = (int) export.sortIdx().idx();

            if (sort.kind() == Sort.Kind.FUNC) {
                ComponentFunction func = store.getFunction(idx);
                exports.put(name, func);
            } else {
                throw new UnsupportedOperationException(
                        "Export of sort " + sort.kind() + " not yet supported");
            }
        }
    }

    private void processComponentSection(ComponentStore store, ComponentSection section) {
        store.addComponent(section.component());
    }
}
