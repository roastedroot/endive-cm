package io.roastedroot.typewire.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates host-side bindings for a WIT world, in the package of whatever carries this.
 *
 * <p>The world's imports become an interface the embedder implements and its exports become typed
 * methods, so that calling a component takes Java types rather than an array of objects.
 *
 * <p>WIT is read from the {@code wit} resource directory, which in a conventional Maven project
 * means {@code src/main/resources/wit}. Give {@link #inline} instead when the WIT is small enough
 * to sit beside the code that uses it.
 *
 * <p>A WIT file whose directory holds a {@code deps} directory is read together with the rest of
 * that directory, so a world may import from the packages under {@code deps}.
 *
 * <pre>{@code
 * @Bindgen(world = "hello-world")                              // reads wit/hello-world.wit
 * @Bindgen(world = "calculator", path = "wit/calc.wit")
 * @Bindgen(world = "host", path = "wit/host.wit")              // also reads wit/*.wit and wit/deps
 * @Bindgen(inline = "package my:project;\nworld hello-world { ... }")
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface Bindgen {

    /**
     * The world whose bindings are generated. May be left out when the package declares exactly
     * one, in which case {@link #path} or {@link #inline} has to say where the WIT is.
     */
    String world() default "";

    /**
     * Resource path of the WIT file, defaulting to {@code wit/<world>.wit}. When the WIT arrives in a
     * dependency jar, javac finds the file only if every directory on its path is named like a Java
     * identifier.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/WIT.md#root-package-a-directory">Root Package: A Directory</a>
     */
    String path() default "";

    /** WIT text, for a world not worth a file of its own. Not to be combined with {@link #path}. */
    String inline() default "";
}
