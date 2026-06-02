# Phase 4: WIT Bindgen (Code Generation)

**Status**: Not started
**Depends on**: Phase 0 (Type Model), Phase 1 (Parser), Phase 2 (wasm-tools),
Phase 3 (Canonical ABI)

## Goal

Generate type-safe Java bindings from WIT definitions. Given a WIT world,
produce Java interfaces, classes, enums, and glue code that applications use
to interact with components.

## Module

`bindgen` (`run.endive.cm:bindgen`) + `bindgen-maven-plugin`

**Package**: `run.endive.cm.bindgen`

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

`run.endive.cm:bindgen-maven-plugin` that runs during `generate-sources`.
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
