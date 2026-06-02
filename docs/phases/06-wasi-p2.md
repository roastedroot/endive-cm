# Phase 6: WASI Preview 2

**Status**: Not started
**Depends on**: Phase 5 (Instantiation), Phase 0 (Type Model),
Phase 3 (Canonical ABI)

## Goal

Implement WASI Preview 2 interfaces so that components targeting
`wasm32-wasip2` can run on Endive. WASI P2 is defined in terms of Component
Model interfaces (WIT), so it requires everything from Phases 0--5.

## Module

`wasi-p2` (`run.endive.cm:wasi-p2`)

**Package**: `run.endive.cm.wasi`

## Deliverables

Each is a WIT world implemented as Java host functions:

### wasi:io

Streams (`input-stream`, `output-stream`) and pollable. Foundation for all
other WASI P2 interfaces.

### wasi:cli

Standard I/O (`stdin`, `stdout`, `stderr`), environment variables,
command-line arguments, exit code.

### wasi:filesystem

File and directory operations. Maps to Java NIO (`java.nio.file`).

### wasi:clocks

Wall clock and monotonic clock.

### wasi:random

Random number generation.

### wasi:sockets (stretch goal)

TCP/UDP socket operations.

## What's Reused

- Existing `WasiPreview1` in Endive as a reference for how to implement WASI
  host functions (though P2 uses component-level interfaces, not raw
  functions -- fundamentally different)
- Phase 5 runtime for component instantiation and linking

## Testing

- WASI P2 conformance test suite
- Real-world components compiled from Rust (`--target wasm32-wasip2`) and
  Go (`GOARCH=wasm GOOS=wasip2`)

## Exit Criteria

A component compiled with `--target wasm32-wasip2` can run on Endive with
basic I/O, filesystem, and CLI support.
