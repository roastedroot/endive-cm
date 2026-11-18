package io.roastedroot.typewire.parser;

import io.roastedroot.typewire.types.AliasSection;
import io.roastedroot.typewire.types.CanonSection;
import io.roastedroot.typewire.types.Export;
import io.roastedroot.typewire.types.ExportSection;
import io.roastedroot.typewire.types.ExternDesc;
import io.roastedroot.typewire.types.Import;
import io.roastedroot.typewire.types.ImportSection;
import io.roastedroot.typewire.types.Section;
import io.roastedroot.typewire.types.Sort;
import io.roastedroot.typewire.types.WasmComponent;
import io.roastedroot.typewire.types.canon.CanonLift;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface Validator {

    void validateBinary(InputStream wasm) throws ComponentValidationException;

    /**
     * Applies validations to a fully parsed WasmComponent. <p> The default implementation applies
     * the following rules require by the spec test suite: A root component cannot import a
     * component because nothing outside it exists to supply one. It cannot export a component
     * because there is no one to receive it. It cannot re-export a function it merely imported
     * because that function has no implementation to stand behind the export. </p>
     */
    default void validateWasmComponent(WasmComponent component)
            throws ComponentValidationException {
        // Whether the function at each index of the component's function index space came from
        // an import, tracked in the order the sections build that space.
        List<Boolean> functionIsImported = new ArrayList<>();
        for (Section section : component.sections()) {
            if (section instanceof ImportSection) {
                for (Import imp : ((ImportSection) section).imports()) {
                    if (imp.externDesc().kind() == ExternDesc.Kind.COMPONENT) {
                        throw new ComponentValidationException(
                                "root-level component imports are not supported");
                    }
                    if (imp.externDesc().kind() == ExternDesc.Kind.FUNC) {
                        functionIsImported.add(true);
                    }
                }
            } else if (section instanceof CanonSection) {
                for (var canon : ((CanonSection) section).canons()) {
                    // Only `canon lift` produces a component-level function.
                    if (canon instanceof CanonLift) {
                        functionIsImported.add(false);
                    }
                }
            } else if (section instanceof AliasSection) {
                for (var alias : ((AliasSection) section).aliases()) {
                    if (alias.sort().kind() == Sort.Kind.FUNC) {
                        functionIsImported.add(false);
                    }
                }
            } else if (section instanceof ExportSection) {
                for (Export export : ((ExportSection) section).exports()) {
                    checkExport(export, functionIsImported);
                    if (export.sortIdx().sort().kind() == Sort.Kind.FUNC) {
                        functionIsImported.add(false);
                    }
                }
            }
        }
    }

    private static void checkExport(Export export, List<Boolean> functionIsImported) {
        Sort.Kind sort = export.sortIdx().sort().kind();
        if (sort == Sort.Kind.COMPONENT) {
            throw new ComponentValidationException(
                    "exporting a component from the root component is not supported");
        }
        if (sort != Sort.Kind.FUNC) {
            return;
        }
        int idx = (int) export.sortIdx().idx();
        if (idx >= 0 && idx < functionIsImported.size() && functionIsImported.get(idx)) {
            throw new ComponentValidationException(
                    "component export `"
                            + export.name()
                            + "` is a reexport of an imported function which is not implemented");
        }
    }
}
