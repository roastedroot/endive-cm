# Phase 1: Binary Parser

**Status**: Complete except for the start section
**Depends on**: Phase 0 (Type Model)

Every section parses except `start` and `value`, both gated features, which are
rejected as not supported. The `0x02` import and export opcode is rejected too.
Every canonical definition parses, including the async and threading built-ins,
whether or not the runtime can execute them.

One addition was made beyond the original plan. A `Validator` interface now sits
in this module, and `ComponentParser.Builder#build` requires an implementation
unless validation is explicitly disabled. The parser itself stays free of any
dependency on the `wasm-tools` module, so the tests supply a validator backed by
`ComponentValidate`. Some checks the spec test suite expects cannot be made on
the binary and are applied after parsing instead, as default methods on the
interface. See `docs/misc.md` for which of those are restrictions of one
implementation rather than of the model, and for what the runtime is entitled to
assume because validation ran.

## Goal

Parse Component Model binary files (`.wasm` with layer=1) into the Phase 0
type model. Validate that all wasm-tools-produced component binaries can be
round-tripped through the parser.

## Module

`parser` (`io.roastedroot:typewire-parser`)

**Package**: `io.roastedroot.typewire.parser`

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
- `parseStartSection` -- not implemented, the section is rejected

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
