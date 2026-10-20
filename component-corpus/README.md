# Component Corpus

Components built by real toolchains, for use as test resources.
The upstream [wasi-testsuite](https://github.com/WebAssembly/wasi-testsuite) has no WASI 0.2 tests,
so the command programs here, together with their expected outcomes, are the conformance programs for the WASI 0.2 host.
The built binaries are committed, so the Maven build needs none of the toolchains below.

## Layout

```
src/main/resources/
  compile.sh          builds the programs into compiled/
  verify.sh           runs every compiled program that has a spec on wasmtime
  rust/<name>/        one Cargo project per program, with its Cargo.lock
    src/main.rs       a command, built for wasm32-wasip2
    src/lib.rs        or a library, built for wasm32-unknown-unknown and then componentized
    spec.json         the expected outcome, optional
    root/             a fixture directory, optional
  compiled/
    <name>.rs.wasm    the component
    <name>.rs.json    spec.json, copied
    <name>.rs.root/   root/, copied
    index.txt         every file under compiled/, since a jar's resources cannot be listed
```

`rust/types` is a library built with `wit-bindgen` from `wit/types.wit`.
It exports functions over records, variants, enums, flags, options, results, lists, strings and tuples,
and a resource with a constructor and a method. It imports nothing, so it runs without WASI.

## Specs

`spec.json` follows the legacy format of the wasi-testsuite
[specification](https://github.com/WebAssembly/wasi-testsuite/blob/main/doc/specification.md),
as Endive's WASI test runner reads it. Every field is optional.

| Field       | Meaning                                               |
|-------------|-------------------------------------------------------|
| `args`      | the arguments after the program name                  |
| `env`       | the whole environment                                 |
| `root`      | `"root"` when the program needs its fixture directory |
| `exit_code` | the expected exit code, `0` when absent               |
| `stdout`    | the expected standard output, not checked when absent |
| `stderr`    | the expected standard error, not checked when absent  |

A runner copies the fixture, `<name>.rs.root/`, to a fresh directory and preopens that as `/`,
which is what Endive's WASI test runner does with `root`.
The current directory of a Rust program is `/`, so `greeting.txt` and `/greeting.txt` name the same fixture file.
Standard input is always empty, and the program name is the runner's choice, so programs print only the arguments after it.

Rust's standard library exits through `wasi:cli/exit.exit`, which carries only success or failure.
Wasmtime reports any failure as exit code 1, so `exit-failure` exits with status 3 and its spec expects 1.

## Adding a program

1. Create a Cargo project in `src/main/resources/rust/<name>/` whose package is named `<name>`,
   with the release profile the other programs use.
2. Run `cargo generate-lockfile` in it, since the build only accepts a committed lockfile.
3. Add `spec.json`, and `root/` if the program reads files.
4. Compile it with `./run.sh rust <name>`.
5. Check its spec against wasmtime with `./run.sh verify <name>`.
6. Commit the sources and every file that changed under `compiled/`.

## Rebuilding

The image pins every tool, so the same sources always compile to the same bytes.

| Tool        | Version                                                       |
|-------------|---------------------------------------------------------------|
| Rust        | 1.96.0, with targets wasm32-wasip2 and wasm32-unknown-unknown |
| wasm-tools  | 1.245.1                                                       |
| wasmtime    | 34.0.1                                                        |
| wit-bindgen | 0.57.1, pinned by `Cargo.lock`                                |

```sh
cd component-corpus

# build the image, the first time or after the Dockerfile changes
./run.sh rebuild

# compile everything, every Rust program, or one program
./run.sh
./run.sh rust
./run.sh rust args

# check every spec, or one, against wasmtime
./run.sh verify
./run.sh verify args
```

Set `CONTAINER=podman` to use Podman instead of Docker.

With the pinned tools installed, the scripts also run on the host and produce the same bytes.
`verify.sh` needs `jq` as well.

```sh
cd component-corpus/src/main/resources
./compile.sh rust
./verify.sh
```

## Consuming

Add the module as a test dependency.

```xml
<dependency>
  <groupId>run.endive.cm</groupId>
  <artifactId>component-corpus</artifactId>
  <scope>test</scope>
</dependency>
```

Then read the compiled files through `ComponentCorpus`.

```java
byte[] component = ComponentCorpus.readBytes("hello-stdout.rs.wasm");
List<String> files = ComponentCorpus.entries();
```
