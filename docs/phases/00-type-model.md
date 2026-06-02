# Phase 0: Type Model Foundation

**Status**: Not started

## Goal

Define the Java types that represent all Component Model concepts.
Everything else parses into, generates from, and instantiates with these types.

## Module

`types` (`run.endive.cm:types`)

**Package**: `run.endive.cm.types`

## Deliverables

### Component value types

Primitives: `bool`, `s8`, `u8`, `s16`, `u16`, `s32`, `u32`, `s64`, `u64`,
`f32`, `f64`, `char`, `string`.

Compound: `record`, `variant`, `list`, `tuple`, `flags`, `enum`, `option`,
`result`.

Handle types: `own<T>`, `borrow<T>` (resource handles).

Each type follows the immutable-with-builder convention seen in Endive's
`ValType` and `FunctionType`.

### Component function type

Named parameters and a typed result -- distinct from core `FunctionType`.
A component function type is `(params: list<named-type>) -> result-type`.

### Component section ID constants

Analogous to `SectionId.java` in core Wasm:

| ID | Name |
|----|------|
| 0 | Custom |
| 1 | CoreModule |
| 2 | CoreInstance |
| 3 | CoreType |
| 4 | Component (nested) |
| 5 | Instance |
| 6 | Alias |
| 7 | Type |
| 8 | Canon |
| 9 | Start |
| 10 | Import |
| 11 | Export |

### Section classes

One per section ID, each with a builder, extending a `ComponentSection` base
class (analogous to `Section` in core Wasm).

### Index space model

Separate growing lists for each namespace: core modules, core instances,
core types, core functions, core memories, core tables, core globals,
components, component instances, component types, component functions, values.

### Canonical definitions

Types representing `canon lift`, `canon lower`, `resource.new`,
`resource.drop`, `resource.rep`.

### Import/export definitions

Component-level import and export types carrying a name, an optional URL,
and an extern descriptor.

### Alias definitions

The three alias forms: `outer`, `export`, `core export`.

## What's Reused

- `Section` base class pattern from Endive (`abstract class Section` with
  `sectionId()`)
- `Builder` convention (builder accumulates, `build()` returns immutable)
- Immutable collections approach (`List.copyOf`, `Map.copyOf`)
- Core `WasmModule`/`ValType`/`FunctionType` referenced as-is for embedded
  core modules

## Testing

Unit tests constructing every type via builders. Verify:
- Immutability (collections are unmodifiable)
- `equals()` / `hashCode()` contracts
- `toString()` produces readable output

Pure data model -- no binary parsing in this phase.

## Exit Criteria

All Component Model types can be constructed programmatically and are
structurally correct. The type model can represent any valid Component Model
binary's decoded content.
