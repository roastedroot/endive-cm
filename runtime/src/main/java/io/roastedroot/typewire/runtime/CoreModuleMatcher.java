package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.types.CoreExportDecl;
import io.roastedroot.typewire.types.CoreFunctionImportDesc;
import io.roastedroot.typewire.types.CoreGlobalImportDesc;
import io.roastedroot.typewire.types.CoreImportDesc;
import io.roastedroot.typewire.types.CoreMemoryImportDesc;
import io.roastedroot.typewire.types.CoreTableImportDesc;
import io.roastedroot.typewire.types.CoreType;
import io.roastedroot.typewire.types.ModuleDecl;
import io.roastedroot.typewire.types.ModuleType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.Export;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.FunctionImport;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.Global;
import run.endive.wasm.types.GlobalImport;
import run.endive.wasm.types.Import;
import run.endive.wasm.types.MemoryImport;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.TableImport;
import run.endive.wasm.types.TableLimits;
import run.endive.wasm.types.ValType;

/**
 * Checks a core module against the {@code (core module ...)} type an import declares.
 *
 * <p>Exports are covariant, so the module must export everything the type promises and may export
 * more. Imports are contravariant, so the type must declare everything the module needs and may
 * declare more. The two directions therefore reverse which side is the expectation and which
 * answers it.
 *
 * <p>A module type carries its own core type index space, so both sides are resolved to a
 * {@link FunctionType} before comparison.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#type-definitions">Explainer.md, core module types</a>
 */
final class CoreModuleMatcher {

    private final WasmModule actual;
    private final List<CoreType> declaredTypes = new ArrayList<>();
    private final List<Import> declaredImports = new ArrayList<>();
    private final Map<String, CoreExportDecl> declaredExports = new LinkedHashMap<>();
    private final List<Import> actualImports;

    private CoreModuleMatcher(ModuleType declared, WasmModule actual) {
        this.actual = actual;
        this.actualImports =
                actual.importSection().stream().collect(java.util.stream.Collectors.toList());
        for (ModuleDecl decl : declared.getModuleDecls()) {
            if (decl.type() != null) {
                declaredTypes.add(decl.type());
            } else if (decl.importDecl() != null) {
                declaredImports.add(decl.importDecl().coreImport());
            } else if (decl.exportDecl() != null) {
                declaredExports.put(decl.exportDecl().name(), decl.exportDecl());
            } else {
                throw new LinkageException(
                        "Core module type alias declarations are not supported yet");
            }
        }
    }

    /**
     * @throws LinkageException if {@code actual} cannot stand in for {@code declared}
     */
    static void requireSubtype(ModuleType declared, WasmModule actual) {
        var matcher = new CoreModuleMatcher(declared, actual);
        matcher.checkExports();
        matcher.checkImports();
    }

    private void checkExports() {
        for (Map.Entry<String, CoreExportDecl> entry : declaredExports.entrySet()) {
            String name = entry.getKey();
            Export export = findExport(name);
            if (export == null) {
                throw new LinkageException("module export `" + name + "` not defined");
            }
            CoreImportDesc want = entry.getValue().coreImportDesc();
            if (want.type() != export.exportType()) {
                throw new LinkageException(
                        "expected "
                                + sortOf(want.type())
                                + " found "
                                + sortOf(export.exportType()));
            }
            checkExportType(name, want, export);
        }
    }

    private void checkExportType(String name, CoreImportDesc want, Export export) {
        switch (want.type()) {
            case FUNCTION:
                {
                    FunctionType wanted =
                            declaredFuncType(((CoreFunctionImportDesc) want).typeIndex());
                    FunctionType got = actualFuncType(export.index());
                    if (!wanted.equals(got)) {
                        throw wrongExportType(
                                name,
                                "expected type `"
                                        + render(wanted)
                                        + "`, found type `"
                                        + render(got)
                                        + "`");
                    }
                    return;
                }
            case TABLE:
                {
                    var wanted = (CoreTableImportDesc) want;
                    var table = actualTable(export.index());
                    if (!wanted.entryType().equals(table.elementType())
                            || !fits(table.limits(), wanted.limits())) {
                        throw wrongExportType(name, null);
                    }
                    return;
                }
            case MEMORY:
                {
                    var wanted = (CoreMemoryImportDesc) want;
                    if (!fits(actualMemory(export.index()), wanted.limits())) {
                        throw wrongExportType(name, null);
                    }
                    return;
                }
            case GLOBAL:
                {
                    var wanted = (CoreGlobalImportDesc) want;
                    var global = actualGlobal(export.index());
                    if (!wanted.valType().equals(global.valType())
                            || wanted.mutabilityType() != global.mutability()) {
                        throw wrongExportType(name, null);
                    }
                    return;
                }
            default:
                throw new LinkageException(
                        "Core module export of type " + want.type() + " is not supported yet");
        }
    }

