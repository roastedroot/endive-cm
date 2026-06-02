# Endive Component Model -- Constitution

This document defines the governing principles, architecture, and verification
strategy for bringing WebAssembly Component Model support to the
[Endive](https://github.com/bytecodealliance/endive) runtime.

For per-phase details see the [phases/](phases/) directory.

## Governing Principles

These apply across all phases and must not be violated:

1. **Hand-rolled binary parser** modeled after Endive's `Parser.java` --
   `ByteBuffer` cursor with `ByteOrder.LITTLE_ENDIAN`, `Encoding.java` LEB128
   utilities, section dispatch via switch, section-scoped buffers with
   exact-consumption checks, builder pattern, `ParserListener`, selective
   section parsing via `BitSet`, immutable final objects.

2. **Binary format only** -- no text/WAT parsing in the Component Model
   parser. Text-to-binary conversion is handled by wrapping wasm-tools
   commands.

3. **Reuse wasm-tools** as a WASI guest for all toolchain operations
   (validate, component new/embed/link). wasm-tools output is the authority
   for spec compliance.

4. **Reuse Endive infrastructure** -- `Encoding.java`, `WasmModule`,
   `Instance`, `Memory`, `WasiPreview1`, `ZeroFs`, `WasmToolsModule`.

5. **Immutable types** -- all parsed/constructed objects are immutable after
   `build()`. Collections via `List.copyOf`, `Map.copyOf`.

6. **Separate index spaces** -- core modules, core instances, core types,
   components, instances, aliases, component types, and component functions
   are never collapsed into a single flat space.

7. **Java 11 baseline** -- no records, sealed classes, or pattern matching
   in production code.

8. **Maven multi-module** under `run.endive.cm` groupId, Google Java Format
   (AOSP style), checkstyle, no wildcard imports.

## Module Dependency Graph

```
wit-parser (existing)
     |
     v
  types (Phase 0)
     |
     +-----> parser (Phase 1)         depends on: types, endive:wasm
     |
     +-----> wasm-tools (Phase 2)     depends on: endive:wasm-tools, endive:wasi
     |
     +-----> canonical-abi (Phase 3)  depends on: types, endive:runtime
     |
     +-----> bindgen (Phase 4)        depends on: types, canonical-abi, parser, javaparser
     |
     v
  runtime (Phase 5)                   depends on: types, parser, canonical-abi, endive:runtime
     |
     v
  wasi-p2 (Phase 6)                   depends on: runtime, types, canonical-abi
```

## Verification

Each phase has its own test suite. Cross-phase verification:

- Phase 1 parser must accept every binary that Phase 2 wasm-tools produces.
- Phase 3 Canonical ABI round-trips must be lossless.
- Phase 4 generated code must compile and correctly call Phase 5 runtime.
- Phase 6 must pass the WASI P2 conformance suite.

Run `mvn clean install` at the root to build and test all modules.
CI (`.github/workflows/ci.yml`) runs on every push.

## Phases

| Phase | Name | Module | Document |
|-------|------|--------|----------|
| 0 | Type Model Foundation | `types` | [00-type-model.md](phases/00-type-model.md) |
| 1 | Binary Parser | `parser` | [01-binary-parser.md](phases/01-binary-parser.md) |
| 2 | wasm-tools Integration | `wasm-tools` | [02-wasm-tools.md](phases/02-wasm-tools.md) |
| 3 | Canonical ABI | `canonical-abi` | [03-canonical-abi.md](phases/03-canonical-abi.md) |
| 4 | WIT Bindgen | `bindgen` | [04-wit-bindgen.md](phases/04-wit-bindgen.md) |
| 5 | Component Instantiation | `runtime` | [05-instantiation.md](phases/05-instantiation.md) |
| 6 | WASI Preview 2 | `wasi-p2` | [06-wasi-p2.md](phases/06-wasi-p2.md) |
