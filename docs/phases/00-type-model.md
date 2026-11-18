# Phase 0: Type Model Foundation

**Status**: Complete

Every deliverable below is implemented apart from the value index space, values
being a gated feature that nothing downstream reaches yet. The model also carries
the async type forms (`stream`, `future`, `error-context`) and the full set of
canonical definitions, which are parsed and represented but not yet executed by
the runtime -- see Phase 5.

Three additions were made beyond the original plan, all in service of resolving
a type against the index space it was written in rather than the one it is used
in:

- `ResolvedType` and `ResolvedFuncType` -- a type with every index already
  followed, so it no longer refers to any index space
- `TypeSpace` -- what an index resolves against
- `ResourceTypeId` -- an opaque handle on a runtime resource identity, which
  lets a resolved type carry identity without this module depending on the
  runtime

## Goal

Define the Java types that represent all Component Model concepts.
Everything else parses into, generates from, and instantiates with these types.

## Module

`types` (`io.roastedroot:typewire-types`)

**Package**: `io.roastedroot.typewire.types`

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

All but values, which nothing reaches yet.

### Canonical definitions

Types representing `canon lift`, `canon lower`, `resource.new`,
`resource.drop`, `resource.rep`. The async and threading built-ins are
represented as well.

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
