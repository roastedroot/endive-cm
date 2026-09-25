package run.endive.cm.bindgen.approvals;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import javax.tools.JavaFileObject;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.BindgenProcessor;

/**
 * Everything one world generates, approved as a single file, which is the API an embedder writes
 * against and so is the review of any generator change.
 *
 * <p>To approve everything use the env var: {@code APPROVAL_TESTS_USE_REPORTER=AutoApproveReporter}
 */
public class ApprovalTest {

    @Test
    public void verifyHelloWorld() {
        verifyGeneratedBindings("HelloWorldHost.java");
    }

    @Test
    public void verifyWorldImports() {
        verifyGeneratedBindings("WorldImportsHost.java");
    }

    @Test
    public void verifyWorldExports() {
        verifyGeneratedBindings("WorldExportsHost.java");
    }

    @Test
    public void verifyWorldExportKinds() {
        verifyGeneratedBindings("WorldExportKindsHost.java");
    }

    @Test
    public void verifyInterfaceImports() {
        verifyGeneratedBindings("InterfaceImportsHost.java");
    }

    @Test
    public void verifyImportedResources() {
        verifyGeneratedBindings("ImportedResourceHost.java");
    }

    @Test
    public void verifyExportedResources() {
        verifyGeneratedBindings("ExportedResourceHost.java");
    }

    @Test
    public void verifyStaticFunctions() {
        verifyGeneratedBindings("StaticFunctionHost.java");
    }

    @Test
    public void verifyRecords() {
        verifyGeneratedBindings("RecordHost.java");
    }

    @Test
    public void verifyVariants() {
        verifyGeneratedBindings("VariantHost.java");
    }

    @Test
    public void verifyFlags() {
        verifyGeneratedBindings("FlagsHost.java");
    }

    @Test
    public void verifyTuples() {
        verifyGeneratedBindings("TupleHost.java");
    }

    @Test
    public void verifyOptions() {
        verifyGeneratedBindings("OptionHost.java");
    }

    @Test
    public void verifyResults() {
        verifyGeneratedBindings("ResultHost.java");
    }

    /**
     * Sources are ordered by name and headed by it, so that the package a world lays out is part of
     * what is approved rather than something a separate assertion has to repeat.
     */
    private static void verifyGeneratedBindings(String host) {
        Compilation compilation =
                javac().withProcessors(new BindgenProcessor())
                        .withClasspathFrom(ApprovalTest.class.getClassLoader())
                        .compile(JavaFileObjects.forResource(host));

        assertThat(compilation).succeededWithoutWarnings();

        List<JavaFileObject> sources =
                compilation.generatedSourceFiles().stream()
                        .sorted(Comparator.comparing(JavaFileObject::getName))
                        .collect(java.util.stream.Collectors.toList());

        StringBuilder approved = new StringBuilder();
        for (JavaFileObject source : sources) {
            approved.append("// ").append(qualifiedName(source.getName())).append("\n\n");
            approved.append(contents(source).strip()).append("\n\n");
        }
        Approvals.verify(approved.toString().strip() + "\n");
    }

    private static String qualifiedName(String path) {
        return path.replace("/SOURCE_OUTPUT/", "").replace(".java", "").replace('/', '.');
    }

    private static String contents(JavaFileObject source) {
        try {
            return source.getCharContent(true).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
