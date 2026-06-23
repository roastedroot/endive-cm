# Phase 1: Binary Parser

**Status**: In progress
**Depends on**: Phase 0 (Type Model)

## Goal

Parse Component Model binary files (`.wasm` with layer=1) into the Phase 0
type model. Validate that all wasm-tools-produced component binaries can be
round-tripped through the parser.

## Module

`parser` (`run.endive.cm:parser`)

**Package**: `run.endive.cm.parser`

## Deliverables

### ComponentParser.java

The main parser class, structurally modeled after Endive's `Parser.java`:

- Static entry points: `parse(InputStream)`, `parse(byte[])`, `parse(File)`,
  `parse(Path)`
- `Builder` with `includeSectionId(int)`, `withValidation(boolean)`,
  custom section parsers
- `ComponentParserListener` callback interface:
  `void onSection(ComponentSection section)`

### Preamble parsing

- Read `\0asm` magic bytes
- Check layer field: `0x01 0x00` = component (reject `0x00 0x00` -- that's a
  core module)
- Read version bytes

### Section loop

For each section:
1. Read `sectionId` (byte)
2. Read section size (`varUInt32`)
3. Create section-scoped `ByteBuffer` via `asReadOnlyBuffer()` + `limit()`
4. Dispatch to `parseXxxSection(buffer)`
5. Verify exact consumption: `if (buffer.hasRemaining()) throw ...`

### Per-section parsers

- `parseCoreModuleSection` -- extract raw bytes, delegate to Endive's
  `Parser.parse(byte[])` to get a `WasmModule`
- `parseComponentSection` -- recursively call `ComponentParser.parse` on the
  section bytes (handles nested components)
- `parseTypeSection` -- SLEB128 opcodes for component types (negative values
  starting at `0x7f` for `bool`, further negative for composite types)
- `parseCanonSection` -- `canon lift`, `canon lower`, resource operations
- `parseAliasSection` -- outer/export/core-export aliases
- `parseImportSection` -- component imports with optional URLs
- `parseExportSection` -- component exports with extern descriptors
- `parseCoreInstanceSection` -- core module instantiation records
- `parseCoreTypeSection` -- core type definitions at component level
- `parseInstanceSection` -- component instance definitions
- `parseStartSection` -- component start function reference

### ComponentSectionsValidator

Validates section ordering (component section ordering constraints differ
from core Wasm). Inner class or companion, following the
`SectionsValidator` pattern.

### ComponentModule

Top-level parsed representation (analogous to `WasmModule`), with builder
and immutable fields for all sections.

## What's Reused

- `Encoding.readVarUInt32`, `readVarSInt32`, `readName`, `readByte`,
  `readBytes` -- all LEB128 utilities from Endive, used directly
- `Parser.parse(byte[])` from Endive -- for embedded core modules
- Exception hierarchy: `MalformedException`, `InvalidException`

## Testing

- **Component Model spec tests**: sync .wast tests from the [Component Model spec tests](https://github.com/WebAssembly/component-model/tree/main/test)
  and use `wasm-tools json-from-wast` to extract and execute tests, parse with `ComponentParser`, and verify the .wast 
  assertions 
- **Round-trip with wasm-tools**: produce component binaries via
  `wasm-tools component new`, parse with `ComponentParser`, verify structure
- **Error paths**: truncated input, wrong magic, wrong layer, malformed
  LEB128, oversized sections, section order violations
- **Nested components**: verify recursive parsing

## Exit Criteria

`ComponentParser` can parse every valid component binary that wasm-tools
produces, and rejects every invalid one with an appropriate exception.
