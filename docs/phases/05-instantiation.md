# Phase 5: Component Instantiation and Linking

**Status**: Not started
**Depends on**: Phase 0 (Type Model), Phase 1 (Parser), Phase 3 (Canonical ABI)

## Goal

Bring it all together: instantiate components at runtime, resolve imports,
link nested components, and manage resource lifetimes.

## Module

`runtime` (`run.endive.cm:runtime`)

**Package**: `run.endive.cm.runtime`

## Deliverables

### ComponentInstance

The runtime representation of an instantiated component. Manages:
- A list of instantiated core module instances (each is an Endive `Instance`)
- Component-level export functions (lifted from core functions via Canonical ABI)
- Component-level import satisfaction (lowered to core functions via Canonical ABI)
- Resource tables for `own<T>` / `borrow<T>` handle management
- Start function invocation

### ComponentLinker

Resolves component imports against available exports:
- Link imports to host-provided functions (Java lambdas)
- Link imports to another component's exports (component composition)
- Validate type compatibility between imports and exports

### ResourceTable

Manages the lifecycle of component resources:
- `resource.new` -- allocate a handle index
- `resource.rep` -- retrieve the representation (core Wasm value) for a handle
- `resource.drop` -- release a handle, call the destructor

### Canon runtime

Runtime execution of canonical definitions:
- `canon lift` -- create a component function from a core function + memory +
  realloc. When called: lift arguments from core Wasm values to component
  values, lower results back.
- `canon lower` -- create a core function from a component function. When
  called: lower arguments from component values to core Wasm values, lift
  results back.

### Alias resolution

At instantiation time, resolve alias definitions by looking up the referenced
index in the appropriate scope (parent component or instance exports).

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
- Reference the 106 test scenarios from the SousaDiconium spike

## Exit Criteria

A component can be parsed, instantiated, linked, and its exports invoked
from Java. Resources are properly managed. Multi-component linking works.
