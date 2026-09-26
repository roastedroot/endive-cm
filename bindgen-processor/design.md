# Bindgen Processor High-Level Design

This module will provide WIT bindgen support for making it simple to build out host-side functionality that is targeting
a specific WIT world.

## Implementation Approach

We should take an approach that mimics the bindgen! macro that is used by Wasmtime, using an annotation processor as an
equivalent to the macro so that the necessary bindings may be generated as part of the Java compilation process.

This bindgen-processor skeleton Maven module has been created to house this, and an initial Bindgen annotation has been
added to the runtime module.

This functionality should initially just focus on WIT and the Component Model, but it should eventually become usable to
build WASI p2+ implementations. As this evolves, it may be necessary to further evolve the API of the runtime module to
make it simpler to provide host-side functions, etc.

The tests for bindgen-processor should mimic the strategy from Endive's HostModuleProcessor tests. We will also need to
establish a repeatable way to build .wasm component binaries that can be used in end-to-end tests for testing
interaction between the host functionality and wasm components that target the same WIT world. These full end-to-end
tests will likely need to reside in a separate module, such as a "bindgen-processor-tests" module.

The annotation processor should internally make use of the wasm-tools module (adding new APIs there as needed) to
convert WIT text into its binary format. The binary format uses the same structure as wasm components, so should be able
to be parsed with our current binary ComponentParser, using our WasmComponent AST as the basis for understanding the WIT
and generating the needed Java binding code from it.

The Bindgen annotation should be able to be used at the class level for simpler cases, or at the package level for more
complex bindings as would likely be needed in building WASI functionality.

We will start with the most basic use cases and build from there, using the examples from Wasmtime's bindgen! macro docs
as a guide. https://docs.wasmtime.dev/api/wasmtime/component/bindgen_examples/index.html

## How the Pipeline Works

WIT text is encoded to its binary form by wasm-tools, parsed back with the component parser, and read from the type
model. Each step below was checked against the running code rather than assumed, and the encoding details are the ones
worth having written down, because getting an index wrong is the failure this has hit most often.

### WIT text converts to binary through the wasm-tools module already present

Implemented as `WitParser.encode`, alongside the `parse` that was already there. Both drive
`wasm-tools component wit`, which writes text to stdout and binary to a file, so the requested form picks both the
command and where to read the result. Output is byte-identical to a native wasm-tools 1.245.1 on the same input.

wasm-tools tells WIT text and the binary encoding apart by content rather than by file name, so `parse` reads either.
That gives the encoder a round-trip test needing nothing outside this module, encoding WIT and reading the binary back
as the same package.

### ComponentParser reads the WIT binary unchanged

A WIT package encodes as a component whose exports name the package's top-level items.

- An `interface` becomes a `ComponentType` whose single export declaration names an `InstanceType` under the fully
  qualified interface id, such as `example:calc/types`. That instance type holds the interface's type definitions,
  each defined and then exported under its WIT name through an `eq` bound.
- A `world` becomes a `ComponentType` whose single export declaration names a nested `ComponentType` under the fully
  qualified world id, such as `example:calc/calculator`. That nested component type holds the world's `ImportDecl`s and
  `ExportDecl`s. A `use` in the world shows up as an imported instance followed by an alias and a type import for each
  used name.

Records, enums, variants, flags, function types, and their parameter names all survive the round trip into the
type model.

The encoded package is validated before it is read, through `ComponentValidate`, the same way every other parse in
this project is. The binary comes from wasm-tools moments earlier so nothing is expected to fail, but the parser
accepting whatever wasm-tools produces is a rule the project holds itself to, and it costs one already-loaded
wasm-tools run at compile time. Without it, a wasm-tools version encoding something this parser read differently would
surface as a puzzling generation failure rather than as a validation error.

Finding a world therefore takes four steps, which `WitBinaryTests` fixes in place.

1. The export section entry named for the world, whose sort is `TYPE`, gives a type index.
2. That type is the package item, a `ComponentType` wrapper.
3. The wrapper holds two declarations, a `ComponentType` for the world itself and an export declaration naming it under
   its fully qualified id. The export's `typeIdx` indexes the wrapper's own nested type space, where the declarations
   preceding it are numbered from zero.
4. The world's `ImportDecl`s and `ExportDecl`s name their function types by index into that same nested space.

Every nesting level restarts its type numbering, so resolution is index bookkeeping over each decl list rather than a
lookup in one flat space.

An instance type's space grows the same way. A resource is exported as a type rather than defined as one, and that
export takes an index just as a definition does, so a resource declaration has to be counted or every index after it
names the wrong type.

The package's own type index space needs the same care and is easy to get wrong, because it grows both by the types the
package defines and by the exports naming them. A package declaring one interface and one world numbers them 0 and 2,
not 0 and 1, so the section walk has to count the exports as well. A package with a single item hides this, since the
only index in play is zero either way.

### Component binaries for end-to-end tests are buildable from .wat

`wasm-tools component embed --world <w> <wit> <core.wasm>` followed by `wasm-tools component new` produces a component
binary from a hand-written core module. This is the repeatable fixture strategy for the end-to-end tests, and it keeps
the project free of a guest language toolchain.