    /**
     * Every import the module needs must be declared, because the declaration is the whole of what
     * an importing component knows to supply. Imports the module does not need go unused.
     */
    private void checkImports() {
        for (Import needed : actualImports) {
            Import supplied = findDeclaredImport(needed.module(), needed.name());
            String label = "`" + needed.module() + "::" + needed.name() + "`";
            if (supplied == null) {
                throw new LinkageException("module import " + label + " not defined");
            }
            if (needed.importType() != supplied.importType()) {
                // Reversed against the export direction, the module stating the requirement.
                throw new LinkageException(
                        "expected "
                                + sortOf(needed.importType())
                                + " found "
                                + sortOf(supplied.importType()));
            }
            if (!importSatisfies(supplied, needed)) {
                throw new LinkageException("module import " + label + " has the wrong type");
            }
        }
    }

    /** Whether what the type declares is usable as the import the module asks for. */
    private boolean importSatisfies(Import supplied, Import needed) {
        switch (needed.importType()) {
            case FUNCTION:
                return declaredFuncType(((FunctionImport) supplied).typeIndex())
                        .equals(
                                actual.typeSection()
                                        .getType(((FunctionImport) needed).typeIndex()));
            case TABLE:
                {
                    var s = (TableImport) supplied;
                    var n = (TableImport) needed;
                    return s.entryType().equals(n.entryType()) && fits(s.limits(), n.limits());
                }
            case MEMORY:
                return fits(((MemoryImport) supplied).limits(), ((MemoryImport) needed).limits());
            case GLOBAL:
                {
                    var s = (GlobalImport) supplied;
                    var n = (GlobalImport) needed;
                    return s.type().equals(n.type()) && s.mutabilityType() == n.mutabilityType();
                }
            default:
                throw new LinkageException(
                        "Core module import of type "
                                + needed.importType()
                                + " is not supported yet");
        }
    }

    /**
     * A mismatch on a matching sort. Both wordings the spec tests look for are reported together,
     * because the suite refers to the same failure in different places.
     */
    private LinkageException wrongExportType(String name, String detail) {
        String message = "export `" + name + "` has the wrong type";
        return new LinkageException(detail == null ? message : message + " - " + detail);
    }

    // -------------------------------------------------------------------------------------
    // Resolving the two sides
    // -------------------------------------------------------------------------------------

    private FunctionType declaredFuncType(long typeIndex) {
        int idx = (int) typeIndex;
        if (idx < 0 || idx >= declaredTypes.size()) {
            throw new LinkageException(
                    "Core module type index "
                            + idx
                            + " out of bounds (size "
                            + declaredTypes.size()
                            + ")");
        }
        var recType = declaredTypes.get(idx).recType();
        if (recType == null) {
            throw new LinkageException("Core type at index " + idx + " is not a function type");
        }
        if (recType.isLegacy()) {
            return recType.legacy();
        }
        return recType.subTypes()[0].compType().funcType();
    }

    private Export findExport(String name) {
        var exports = actual.exportSection();
        for (int i = 0; i < exports.exportCount(); i++) {
            Export export = exports.getExport(i);
            if (export.name().equals(name)) {
                return export;
            }
        }
        return null;
    }

    private Import findDeclaredImport(String module, String name) {
        for (Import declared : declaredImports) {
            if (declared.module().equals(module) && declared.name().equals(name)) {
                return declared;
            }
        }
        return null;
    }

