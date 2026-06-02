# Phase 3: Canonical ABI

**Status**: Not started
**Depends on**: Phase 0 (Type Model)

## Goal

Implement the Canonical ABI -- the contract that defines how component-level
types (records, strings, lists, etc.) map to core Wasm values and linear memory.
Required for both code generation (Phase 4) and component instantiation
(Phase 5).

## Module

`canonical-abi` (`run.endive.cm:canonical-abi`)

**Package**: `run.endive.cm.abi`

## Deliverables

### Type layout computation

For each component-level type, compute:
- `size` -- byte size in linear memory
- `alignment` -- required alignment
- `flattenedTypes` -- list of core Wasm `ValType` values representing this
  type when passed by value

Constants: `MAX_FLAT_PARAMS = 16`, `MAX_FLAT_RESULTS = 1`. Beyond these
thresholds, values are passed via pointer indirection.

### CanonicalLower

Lowering: convert component-level values (Java objects) to core Wasm
representation. Handles:

- **Primitives**: direct mapping to `i32`/`i64`/`f32`/`f64`
- **Strings**: UTF-8 encoding into linear memory, returns `(pointer, length)`
- **Lists**: element encoding into linear memory, returns `(pointer, length)`
- **Records**: field-by-field encoding with alignment padding
- **Variants / option / result**: discriminant byte + payload encoding
- **Flags**: bit vector encoding
- **Resources**: handle table index

### CanonicalLift

Lifting: convert core Wasm representation back to component-level values
(Java objects). The inverse of lowering.

### Realloc

Abstraction over the `cabi_realloc` function that components export. Lowering
calls realloc to allocate linear memory for strings, lists, and other
heap-allocated types.

### String encoding

The Canonical ABI supports three string encodings:
- UTF-8
- UTF-16
- latin1+UTF-16

The encoding is specified at `canon lift`/`canon lower` definition time.
All three must be implemented.

## What's Reused

- `Memory` interface from Endive runtime for linear memory read/write
- Phase 0 type model for all component-level type definitions

## Testing

- **Layout computation**: unit tests for size, alignment, and flattening for
  all type forms
- **Round-trip**: lower a Java value, lift it back, verify equality
- **Cross-validation**: compare against the Python reference implementation
  from the Component Model spec repository
- **Edge cases**: empty strings, empty lists, deeply nested records, variant
  discriminants at alignment boundaries, flags with >32 bits

## Exit Criteria

Every Component Model type can be lowered to and lifted from linear memory
correctly. String encoding/decoding works for all three encodings.