One non-obvious detail costs time if it is not known. A core module satisfies a world's top-level import by importing it
from the core module name `$root`, not from the interface or world id. Imports that come through a WIT interface use the
interface id as the core module name.

`ComponentEmbed` and `ComponentNew` now wrap both commands. Each accepts its input as text or binary, so a fixture
written as `.wat` goes straight through both.

### The runtime could not be driven from outside its own package

Everything generated code has to emit was package-private. `ComponentInstance.Builder.instance()`, `addExport`,
`addImport` and `declareHostResourceType` all were, and `ComponentFunctionInstance.builder()`, though public, wants a
`ComponentInstance` and a `TypeResolver` that nothing outside could supply. A host function whose type names anything
beyond a primitive needs a type space against which its `typeIdx` references resolve.

That gap is closed by the facade in [What the Runtime Gained](#what-the-runtime-gained). It is recorded here because
anything else the generator comes to need will run into the same wall, and the answer is to widen the facade rather
than to reach past it.

### Existing Java bindings for component types

`PrimitiveHostTypeDescriptor` and its siblings already fix the conventions, unsigned widening included. Bindgen inherits
them rather than inventing its own.

| WIT | Java |
|---|---|
| `bool` | `Boolean` |
| `s8` | `Byte` |
| `u8`, `s16` | `Short` |
| `u16`, `s32` | `Integer` |
| `u32`, `s64`, `error-context` | `Long` |
| `u64` | `BigInteger`, since it exceeds a signed `long` |
| `f32` | `Float` |
| `f64` | `Double` |
| `char` | `CharValue` |
| `string` | `String` |
| `list<T>` | `List` |
| `record`, `tuple`, `flags` | `Map` |
| `variant`, `enum`, `option`, `result` | `VariantValue` |
| `enum` | a Java enum, matched label by label after kebab-casing |
| `own<R>`, `borrow<R>` | `ResourceValue` |

## Design Decisions

### The Bindgen annotation

Implemented. WIT is found the way the bindgen! macro finds it. A `wit` root directory holds the WIT, which in a
conventional Maven project means `src/main/resources/wit/`.

```java
@Bindgen(world = "hello-world")                           // reads wit/hello-world.wit
@Bindgen(world = "calculator", path = "wit/calc.wit")
@Bindgen(inline = "package ex:calc;\nworld calculator { ... }")
```

- `world` names the world to generate for and may be omitted when the package declares exactly one, in which case
  `path` or `inline` has to say where the WIT is.
- `path` is a resource path, defaulting to `wit/<world>.wit`.
- `inline` carries WIT text directly and is mutually exclusive with `path`.

`path` names a single file rather than a directory. The `Filer` fetches a named resource and cannot list a directory,
so taking a whole directory as one package needs a route to a real filesystem path, which multi-file package support
will have to bring with it.

Resources are looked up through the `Filer`, trying `CLASS_OUTPUT` and then `CLASS_PATH`. Maven's `process-resources`
phase populates `target/classes` before `compile`, so `CLASS_OUTPUT` is what resolves during a normal build.
`CLASS_PATH` covers two other cases, WIT arriving inside a dependency jar, which is how WASI p2 will consume the WASI
WIT, and the processor's own tests, where `compile-testing` runs an in-memory file manager that has no class output but
does honor a directory passed to `withClasspath`. Spike confirmed both behaviors.

The annotation moves to `SOURCE` retention, since nothing reads it at runtime, and its target widens to `{TYPE,
PACKAGE}` so that a `package-info.java` can carry the more elaborate bindings WASI will need.

`Bindgen` stays in the runtime module, which generated code depends on anyway. This module's dependency on `runtime`
therefore has to move from test scope to compile scope. If the processor's footprint ever becomes a concern, the
annotation can be split into its own artifact the way Endive separates `annotations` from `annotations/processor`.

A WIT package spread across a directory, with `deps/` and multiple files, needs the wasm-tools wrapper to mirror a tree
into ZeroFs rather than write the single `input.wit` it writes today.

### A public host-linking facade in the runtime

Implemented. Rather than widen the visibility of `ComponentInstance.Builder` and expose index-space mechanics as
supported API, the runtime module gained a public facade for building a host-provided instance.

```java
var host = HostInstance.builder(store)
        .withType(pointRecord)                              // yields a ValType naming it
        .addFunction("host-log", logFuncType, args -> ...)
        .addResource("conn", destructor)
        .build();

linker.instantiate(store, component, Map.of("host", host));
```

It has to cover everything `SpecTestImports` does, which is declaring value types into the instance type space and
handing back `ValType`s that name them, declaring host resource types with a Java destructor, adding functions from a
`FuncType` and a `ComponentFunctionCall`, and exporting functions, resource types, nested instances, and core modules.

A world's imports come in two shapes, so the facade has two entry points. An import declared as an instance, which is
what a WIT interface becomes, is built through `HostInstance`. An import declared as a bare function, which is what a
top-level function in a world becomes, is built through `HostFunction`, because such a function belongs to no instance
the importer can name. `HostFunction.of` builds and seals an instance to hold it, since a call still has to enter
something. A bare function's type may name only primitives, as the instance holding it declares no types.

`declareResource` declares the resource type together with its `own` and `borrow` in one step and hands back a
`HostResource` carrying all three. That folds away the index bookkeeping the previous hand-written code needed, at the
cost of two type slots for a resource that is only ever owned. Nothing indexes them and nothing pays for them.
`ResourceTypeInstance` stays package-private, so `HostResource.type()` hands out the public `ResourceTypeRef` that
`ResourceValue.owned` wants.

`SpecTestImports` is rewritten onto the facade and the sync ABI spec suite stays green, 1287 tests passing with the 8
pre-existing `@Disabled` gates untouched. `HostInstanceTests` covers the facade directly.

### Generated types are nominal, and cross the boundary through descriptors

Bindgen generates a Java class per WIT record, a Java enum per WIT enum, a tagged-union class per variant, an
`EnumSet`-backed wrapper per flags, and an `AutoCloseable` class per resource.

The `HostTypeDescriptor` family is the mechanism for binding those to component types, so that mismatches are caught
ahead of any call rather than during one. Generated code that invokes a component goes through
`ComponentFunction.typed(resultDescriptor, paramDescriptors...)`, which checks the descriptors against the component
type when the function is cast and checks each argument's class when it is called.

`EnumHostTypeDescriptor` already binds a Java enum to a WIT `enum` or `flags` and already does the kebab-case label
matching, so generated enums use it directly. The rest of the family names ABI representations rather than user classes,
`RecordHostTypeDescriptor` binding `Map` and `VariantHostTypeDescriptor` binding `VariantValue`, so a generated `Point`
is not something they can name. `HostTypeDescriptor` therefore grows so that a descriptor can both match a generated
class and carry the mapping to and from the ABI representation it denotes.

The canonical-abi module is not touched. Lifting and lowering keep working on the representations they already handle,
which is what keeps the passing sync ABI spec tests untouched. The cost is one allocation per aggregate per call, which
is the right trade for now and can be revisited once there is something to measure.

### The Java shape of each WIT type

A WIT type has to arrive as something a Java embedder would have written, which for several of them is not what the
Canonical ABI carries. These are the shapes the generator targets.

| WIT | Java | Carried as |
|---|---|---|
| `record` | a class with final fields, a constructor, getters, `equals`, `hashCode` and `toString` | `Map` |
| `flags` | an `EnumSet` backed wrapper | `Map` of booleans |
| `tuple<A, B>` | `Tuple2<A, B>` through `TupleN`, which the runtime module provides | `Map` keyed `"0"`, `"1"` |
| `variant` | an abstract base class with a nested final class per case | `VariantValue` |
| `enum` | a Java enum | `VariantValue` |
| `option<T>` | a nullable `T` | `VariantValue` |
| `result<T, E>` | an unchecked exception carrying the error payload | `VariantValue` |

Three of those need a reason recorded.

A nullable `option` reads as Java rather than as a translation of WIT, at the cost of `option<option<T>>`, where
`some(none)` and `none` both become null. That case is refused by name rather than encoded wrongly. An option payload
always lowers to its own `VariantValue.of("none", null)`, never to a bare Java null, or a variant case carrying `none`
would be indistinguishable from a case carrying nothing.

A `result` becomes control flow rather than a value, so it is meaningful only as a function's direct result. One in a
record field, a list element or a variant case is refused. The generated exception is unchecked, so a signature stays
free of `throws`. A world's bindings catch only that exception when lowering an import, because catching
`RuntimeException` would deliver a genuine bug in embedder code to the guest as a well formed error.

An exception is named after the error payload's type when that type has a name, so `parse-error` gives
`ParseErrorException`. An anonymous result type is shared by every function with the same signature, so it cannot be
named after a function, and it falls back to the interface and the type's index in the scope, giving
`RunningResult6Exception`. Two results sharing an error payload share the exception named after it, since the exception
carries the payload and nothing else. A `variant` becomes case subclasses rather than a visitor because an `instanceof`
test allocates nothing, and because the same generated classes become `sealed` once the Java baseline allows it.

### Type metadata is generated as builder code

Generated code reconstructs the `ValType` and `FuncType` graph with the existing builders. Value types are written
inline inside the function type that names them, and one `FuncType` constant is generated per imported function. Only
imports need one, because an export's type is read off the instance exporting it.

A separate holder class per world, and sharing structurally identical types across it, is deferred until a world spans
more than one generated class. Neither buys anything while a world generates a single file.

```java
final class Calculator_Types {
    static final ValType U32 = ValType.builder().withPrimValType(PrimValType.U32).build();
    static final FuncType HOST_LOG =
            FuncType.builder()
                    .addParam(LabelValType.builder().withLabel("msg").withValType(STRING).build())
                    .build();
}
```

Everything stays visible to the approved files and nothing is read from the classpath at runtime. The holder also
carries the generated descriptor constants. This is verbose for something the size of WASI, which is a code-size concern
to revisit rather than a correctness one.

### Generated structure

The world becomes one class, and everything else lands in a Java package mirroring the WIT id, the way `bindgen!`
lays out Rust modules.

| WIT | Java |
|---|---|
| world `hello-world` | `<base>.HelloWorld` |
| the world's own imports | `<base>.HelloWorld.Imports` |
| import `example:imported-resources/logging` | `<base>.example.importedresources.logging.Host` |
| import written inline in the world | `<base>.<name>.Host` |
| export `example:world-exports/units` | `<base>.exports.example.worldexports.units.Guest` |
| export written inline in the world | `<base>.exports.<name>.Guest` |
| a type the interface declares | that same package |

An interface written inline has no id to contribute, so it sits directly under the base. `exports` is a deliberate
split, and it is what lets a world import and export one name at once.

The point of it is the use site. A type reached as `ImportSomeResources.Logging.Level` cannot be imported and has to be
written whole every time. As `Level` in a package of its own it is imported once.

Unversioned package segments are lowercase with the words run together, since Google's Java style allows no
underscores, which is why `imported-resources` becomes `importedresources`. Real WASI ids are mostly single words, so
the run-together spelling rarely shows.

Versioned interface ids add `_v` and the lowercase hexadecimal UTF-8 bytes of the version to their Java package
segment and member name. For example, `streams@0.2.0` becomes `streams_v302e322e30`. Encoding the entire version keeps
`1.2.3-a.b` distinct from `1.2.3-a-b`, and `1.2.3-a-b` distinct from `1.2.3-ab`. Unversioned interfaces keep their
existing names. Component Model imports and exports still use the original WIT id.

Two things follow from generating more than one file.

A world's interfaces are shared rather than copied. The first world to need an interface id generates it and a later
one reuses it, so two worlds importing `wasi:io/streams` get one Java type and one implementation serves both. Two
worlds that would generate the same file differently is a real conflict, and is reported as one.

The world class names types from other packages, and Java has no way to write a qualified name that a field cannot
shadow. A world exporting `run` inside a package under `run.endive` is enough to break it. So a field whose name
matches the first segment of a qualified reference is renamed rather than the reference. Only the world class makes
such references, since every other unit names its own package.

### Naming

WIT reserves fewer words than Java does, so a name like `new`, `class` or `final` is a WIT name but not a Java one, and
a WIT name may also escape a WIT keyword with `%`. Any identifier Java reserves gains a trailing underscore. Only
member names need it, since a type name is capitalised and an enum constant upper-cased, and neither can collide.

The WIT name is what the linker matches on, so only the Java identifier is escaped. `import new: func()` still registers
under `"new"`.

| WIT | Java |
|---|---|
| world `calculator` | class `Calculator`, imports interface `Calculator.Imports` |
| interface `example:calc/types` | nested class `Calculator.Types` |
| function `host-log` | method `hostLog` |
| type `point` | class `Point` |
| record field `first-name` | field and accessor `firstName` |
| enum case `red` | constant `RED` |
| variant case `not-found` | case class `NotFound` |

### Generated shape

Mirroring bindgen!, a world produces one class carrying an interface the embedder implements for the world's imports
and typed accessors for its exports. Everything an interface declares lands in a package of its own, as
[Generated structure](#generated-structure) describes.

```java
public final class Calculator {

    public interface Imports {
        void hostLog(String msg);
    }

    public static Calculator instantiate(ComponentStore store, WasmComponent component, Imports imports);

    public ComponentInstance instance();

    public Long add(Long a, Long b);
}
```

## What Is Built

`BindgenProcessor` reads the annotation, loads the WIT, and hands it to `WorldReader`, which encodes it, parses it, and
resolves the named world into a `WitWorld` of imported and exported functions. `WorldGenerator` turns that into one
`GeneratedUnit` per file, with `WitTypes` mapping component value types onto Java ones and `Names` doing the
kebab-case conversion.

### Generation builds JavaParser nodes rather than source strings

Every generated construct is assembled as a typed AST node. Nothing is parsed back from a string, so a mistake is a
compile error in the generator rather than a parse failure at annotation processing time, and a debugger stepping
through generation shows the tree being built.

Seven types carry it, and knowing which one owns a decision is most of finding your way around.

| Type | Owns |
|---|---|
| `QualifiedTypes` | The qualified names of every runtime and JDK type generated code refers to |
| `AstBuilders` | Factories over JavaParser nodes, terse enough that a node reads like the Java it stands for |
| `GeneratedUnit` | One source file, which imports a type as a consequence of naming it |
| `WitTypes` | A component value type as a Java type, a rebuilt `ValType`, or a descriptor |
| `FunctionBindings` | The pieces a WIT function turns into, whichever way the call runs |
| `HostWiring` | The statements building a `HostInstance` for an imported interface |
| `InterfaceGenerator` | The Java package mirroring one interface, so `WorldGenerator` holds only the world class |

Two of those are worth knowing the reason for.

`GeneratedUnit.use` returns a type by its simple name and records the import, so the import list follows from what the
generated code names. The previous arrangement decided imports separately, by asking whether any function mentioned a
kind, which drifted from what was actually written. Rewriting the generator onto `use` changed the checked-in expected
sources by exactly one line, an unused `java.math.BigInteger` the old rule added to a world whose only `u64` was in
another file. A `GeneratedUnit` is also what the processor writes, taking its file name from the type it declares
rather than from a name passed alongside, so the two cannot disagree.

`AstBuilders` parenthesises a scope that binds less tightly than the expression reaching into it, because
JavaParser's printer writes a tree structurally rather than by precedence. Without that,
`((VariantValue) value).label()` would print as `(VariantValue) value.label()`.

`WorldReader` refuses what it cannot yet read rather than guessing. An alias that introduces a type would shift every
index after it, so a world using types from an interface is rejected by name instead of being mis-numbered in silence.

The Hello World world generates this, verified against a checked-in expected source and run end to end in
`bindgen-processor-tests` against a component built with `component embed` and `component new`. Instantiating it links,
calling `greet` enters the guest, and the guest's call back into `name` returns the host's string through the ABI.

```java
public final class HelloWorld {

    public interface Imports {
        String name();
    }

    public static HelloWorld instantiate(ComponentStore store, WasmComponent component, Imports imports);

    public ComponentInstance instance();

    public void greet();
}
```

Imports are wired through `HostFunction.of` with a generated `FuncType`, and each export is narrowed once in the
constructor with `ComponentFunction.typed`, so a descriptor that the component's type could never satisfy fails at
instantiation rather than at the first call.

A world may also import an interface, which the encoding carries as an instance rather than a bare function. Each one
becomes a `Host` interface in a package of its own plus an accessor on `Imports`, so that `instantiate` stays a three
argument call however many interfaces a world imports, and one embedder object can implement the world and its
interfaces together.

```java
// <base>.MyWorld
public interface Imports {
    String greet();
    void log(String msg);

    run.example.mycustomhost.Host myCustomHost();
}

// <base>.mycustomhost.Host
public interface Host {
    void tick();
}
```

The interface is resolved from `Imports` once at instantiation rather than per call, and wired in with `HostInstance`.
Function type constants are prefixed by the interface they belong to, so two interfaces may each declare a `tick`.

A resource a world exports is implemented by the guest rather than by the host, so the handle runs the other way. It
becomes an `AutoCloseable` wrapper holding the handle the constructor returned.

```java
public static final class Logger implements AutoCloseable {
    public Level getMaxLevel();
    public void log(Level level, String msg);

    @Override public void close();
}

public Logger logger(Level maxLevel);
```

Lifting an `own` out of a component takes the handle out of that component's table, so what the embedder ends up
holding is the last thing naming the resource and nothing will destroy it on the embedder's behalf. `close` runs the
guest's destructor through `GuestResource.drop`, and doing it twice is harmless so the wrapper suits
try-with-resources.

A descriptor has to describe what actually crosses rather than what the embedder holds. An enum crosses as the variant
it despecializes to, because the generated code converts before it calls, so the descriptor is
`VariantHostTypeDescriptor` and not `EnumHostTypeDescriptor`. The latter sanctions a Java enum that lowering would then
refuse, which is worth knowing before reaching for it.

An interface a world exports is an instance too, and becomes a wrapper class reached through an accessor, mirroring
`bindgen!`'s `bindings.demo().call_run()`.

```java
public static final class Demo {
    public void run();
}

public Demo demo();
```

Each of its functions is narrowed once when the wrapper is built, the same as a world's own exports. Reaching a nested
exported instance needed `ComponentInstance.exportedInstance`, since the only public accessor before it insisted the
export was a function.

Whether an interface is written inline in the world or named by its package makes no structural difference. Both arrive
as an instance, differing only in whether the name carries a package qualification, and the Java name comes from the
last segment either way. That holds on the export side too, so a world exporting a function of its own, an interface
written inline, and an interface named by its id needs nothing beyond what one exported interface already needed.

An imported and an exported interface of one name no longer collide, since exports sit under `exports`. Two worlds of
the same name in one Java package still would, because both want the same world class. Two of the bindgen! examples do
declare a `hello-world` each, which is why the end-to-end tests give every example a package of its own.

An interface may declare a resource the host implements. The Canonical ABI names a resource's functions rather than
nesting them, so `[constructor]file` and `[method]file.get-name` are split apart again and become a nested Java
interface with a factory on the interface that owns it.

```java
// <base>.example.resources.types.Host
public interface Host {
    File file(String name);
}

// <base>.example.resources.types.File
public interface File {
    String getName();

    default void drop() {}
}
```

A method's borrowed receiver is what Java carries as `this`, so it is dropped from the signature. A handle carries an
integer rather than an object, so the bindings keep a `HostResourceTable` per resource type mapping one to the other,
and the generated destructor hands the object to `drop` before forgetting it. `drop` is a default method, so observing
a drop is optional rather than forced on every embedder.

A resource may also carry `static` functions, which reach it without a receiver, so there is no borrowed first
parameter to drop. What a static hands back is what decides its shape, and that is read off its declared result rather
than off a flag. One returning an `own` handle to its own resource mints a resource exactly as a constructor does, and
one returning an ordinary value is wired like any other function.

```java
// <base>.example.resources.types.Host
public interface Host {
    File file(String name);

    File fileOpen(String name);
    Long fileCount();
}
```

A static has no handle to hold, so on the export side it sits on the `Guest` rather than on the resource wrapper. It is
not a Java `static` either, since the `Host` side has to be overridable and the `Guest` side reads the narrowed function
off the instance. Both sides name it after the resource owning it, so two resources may each declare an `open`.

The `Guest` field holding a narrowed resource function is named the same way, which is why a constructor yields
`fileFile`. Two functions that would otherwise share one field name are numbered apart, since `[constructor]file` and
`[static]file.file` both want the first spelling.

An interface declaring a resource is built through a local rather than in one chained expression, because a resource
has to be declared before anything names it. That is also why its constructor and method types are built inside
`instantiate` rather than held as constants. `own` and `borrow` name the resource by index, and the index is only known
once `declareResource` has run.

An interface may also declare a `list`, an `enum` or a `flags`. A list is carried by `java.util.List` of whatever
carries its element, so `list<u8>` arrives as `List<Short>`. An enum becomes a Java enum carrying the label the ABI
knows it by, and converts at the boundary, because the ABI despecializes an enum to a variant and lifts it as a
`VariantValue` rather than as anything nominal.

A `flags` becomes a final class holding an `EnumSet` of a nested `Flag` enum, one constant per label, and is described
to the runtime by `RecordHostTypeDescriptor`, since the ABI carries it as a label to boolean map. `toComponent` writes
every label, because `CanonicalAbi.packFlagsIntoInt` reads each one by name, and `fromComponent` reads each back with
`Boolean.TRUE.equals`, so a label the map leaves out is false rather than a bug the way a missing record field is. A
flags type whose Java name would be `Flag` is refused, because a nested type may not be named after the class holding
it. More than 32 flags never reaches the generator, since wasm-tools rejects it while encoding the WIT.

An `option<T>` is a nullable `T`, so it generates no Java type of its own. It is structural rather than nominal and
has no WIT name, so it is reached only through the `WitScope` and never looked up by name. It converts at the boundary in both directions, lowering to
`VariantValue.of("some", payload)` or `VariantValue.of("none", null)` and lifting a `none` back to Java `null`.
Lowering never yields a bare `null`, both because `ComponentFunctionInstance` reads `arg.getClass()` without a null
guard and because a `none` nested in another type would otherwise be indistinguishable from a case carrying no payload
at all. `option<option<T>>` is refused, by resolving the payload through the scope rather than reading the WIT text, so
that one reached through a named alias is refused as well.

Conversion is written as an expression transform that evaluates its input exactly once and recurses into whatever a
compound type holds, which is what lets `option` nest. A container is therefore responsible for routing its own
payloads through `WitTypes.toComponent` and `WitTypes.fromComponent` rather than passing them through. A list does so
element by element, so `list<option<u32>>` arrives as a `List<Long>` whose entries may be null.

Both have to be declared into the host instance before a function type can name them, the same as a resource, which is
why every imported interface is built through a local rather than in one chained expression. A `WitScope` carries an
interface's type index space together with the names its exports give those types, since only the export says what a
type is called and a Java type has to be called something.

A `tuple` is structural rather than nominal, so it has no WIT name and generates no source of its own. It arrives as
one of the runtime's `Tuple2` through `Tuple8` classes and converts at the boundary, because the ABI despecializes a
tuple to a record whose fields are labelled by position and carries it as a `java.util.Map`. Lifting one names each
element by its class, as `Tuple2.fromComponent(value, String.class, Long.class)`, which is what gives the conversion
the tuple type a caller expects without an unchecked cast. An element the conversion cannot name that way is refused,
which covers an element that converts on its own such as an `enum`, and one carrying a type argument such as a `list`.
A tuple wider than the runtime carries is refused too.

A `variant` becomes an abstract base class with one nested final class per case, as
[The Java shape of each WIT type](#the-java-shape-of-each-wit-type) settled. A case carrying a payload holds it in a
field reached through `value()`, since a WIT case payload is anonymous, and one carrying none has no field at all.
Which case a value is comes from its Java class rather than from whether a payload is present, so a case carrying
`none` stays distinct from a case carrying nothing. `fromComponent` switches on the label and refuses an unknown one
the way an enum's does.

Two limits follow from generating a nested class per case. A case named after the variant it belongs to is refused,
because Java forbids a nested class sharing the simple name of a class enclosing it. A payload of a kind the generator
cannot map is refused where the variant is declared rather than where a function names it, since the case class needs
a Java type for its field either way.

An interface may also declare a `record`, which becomes a final class with final fields, a constructor taking them in
declaration order, accessors named after the fields, and `equals`, `hashCode` and `toString`. The ABI carries a record
as a map keyed by field label, so it converts at the boundary and `toComponent` writes every field, because
`CanonicalAbi.storeRecord` reads each by label and a label the map leaves out is stored as a null field rather than
reported. A field naming another record converts through that record's own pair, since the encoding orders a
definition before whatever uses it. Three things a record cannot yet carry are refused by name: a resource handle,
whose type is declared into the instance only after its value types, and any field of a kind the generator does not yet read.

An interface may also declare a `result`, which becomes control flow rather than a value. The ok payload is the Java
return value and the error case is a generated unchecked exception carrying the error payload, so `parse: func(text:
string) -> result<u32, parse-error>` binds as `Long parse(String text)` throwing `ParseErrorException`. All four shapes
are bound, and two of them, `result` and `result<_, E>`, return nothing at all even though `FuncType.hasResult()` holds
for every one of them.

Which way the call runs decides where the exception is built and where it is caught. Calling into the component reads
the label off the `VariantValue` that comes back and either returns the ok payload or throws. Satisfying an import wraps
the embedder's call in a `try` that catches only the generated exception and turns it back into the `error` case.
Catching every `RuntimeException` there would deliver a genuine bug in embedder code to the guest as a well formed
error, which is why the catch is narrow.

A world's `use`, an interface that uses types from elsewhere, a compound type on a world's bare function import, and
a `result` reached as anything but a function's own result are each rejected with a message naming what is
unsupported. The bare function import is a limit of `HostFunction`, which builds an instance with no type space,
leaving an index nothing to resolve.

## Fidelity to the bindgen! examples

The WIT under `src/test/resources/wit` in `bindgen-processor` is the bindgen! example world for that stage, verbatim.
That is what the approved files are generated from, so a difference from the example is visible rather than assumed.

All seven of the non-async example worlds are present. A world covering a WIT feature no example declares is written
for the purpose and named after it, which is where `record-types`, `variant-types` and `static-functions` come from,
and each such fixture says so at the top.

All seven of the non-async example worlds are present. `result-types` is not one of them, because no bindgen! example
uses a `result`, so that world is written for these tests and its fixtures say so at the top.

The end-to-end fixtures use the same WIT, with one exception that has to be stated wherever it appears. A world that
imports without exporting cannot be driven, since nothing enters the guest, so `with-imports`,
`import-some-resources` and `export-some-resources` each carry an added export the test calls. Nothing else differs,
and each fixture says so at the top.

Earlier fixtures were adapted rather than copied, because both `sha256: func(bytes: list<u8>) -> string` and the
`logging` interface's `enum level` needed types the generator could not yet read. Adapting them hid that, which is the
thing to avoid. A WIT type that is not supported belongs in the unsupported list above, not worked around in a fixture.

## Module Layout

- `bindgen-processor` holds the processor and its tests. Sources are built as JavaParser AST nodes and written
  through the `Filer`. WIT for those tests lives in `src/test/resources/wit/` and reaches the processor through
  `withClasspath`. `ApprovalTest` approves everything one world generates as a single file, in the style the Endive
  compiler uses for bytecode, while `BindgenProcessorTest` holds the focused assertions and the refusals, which is
  where a rule about conversion or naming belongs rather than buried in a whole generated file.
- `bindgen-processor-tests` holds the end-to-end tests. A `.wat` fixture and a `.wit` file are turned into a component
  by `ComponentEmbed` and `ComponentNew`, and the same WIT generates the bindings that call it, so nothing is written
  by hand twice.

  The processor is also declared as an ordinary `provided` dependency, because the reactor does not treat
  `annotationProcessorPaths` as a dependency edge. Without that declaration a build of this module alone runs whatever
  processor was last installed, and these tests can pass against a stale one.

  The processor runs over this module's own test sources, named through the compiler plugin's
  `annotationProcessorPaths` rather than discovered on the class path. Naming it keeps processing explicit, which
  matters because the build fails on warnings and implicit discovery warns. Maven copies test resources before
  compiling test sources, so the WIT is on the class output by the time the processor looks for it.

  The fixture traps unless the host's string reaches guest memory intact, and a test feeding it a different string
  asserts that trap. Without that second test the first would pass on bindings whose value never arrived.

  Components are built and parsed through `Components`, which wires `TestValidator` into the parser rather than
  validating in a call of its own. Every parse in this project reaches wasm-tools the same way, so there is one place
  to look when that has to change.

## Staging

Following the order of the bindgen! examples. Every stage is complete and checked end to end against the example world,
verbatim, except where [Fidelity](#fidelity-to-the-bindgen-examples) records otherwise.

0. ~~A world with one imported function and one exported function over primitives and strings.~~
1. ~~World imports, both top-level functions and an inline interface.~~
2. ~~World exports, including an exported interface.~~ A named interface import came with it, since it is the same
   shape as an inline one.
3. ~~Imported interfaces.~~ Came with stage 2, and has a fixture of its own confirming it.
4. ~~Imported resources.~~
5. ~~All kinds of world export.~~
6. ~~Exported resources.~~

The async example stays out of scope, because async is unimplemented across the whole project and the runtime rejects
it.

## What the Runtime Gained

Generating bindings needed public API the runtime did not have. All of it is in `run.endive.cm.runtime`.

| Type | For |
|---|---|
| `HostInstance` | Building an instance the embedder supplies, declaring types and resources into it |
| `HostFunction` | An import declared as a bare function, which belongs to no instance |
| `HostResource` | A resource type the embedder implements, with its `own` and `borrow` |
| `HostResourceTable` | Mapping a resource representation to the Java object it stands for |
| `GuestResource` | Dropping an owned handle to a resource the guest implements |
| `ComponentInstance.exportedInstance` | Reaching an exported interface, which is an instance rather than a function |

`SpecTestImports` in the runtime module is written against `HostInstance`, so the sync ABI spec suite is what keeps
that facade honest.

## Remaining Work

Nothing here is started. Each item says what it is and what makes it awkward, so it can be picked up cold.

### WIT the generator refuses

`WorldReader` and `WitTypes` reject what they cannot read, by name, rather than guessing. Everything below fails that
way today, which means adding one is a matter of finding its rejection and replacing it.

- **`record`, `tuple`, `flags`.** The largest gap. A record despecializes to something the ABI carries as a
  `java.util.Map`, so a generated class needs conversion at the boundary the way an enum already does. This is the
  remaining half of [Generated types are nominal](#generated-types-are-nominal-and-cross-the-boundary-through-descriptors).
- **`variant`, `option`.** Both carried as `VariantValue`, so they follow the enum pattern, but a variant case has a
  payload and `option` wants an idiomatic Java shape rather than a literal case class.
- **A `result` on a function a world declares in its own right.** The exception generated for one lives in the Java
  package of the interface declaring the result, and a world declares no such package. Moving a result into an
  interface is enough, and the refusal says so.
- **A resource's `static` functions.** `[static]file.open` is recognised and rejected in
  `WorldReader.ResourceFunctions.add`. It maps to a static Java method, so the wiring is simpler than a method's.
- **A world's `use`, and an interface using types from elsewhere.** Both are aliases that grow the type index space,
  which `WorldReader.track` refuses rather than mis-number. Supporting them means resolving an alias to the interface
  that declared the type and referring to the Java type already generated for it.
- **A compound type on a world's bare function import.** `HostFunction` builds an instance with no type space,
  leaving an index nothing to resolve. Either `HostFunction` grows type declarations or such an import is built
  through `HostInstance` like an interface.

### Multi-file WIT packages

`WitParser.encode` writes one `input.wit` into ZeroFs, so a package spread across a directory with `deps/` cannot be
read. `@Bindgen(path = ...)` names a single file for the same reason: the `Filer` fetches a named resource and cannot
list a directory. Supporting a directory needs a route to a real filesystem path, and both halves change together.

### The interface sharing policy is untested

`BindgenProcessor.write` generates an interface once per package and reports a conflict when two worlds would generate
the same file differently. Neither branch has a test. It needs a fixture with two `@Bindgen` annotations in one package
importing the same interface, and one where they disagree.

### The unchecked suppression covers a whole file

A value arrives from the ABI as an `Object`, so the generated cast naming what is inside it cannot be checked.
`GeneratedUnit.markUnchecked` records that a file writes such a cast and annotates the type it declares, which silences
unchecked warnings for everything else in that file as well. Narrowing it to the member holding the cast needs the
enclosing member threaded through expression construction, since a cast is built deep inside `WitTypes` while the
member is built by the caller.

### A variant case may still shadow an imported type

Generated code introduces names of its own, and a WIT name is free to be any of them. A record's locals, a
conversion's lambda parameters and a resource function's field all give way to the WIT names already in scope, and a
variant case is refused when it is named after its own variant or after a type the interface declares. What is not
checked is a case named after a type the generated file imports, such as `objects`, whose nested class would shadow
`java.util.Objects` inside the variant. It surfaces as a javac error rather than a bindgen one, and closing it means
knowing what the unit will import before the case is written.

### A Maven plugin

`docs/phases/04-wit-bindgen.md` calls for a `bindgen-maven-plugin` running at `generate-sources`. The annotation
processor covers the same ground for a Java project and nothing has needed the plugin, so it is unbuilt rather than
abandoned.

### ComponentLink

The last unwrapped wasm-tools component command, tracked in `docs/phases/02-wasm-tools.md`. Nothing in bindgen needs it.

## Working On This

`mvn clean install` from the repository root builds and tests everything. The suites that matter here are
`bindgen-processor` for generation and `bindgen-processor-tests` for the end-to-end path, and the runtime's sync ABI
spec suite for anything touching the facade.

**Changing the generator changes the approved files.** Re-run the suite with
`APPROVAL_TESTS_USE_REPORTER=org.approvaltests.reporters.AutoApproveReporter` and read the diff. An approved file is
the API an embedder writes against, so that diff is the review, and because it carries every source a world generates
under its qualified name, the package layout is part of what is approved.

An approved file shows that a change happened, not that it is right. A rule the generator has to keep, such as an
`option` member lowering to its own variant or two nested conversions not sharing a lambda parameter, belongs in a
focused test in `BindgenProcessorTest`, because a whole generated file is where such a rule goes unnoticed.

Two traps are worth knowing before losing an hour to either.

- The processor is used by `bindgen-processor-tests` through `annotationProcessorPaths`, which the reactor does not
  treat as a dependency edge. A `provided` dependency is declared alongside it for that reason. Without it,
  `-pl ... -am` runs whatever processor was last installed and the tests pass against a stale one.
- Driving the processor by hand with `javac` needs `mvn -pl bindgen-processor install` first, for the same reason.

An end-to-end fixture is a `.wat` implementing the world, turned into a component by `ComponentEmbed` and
`ComponentNew`. Three things about writing one are easy to get wrong.

- A world's top-level import binds to the core module name `$root`. An imported interface binds to the name the world
  gives it.
- A function inside an exported interface is a core export named `<interface>#<func>`, using the whole id when the
  interface is exported by one.
- An exported function returning a string hands back a pointer to the (pointer, length) pair. An imported one is given
  a return area for writing. The two directions are opposite.

A fixture should trap unless it is handed what the test says it was handed, with a companion test asserting that trap.
Several tests here would otherwise pass on bindings whose values never arrived.
