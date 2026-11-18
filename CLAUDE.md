# Claude Code guidelines for Typewire

## Endive runtime reference

When you need to inspect Endive internals (e.g., `Encoding.java`, `WasmModule`, `Parser`, `WasmWriter`),
clone the repo locally and read the source files directly. **Never** extract sources from Maven jars.

```sh
git clone https://github.com/bytecodealliance/endive.git /tmp/endive
```

Key paths within the clone:
- `wasm/src/main/java/run/endive/wasm/` — core wasm types, parser, encoding
- `runtime/src/main/java/run/endive/runtime/` — runtime, instance, memory
- `wasi/src/main/java/run/endive/wasi/` — WASI preview 1
- `wasm-tools/src/main/java/run/endive/tools/wasm/` — wasm-tools WASI module
- `test-gen-lib/src/main/java/run/endive/testgen/` — test generation library (reference for cm-test-gen-lib)
- `test-gen-plugin/src/main/java/run/endive/maven/` — test generation plugin (reference for cm-test-gen-plugin)

## Build

Always use `mvn clean install` from the repo root to see changes — the cm-test-gen-plugin
is a Maven plugin so artifacts must be installed to the local repo for downstream modules to use them.

## Tools

Take advantage of the idea MCP server if it is currently available.

## Code Style

Code should always use idiomatic Java style and follow the patterns that have already been established throughout
the codebase. Code should adhere to the style enforced by the project's CheckStyle and Spotless build plugins.

When writing comments, always prefer simple, clear, and concise sentences. Omit unnecessary words and avoid overly
complex constructions. Do not ever use em dashes, semicolons, or colons in the middle of sentences. Follow good
English grammar rules and do not ever end a sentence with a preposition. When writing comments for classes or
methods, included only the minimum information needed by a consumer/caller of the API in question. Inline comments
should be avoided except where absolutely needed for brief explanations that cannot be inferred from the code itself.

When building functionality dictated by the WebAssembly Component Model specification, include links to
the specific sections of the specification whenever possible in method-level comments, and prefer including such
links over repeating prose from the specification.