    /**
     * An index space starts with the module's imports of that sort, in import order, and continues
     * into the ones it defines, so an exported index may name either.
     */
    private <T> T resolveIndex(
            ExternalType sort,
            int index,
            java.util.function.Function<Import, T> fromImport,
            java.util.function.IntFunction<T> fromDefinition) {
        int seen = 0;
        for (Import imported : actualImports) {
            if (imported.importType() == sort) {
                if (seen == index) {
                    return fromImport.apply(imported);
                }
                seen++;
            }
        }
        return fromDefinition.apply(index - seen);
    }

    private FunctionType actualFuncType(int index) {
        return resolveIndex(
                ExternalType.FUNCTION,
                index,
                imported -> actual.typeSection().getType(((FunctionImport) imported).typeIndex()),
                local -> actual.functionSection().getFunctionType(local, actual.typeSection()));
    }

    private TableLike actualTable(int index) {
        return resolveIndex(
                ExternalType.TABLE,
                index,
                imported ->
                        new TableLike(
                                ((TableImport) imported).entryType(),
                                ((TableImport) imported).limits()),
                local -> {
                    var table = actual.tableSection().getTable(local);
                    return new TableLike(table.elementType(), table.limits());
                });
    }

    private MemoryLimits actualMemory(int index) {
        return resolveIndex(
                ExternalType.MEMORY,
                index,
                imported -> ((MemoryImport) imported).limits(),
                local ->
                        actual.memorySection()
                                .orElseThrow(
                                        () -> new LinkageException("module has no memory section"))
                                .getMemory(local)
                                .limits());
    }

    private GlobalLike actualGlobal(int index) {
        return resolveIndex(
                ExternalType.GLOBAL,
                index,
                imported ->
                        new GlobalLike(
                                ((GlobalImport) imported).type(),
                                ((GlobalImport) imported).mutabilityType()),
                local -> {
                    Global global = actual.globalSection().getGlobal(local);
                    return new GlobalLike(global.valueType(), global.mutabilityType());
                });
    }

    // -------------------------------------------------------------------------------------
    // Subtyping of the leaf types
    // -------------------------------------------------------------------------------------

    /**
     * Whether a resource sized {@code actual} can be used where {@code declared} is expected. It
     * must start at least as large, and may not be allowed to grow beyond what was promised. Both
     * sides carry a concrete maximum, since an absent maximum parses to a sentinel, so no unbounded
     * case needs distinguishing.
     */
    private static boolean fits(TableLimits actual, TableLimits declared) {
        return actual.min() >= declared.min() && actual.max() <= declared.max();
    }

    private static boolean fits(MemoryLimits actual, MemoryLimits declared) {
        return actual.initialPages() >= declared.initialPages()
                && actual.maximumPages() <= declared.maximumPages();
    }

    /** How the Component Model's linkage diagnostics name a core sort. */
    private static String sortOf(ExternalType type) {
        switch (type) {
            case FUNCTION:
                return "func";
            case TABLE:
                return "table";
            case MEMORY:
                return "memory";
            case GLOBAL:
                return "global";
            case TAG:
                return "tag";
            default:
                return type.toString().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Renders a core function type the way the spec's diagnostics spell it. */
    private static String render(FunctionType type) {
        var sb = new StringBuilder("func");
        for (ValType param : type.params()) {
            sb.append(" (param ").append(render(param)).append(')');
        }
        for (ValType result : type.returns()) {
            sb.append(" (result ").append(render(result)).append(')');
        }
        return '(' + sb.toString() + ')';
    }

    private static String render(ValType type) {
        return type.toString().toLowerCase(java.util.Locale.ROOT);
    }

    /** A table's type, however it entered the index space. */
    private static final class TableLike {
        private final ValType elementType;
        private final TableLimits limits;

        TableLike(ValType elementType, TableLimits limits) {
            this.elementType = elementType;
            this.limits = limits;
        }

        ValType elementType() {
            return elementType;
        }

        TableLimits limits() {
            return limits;
        }
    }

    /** A global's type, however it entered the index space. */
    private static final class GlobalLike {
        private final ValType valType;
        private final MutabilityType mutability;

        GlobalLike(ValType valType, MutabilityType mutability) {
            this.valType = valType;
            this.mutability = mutability;
        }

        ValType valType() {
            return valType;
        }

        MutabilityType mutability() {
            return mutability;
        }
    }
}
