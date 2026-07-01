# Endive Component Model

This repository is a collaborative space to design and implement
[WebAssembly Component Model](https://github.com/WebAssembly/component-model) support
for the [Endive](https://github.com/bytecodealliance/endive) runtime.

The Component Model defines how Wasm modules compose through
[WIT](https://component-model.bytecodealliance.org/design/wit.html) interfaces
and the [Canonical ABI](https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md).
Bringing this to Endive means JVM applications will be able to instantiate, link,
and interact with Wasm components using idiomatic Java APIs, with zero native dependencies.

## Prior Work

This effort builds on earlier exploration in the ecosystem:

- **[Component Model spike on Chicory](https://github.com/SousaDiconium/chicory/pull/1)**:
  a proof-of-concept by SousaDiconium adding a `wasm-component` module with WIT parsing,
  Canonical ABI encoding/decoding, code generators (records, variants, component wrappers),
  bidirectional host-guest communication, and 106 passing tests.
  This spike demonstrated the feasibility of the approach while highlighting areas
  that need further design work for production readiness.

- **[webassembly4j](https://github.com/tegmentum/webassembly4j)**:
  a unified Java API for WebAssembly by tegmentum, abstracting over multiple runtimes
  (Wasmtime, WAMR, GraalWasm, Chicory). Its `webassembly4j-bindgen` module provides
  WIT-to-Java code generation as a Maven plugin and CLI, demonstrating another approach
  to Component Model tooling in the JVM ecosystem.

## Vision

The long-term goal is to bring full Component Model support to Endive:

- **WIT parsing**: spec-compliant parsing of WIT definitions (available now via the `wit-parser` module)
- **Canonical ABI**: encoding and decoding of Component Model types for Java
- **WIT bindgen**: generate type-safe Java bindings from WIT definitions
- **Component instantiation and linking**: load, instantiate, and compose Wasm components

## Current Status

Initial work is in progress on the component type model (`types` module), binary parser (`parser` module), and
supporting infrastructure including initial `wasm-tools component` command support (`wasm-tools` module).

The repository includes a `wit-parser` module that wraps the
[wasm-tools](https://github.com/bytecodealliance/wasm-tools) `component wit` command,
using the same pattern as the
[wasm-tools module](https://github.com/bytecodealliance/endive/tree/main/wasm-tools) in the main Endive repo.
This gives spec-compliant WIT parsing from day one with zero maintenance burden.
Updating the wasm-tools version automatically updates the parser.

## Getting Started

### Prerequisites

- Java 11+
- Maven 3.6+
- Endive installed locally (see below)

### Building

Since this project depends on Endive at `999-SNAPSHOT`, you need to build and install Endive first:

```sh
git clone https://github.com/bytecodealliance/endive.git
cd endive
mvn -Dquickly
```

Then build endive-cm:

```sh
cd endive-cm
mvn clean install
```

The build is fully self-contained: the `cm-test-gen-plugin` automatically downloads the
[Component Model spec tests](https://github.com/WebAssembly/component-model/tree/main/test)
and generates JUnit 5 test classes during the `generate-test-sources` phase.
See [CONTRIBUTING.md](CONTRIBUTING.md) for details on adding new test coverage.

### Usage

```java
import run.endive.cm.wit.WitParser;

String output = WitParser.parse("package example:hello;\n"
        + "world hello {\n"
        + "  export greet: func(name: string) -> string;\n"
        + "}\n");
```

## Get Involved

This project is in its early stages and we welcome contributions!
Whether you're interested in the Canonical ABI, code generation,
component linking, or testing, there's plenty to work on.

- **Chat**: [Join the Endive Zulip stream](https://bytecodealliance.zulipchat.com/#narrow/stream/endive)
- **Issues**: open an issue in this repository to discuss ideas or report problems
- **Pull requests**: contributions of all sizes are welcome

## License

Apache 2.0. See [LICENSE](LICENSE) for details.
