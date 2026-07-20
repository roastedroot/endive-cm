package run.endive.cm.runtime;

import java.util.ArrayList;
import java.util.List;
import run.endive.cm.types.Type;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.Instance;
import run.endive.wasm.WasmModule;

final class ComponentStore {

    private final List<WasmModule> coreModules = new ArrayList<>();
    private final List<Instance> coreInstances = new ArrayList<>();
    private final List<ExportFunction> coreFunctions = new ArrayList<>();
    private final List<ComponentFunction> functions = new ArrayList<>();
    private final List<Type> types = new ArrayList<>();
    private final List<WasmComponent> components = new ArrayList<>();

    void addCoreModule(WasmModule module) {
        coreModules.add(module);
    }

    WasmModule getCoreModule(int index) {
        if (index < 0 || index >= coreModules.size()) {
            throw new LinkageException(
                    "Core module index "
                            + index
                            + " out of bounds (size "
                            + coreModules.size()
                            + ")");
        }
        return coreModules.get(index);
    }

    void addCoreInstance(Instance instance) {
        coreInstances.add(instance);
    }

    Instance getCoreInstance(int index) {
        if (index < 0 || index >= coreInstances.size()) {
            throw new LinkageException(
                    "Core instance index "
                            + index
                            + " out of bounds (size "
                            + coreInstances.size()
                            + ")");
        }
        return coreInstances.get(index);
    }

    void addCoreFunction(ExportFunction function) {
        coreFunctions.add(function);
    }

    ExportFunction getCoreFunction(int index) {
        if (index < 0 || index >= coreFunctions.size()) {
            throw new LinkageException(
                    "Core function index "
                            + index
                            + " out of bounds (size "
                            + coreFunctions.size()
                            + ")");
        }
        return coreFunctions.get(index);
    }

    void addFunction(ComponentFunction function) {
        functions.add(function);
    }

    ComponentFunction getFunction(int index) {
        if (index < 0 || index >= functions.size()) {
            throw new LinkageException(
                    "Component function index "
                            + index
                            + " out of bounds (size "
                            + functions.size()
                            + ")");
        }
        return functions.get(index);
    }

    void addType(Type type) {
        types.add(type);
    }

    Type getType(int index) {
        if (index < 0 || index >= types.size()) {
            throw new LinkageException(
                    "Type index " + index + " out of bounds (size " + types.size() + ")");
        }
        return types.get(index);
    }

    void addComponent(WasmComponent component) {
        components.add(component);
    }
}
