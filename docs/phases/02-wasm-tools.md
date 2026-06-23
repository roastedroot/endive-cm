# Phase 2: wasm-tools Integration (Component Commands)

**Status**: In progress
**Depends on**: None (independent, but Phase 1 testing benefits from it)

## Goal

Wrap the remaining wasm-tools component subcommands as Java APIs, complementing
the existing `WitParser` (which wraps `wasm-tools component wit`).

## Module

`wasm-tools` (`run.endive.cm:wasm-tools`)

**Package**: `run.endive.cm.tools`

## Deliverables

Each class follows the pattern established by `WitParser.java`:

1. Load `WasmToolsModule` (cached singleton)
2. Set up `ZeroFs` virtual filesystem for input files
3. Configure `WasiOptions` with stdin/stdout/stderr + directory mappings +
   command arguments
4. Create `WasiPreview1` + `ImportValues` + `Instance`
5. Handle `WasiExitException`, extract output from stdout stream
6. Provide `parse(File)`, `parse(String)`, `parse(InputStream)` overloads

### ComponentNew.java

Wraps `wasm-tools component new`. Takes a core Wasm module + WIT and produces
a component binary.

### ComponentEmbed.java

Wraps `wasm-tools component embed`. Embeds component type information into
a core module.

### ComponentValidate.java

Wraps `wasm-tools component validate`. Validates a component binary against
the spec.

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
