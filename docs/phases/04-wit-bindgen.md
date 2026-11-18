# Phase 4: WIT Bindgen (Code Generation)

**Status**: Complete for the bindgen! example worlds
**Depends on**: Phase 0 (Type Model), Phase 1 (Parser), Phase 2 (wasm-tools),
Phase 3 (Canonical ABI)

Delivered as an annotation processor rather than a standalone library, mirroring
Wasmtime's `bindgen!` macro. `@Bindgen` on a class or package generates the
bindings for a WIT world during compilation.

All seven non-async `bindgen!` example worlds are checked in as fixtures, and
each runs end to end against a component built from `.wat`. What is not yet
supported is listed in the design document, records and variants chief among
them.

**`bindgen-processor/design.md` is the working document for this phase.** It
carries the design decisions and their reasons, the encoding details that are
easy to get wrong, the remaining work, and how to regenerate the checked-in
expected sources. Read it before this file, which records only the original
plan and how the result diverged from it.

## How It Diverged From This Plan

- Two modules rather than one, `bindgen-processor` and `bindgen-processor-tests`,
  the second existing so that generated bindings are compiled and run as ordinary
  classes.
- No `bindgen-maven-plugin`. The annotation processor covers the same ground for
  a Java project and nothing has needed the plugin.
- The WIT-to-type-model bridge uses `wasm-tools component wit --wasm` through
  `WitParser.encode`, rather than `ComponentNew` and `ComponentEmbed`. Those two
  were still needed, for building the end-to-end test fixtures.
- Generated types are nominal but convert at the boundary rather than being lifted
  and lowered directly, so the Canonical ABI module is untouched.

## Goal

Generate type-safe Java bindings from WIT definitions. Given a WIT world,
produce Java interfaces, classes, enums, and glue code that applications use
to interact with components.

## Module

`bindgen` (`io.roastedroot:typewire-bindgen`) + `bindgen-maven-plugin`

**Package**: `io.roastedroot.typewire.bindgen`

## Deliverables

### WIT-to-type-model bridge

Use `ComponentNew` + `ComponentEmbed` (Phase 2) to produce a component binary
from WIT, then `ComponentParser` (Phase 1) to extract type information. This
ensures spec compliance by going through the official toolchain.

### JavaParser-based code generators

Using the JavaParser library for AST-based code generation:

| WIT construct | Generated Java |
|---------------|---------------|
| `record` | Class with final fields, constructor, getters, `equals`/`hashCode`/`toString` |
| `enum` | Java enum |
| `variant` | Tagged union class (Java 11 compatible) |
| `flags` | `EnumSet`-style wrapper |
| `resource` | `AutoCloseable` class with methods + handle table |
| `interface` | Java interface with methods using mapped types |

### Glue code generation

For each `canon lift` / `canon lower`:
- Generate a wrapper function that calls `CanonicalLower` to lower arguments,
  invokes the core function, and calls `CanonicalLift` to lift results
- For imports: generate a host function adapter
- For exports: generate a typed wrapper around the exported component function

### Maven plugin

`io.roastedroot:typewire-bindgen-maven-plugin` that runs during `generate-sources`.
Takes WIT file(s) as input, produces Java sources into
`target/generated-sources/wit`.

## What's Reused

- **JavaParser library** for AST construction and pretty-printing
- **Endive's `test-gen-plugin`** as a pattern for Maven plugin structure
- Phase 0 types, Phase 1 parser, Phase 2 wasm-tools, Phase 3 Canonical ABI

## Testing

- Generate bindings for a suite of WIT files covering all type forms
  (primitives, records, variants, lists, options, results, flags, enums,
  resources)
- Compile the generated code (must be valid Java)
- Integration tests: generate bindings, compile, call a component, verify
  correct behavior
- Regression tests against the WIT test suite

## Exit Criteria

Given any valid WIT world, the bindgen produces compilable Java code. The
generated code correctly wraps component imports and exports using the
Canonical ABI.
