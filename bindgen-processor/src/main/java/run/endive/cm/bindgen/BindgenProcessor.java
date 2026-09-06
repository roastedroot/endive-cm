package run.endive.cm.bindgen;

import static javax.tools.Diagnostic.Kind.ERROR;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import run.endive.cm.runtime.Bindgen;

/**
 * Generates host-side bindings for the WIT worlds named by {@link Bindgen}.
 *
 * <p>WIT is read, encoded to its binary form by wasm-tools, and parsed back with the component
 * parser, so the types the bindings are generated from come from the same toolchain that validates
 * components rather than from a WIT reader of this project's own.
 */
public final class BindgenProcessor extends AbstractProcessor {

    private static final String WIT_ROOT = "wit/";

    /** What has already been written, so that worlds sharing an interface share its Java type. */
    private final Map<String, String> written = new HashMap<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Bindgen.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element element : round.getElementsAnnotatedWith(Bindgen.class)) {
            try {
                generate(element, element.getAnnotation(Bindgen.class));
            } catch (BindgenException e) {
                processingEnv.getMessager().printMessage(ERROR, e.getMessage(), element);
            } catch (RuntimeException e) {
                // Reported against the annotation rather than thrown, so that a world this cannot
                // yet handle fails the compilation it belongs to instead of crashing javac.
                processingEnv
                        .getMessager()
                        .printMessage(
                                ERROR,
                                "bindings could not be generated: "
                                        + e.getClass().getSimpleName()
                                        + ": "
                                        + e.getMessage(),
                                element);
            }
        }
        return false;
    }

    private void generate(Element element, Bindgen bindgen) {
        WitWorld world = WorldReader.read(readWit(bindgen), bindgen.world());

        String packageName = packageOf(element).getQualifiedName().toString();
        for (GeneratedUnit generated : WorldGenerator.generate(world, packageName, generatedBy())) {
            write(element, world, generated);
        }
    }

    /**
     * Writes one generated source, unless an identical one is already there.
     *
     * <p>Two worlds in one package that name the same interface generate the same file, and one
     * Java type serving both is what an embedder wants. Two that disagree are a genuine conflict,
     * so that is reported rather than resolved by whichever ran first.
     */
    private void write(Element element, WitWorld world, GeneratedUnit generated) {
        String name = generated.qualifiedName();
        String contents = generated.contents();
        String existing = written.get(name);
        if (existing != null) {
            if (!existing.equals(contents)) {
                throw new BindgenException(
                        name
                                + " is generated differently by world \""
                                + world.name()
                                + "\" than by a world already read in this package");
            }
            return;
        }

        try (Writer writer = filer().createSourceFile(name, element).openWriter()) {
            writer.write(contents);
        } catch (IOException e) {
            throw new BindgenException("could not write " + name + ": " + e.getMessage(), e);
        }
        written.put(name, contents);
    }

    private String generatedBy() {
        return getClass().getCanonicalName();
    }

    /**
     * WIT comes from {@code inline} when it is given, otherwise from a resource, which is
     * {@code wit/<world>.wit} unless {@code path} says otherwise.
     */
    private String readWit(Bindgen bindgen) {
        if (!bindgen.inline().isEmpty()) {
            if (!bindgen.path().isEmpty()) {
                throw new BindgenException("only one of inline and path may be given");
            }
            return bindgen.inline();
        }

        String path = bindgen.path();
        if (path.isEmpty()) {
            if (bindgen.world().isEmpty()) {
                throw new BindgenException(
                        "a world, a path or inline WIT is needed to say which WIT to read");
            }
            path = WIT_ROOT + bindgen.world() + ".wit";
        }
        return readResource(path);
    }

    /**
     * Maven copies resources into the class output before compiling, so that is where a WIT file in
     * this project lands. The class path covers WIT arriving inside a dependency, which is how a
     * shared world such as WASI is consumed.
     */
    private String readResource(String path) {
        for (StandardLocation location :
                new StandardLocation[] {
                    StandardLocation.CLASS_OUTPUT, StandardLocation.CLASS_PATH
                }) {
            try {
                FileObject resource = filer().getResource(location, "", path);
                return resource.getCharContent(true).toString();
            } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) {
                // Not here, so try the next location.
            }
        }
        throw new BindgenException(
                "WIT file \""
                        + path
                        + "\" was not found on the class output or the class path. A WIT file"
                        + " belongs in src/main/resources/"
                        + WIT_ROOT);
    }

    private Filer filer() {
        return processingEnv.getFiler();
    }

    private static PackageElement packageOf(Element element) {
        Element enclosing = element;
        while (enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        return (PackageElement) enclosing;
    }
}
