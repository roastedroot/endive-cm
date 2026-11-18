# Phase 5: Component Instantiation and Linking

**Status**: Complete for the sync ABI
**Depends on**: Phase 0 (Type Model), Phase 1 (Parser), Phase 3 (Canonical ABI)

Every deliverable below is implemented. The exit criteria are met, and the
generated spec suites pass for every test that does not require async.

Not implemented, all of them gated or async features:

- Value definitions. A `value` instantiation argument is rejected.
- Start definitions. The parser rejects the section, so the runtime never sees
  one.
- Every async canonical built-in. `canon lift`, `canon lower` and the three
  resource built-ins are executed, and any other canon kind is rejected.

Additions beyond the original plan are described under the headings below.

## Goal

Bring it all together: instantiate components at runtime, resolve imports,
link nested components, and manage resource lifetimes.

## Module

`runtime` (`io.roastedroot:typewire-runtime`)

**Package**: `io.roastedroot.typewire.runtime`

## Deliverables

### ComponentInstance

The runtime representation of an instantiated component. Manages:
- A list of instantiated core module instances (each is an Endive `Instance`)
- Component-level export functions (lifted from core functions via Canonical ABI)
- Component-level import satisfaction (lowered to core functions via Canonical ABI)
- Resource tables for `own<T>` / `borrow<T>` handle management
- Start function invocation -- not implemented

Nothing on it is mutable from outside. Definitions go in through a `Builder`
during instantiation and every index space is sealed once `build()` returns.

### ComponentStore

Not in the original plan. A store holds any number of instances at any nesting
depth, and only instances within one store may be wired to each other, because
a handle indexes some instance's table and a resource type is compared by
identity. It is what an embedder is handed first.

### ComponentLinker

Resolves component imports against available exports:
- Link imports to host-provided functions (Java lambdas)
- Link imports to another component's exports (component composition)
- Validate type compatibility between imports and exports

Type compatibility is checked at the host boundary only, because a validator has
already settled it wherever a component instantiates a child it defines. See
`docs/misc.md` for why that is sound and what it depends on. Three matchers do
the work, none of them in the original plan:

- `TypeMatcher` for value and function types
- `CoreModuleMatcher` for `(core module ...)` types
- `ComponentTypeMatcher` for `(component ...)` types, including resources,
  which have no identity before instantiation and so are related by standing one
  in per declaration

Automatic resolution of an import from a component already instantiated into the
store is deferred. Imports are supplied explicitly.

### ResourceTable

Manages the lifecycle of component resources:
- `resource.new` -- allocate a handle index
- `resource.rep` -- retrieve the representation (core Wasm value) for a handle
- `resource.drop` -- release a handle, call the destructor

Implemented as `HandleTable` in the `canonical-abi` module, with the table
itself private to the instance that owns it. Resource types are generative, so
`ResourceTypeInstance` gives one identity per declaration per instantiation and
they compare by reference.

### Canon runtime

Runtime execution of canonical definitions. Only the sync ones are executed:
- `canon lift` -- create a component function from a core function + memory +
  realloc. When called: lift arguments from core Wasm values to component
  values, lower results back.
- `canon lower` -- create a core function from a component function. When
  called: lower arguments from component values to core Wasm values, lift
  results back.

### Alias resolution

At instantiation time, resolve alias definitions by looking up the referenced
index in the appropriate scope (parent component or instance exports).

An outer alias follows the chain of instantiations rather than of definitions,
so an alias reaching out of a nested component lands in the instantiation it was
reached through. `ComponentClosure` carries a component value together with the
scope it was written in for that reason.

## What's Reused

- Endive `Instance` (core module instantiation), `ImportValues` (import
  wiring), `Memory` (linear memory), `Store` pattern
- Phase 0 type model, Phase 1 parser, Phase 3 Canonical ABI

## Testing

- **Smoke tests**: instantiate a simple component (one core module, one
  export), call its export
- **Multi-component linking**: link two components (one imports what the
  other exports), verify correct behavior
- **Resource lifecycle**: create, use, and drop resources; verify destructors
  are called
- **String/list passing**: end-to-end tests passing strings and lists across
  the component boundary
- **Spec suites**: the `cm-test-gen` plugin generates JUnit tests from the
  Component Model repository's own `.wast` scripts, which is what the bulk of
  the coverage rests on

## Exit Criteria

A component can be parsed, instantiated, linked, and its exports invoked
from Java. Resources are properly managed. Multi-component linking works.

Met, for components that do not use async.
