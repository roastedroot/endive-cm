# Contributing

## Component Model Spec Tests

The `parser` module is tested against the official
[WebAssembly Component Model test suite](https://github.com/WebAssembly/component-model/tree/main/test).
Tests are automatically downloaded and generated at build time by the `cm-test-gen-plugin` Maven plugin
(following the same model as Endive's `test-gen-plugin`).

### How it works

The `.wast` files in the Component Model repo are assertion-based spec tests — the same format used by
[Wasmtime](https://docs.wasmtime.dev/contributing-testing.html),
[WasmEdge](https://github.com/WasmEdge/wasmedge-spectest), and other implementors.

During `mvn install`, the plugin:

1. Downloads the Component Model test suite from GitHub (cached locally in `component-model-testsuite/`)
2. Converts each included `.wast` file to JSON + compiled `.wasm` binaries via `wasm-tools json-from-wast`
3. Generates a JUnit 5 test class per `.wast` file in `target/generated-test-sources/cm-test-gen/`
4. Each command in the `.wast` file becomes a separate `@Test` method:
   - `module` / `component` — parse with `ComponentParser` and validate with `ComponentValidate`
   - `assert_malformed` / `assert_invalid` / `assert_unlinkable` — verify that validation throws `ComponentValidateException`
   - `assert_return` / `assert_trap` — generated as `@Disabled` placeholders (require component instantiation, not yet implemented)

### Current coverage

The plugin configuration in [`parser/pom.xml`](parser/pom.xml) declares three lists:

- **`includedWasts`** — `.wast` files that are actively tested (currently all 31 `wasm-tools/*.wast` files)
- **`excludedWasts`** — `.wast` files not yet tested (e.g. `async/`, `wasmtime/`, `values/` — require runtime support)
- **`excludedTests`** — individual test methods that are `@Disabled` due to unimplemented parser sections (141 tests)

All `.wast` files must be in either `includedWasts` or `excludedWasts` — the plugin will fail if any file is unaccounted for.

Test counts (from `wasm-tools/` directory):
- **530 total** generated test methods across 31 test classes
- **389 passing** (validation + parsing assertions)
- **141 skipped** (`@Disabled` — blocked on unimplemented sections: Core Module, Import, Export, Instance, Alias, Canon)

### Adding a new `.wast` file

1. Move the file from `<excludedWasts>` to `<includedWasts>` in [`parser/pom.xml`](parser/pom.xml)
   (keep both lists sorted alphabetically):

   ```xml
   <includedWasts>
     ...
     <wast>wasmtime/simple.wast</wast>        <!-- newly added -->
     ...
   </includedWasts>
   ```

2. Run `mvn clean install -pl parser` to generate and run the new tests

3. If some generated tests fail due to unimplemented features, add them to `<excludedTests>`:

   ```xml
   <excludedTests>
     ...
     <test>CmSpecWasmtimeSimpleTest.test5</test>
     ...
   </excludedTests>
   ```

   Tests in `excludedTests` are generated with `@Disabled` and show as skipped in reports —
   they serve as a tracking mechanism for what needs to be implemented.

### Updating the test suite

The plugin downloads the test suite from the `main` branch of `WebAssembly/component-model`
by default. To pin to a specific commit, set `<testSuiteRepoRef>` in the plugin configuration:

```xml
<testSuiteRepoRef>abc123def456</testSuiteRepoRef>
```

To force a re-download, delete the `component-model-testsuite/` directory.

### Roadmap: runtime tests

The `wasmtime/` and `values/` directories contain `assert_return` and `assert_trap` commands
that test component instantiation and function invocation. These are currently in `excludedWasts`
and will be enabled once component instantiation is implemented. The test generator already
recognizes these command types and generates `@Disabled` placeholder methods for them.

### Module structure

| Module | Purpose |
|--------|---------|
| `cm-test-gen-lib` | Core library: test suite download, `.wast` conversion, Java test class generation |
| `cm-test-gen-plugin` | Maven plugin wrapper (phase: `generate-test-sources`) |
| `wasm-tools` | Provides `JsonFromWast` (converts `.wast` to JSON + `.wasm` via wasm-tools WASI module) |

## General

- Java 11+ required (parser module requires Java 16+)
- Run `mvn clean install` from the root to build everything
- The build automatically downloads all external dependencies (no manual steps needed)
- Code style is enforced by Spotless (Google Java Format, AOSP style) and Checkstyle
