# Claude Code guidelines for endive-cm

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
