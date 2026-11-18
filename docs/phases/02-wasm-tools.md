# Phase 2: wasm-tools Integration (Component Commands)

**Status**: Complete but for `ComponentLink`

Implemented are `ComponentValidate`, which the test suites use to validate every
binary before parsing it, `JsonFromWast`, which the spec test generator uses to
read `.wast` scripts, `WitParser`, which both reads and now encodes WIT, and
`ComponentEmbed` and `ComponentNew`, which together build a component from a core
module and a world.

`ComponentLink` is not implemented and nothing needs it yet.

`wasm-tools parse`, for turning `.wat` into a binary, is already wrapped upstream
as `run.endive.tools.wasm.Wat2Wasm` and is reused rather than duplicated here.

**Depends on**: None (independent, but Phase 1 testing benefits from it)

## Goal

Wrap the remaining wasm-tools component subcommands as Java APIs, complementing
the existing `WitParser` (which wraps `wasm-tools component wit`).

## Module

`wasm-tools` (`io.roastedroot:typewire-wasm-tools`)

**Package**: `io.roastedroot.typewire.tools`

## Deliverables

Each class follows the pattern established by `WitParser.java`:

1. Load `WasmToolsModule` (cached singleton)
2. Set up `ZeroFs` virtual filesystem for input files
3. Configure `WasiOptions` with stdin/stdout/stderr + directory mappings +
   command arguments
4. Create `WasiPreview1` + `ImportValues` + `Instance`
5. Handle `WasiExitException`, extract output from stdout stream
6. Provide `parse(File)`, `parse(String)`, `parse(InputStream)` overloads

### ComponentValidate.java

Wraps `wasm-tools component validate`. Implemented, and used by every test that
parses a component.

### JsonFromWast.java

Wraps `wasm-tools json-from-wast`. Implemented, and used by the spec test
generator.

### ComponentNew.java

Wraps `wasm-tools component new`. Implemented. Takes a core module carrying
embedded component types and produces a component binary.

### ComponentEmbed.java

Wraps `wasm-tools component embed`. Implemented. Records a world inside a core
module as a `component-type` custom section.

Both accept their input in either the binary or the text format, because
wasm-tools tells the two apart by content rather than by file name. That is what
lets a test fixture be written as `.wat` and go straight through both commands
without a guest language toolchain in the way.

A world's top-level import binds to the core module name `$root` rather than to
the world's own id, so `$root` is the module name a core module has to use.

### ComponentLink.java

Wraps `wasm-tools component link`. Links/composes multiple components.

## What's Reused

All from Endive:
- `WasmToolsModule` -- the precompiled wasm-tools binary
- `WasiPreview1` -- WASI runtime
- `WasiOptions` -- configuration builder
- `ZeroFs` -- in-memory virtual filesystem
- `ByteArrayMemory` -- isolated Wasm memory
- `ImportValues` -- import wiring
- `Instance` -- Wasm execution

The existing `WitParser` in this repo is the exact template.

## Testing

- Integration test per command with known-good inputs
- `ComponentNew` output must parse successfully via `ComponentParser` (Phase 1)
- `ComponentValidate` must accept valid components and reject invalid ones

## Exit Criteria

All four wasm-tools component commands are wrapped and tested. `ComponentNew`
output feeds into `ComponentParser` successfully.

Met but for `ComponentLink`. `ComponentNew` output validates and round-trips
back through `WitParser` as the world that built it.
