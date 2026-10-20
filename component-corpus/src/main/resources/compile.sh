#!/bin/bash

# Arguments are <lang|all> and an optional program name, both defaulting to all.

set -euo pipefail

cd "$(dirname "$0")"

compiled="$PWD/compiled"
export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$(cd ../../.. && pwd)/target/cargo}"

expectVersion() {
  if [[ "$("$1" --version)" != "$1 $2"* ]]; then
    echo "warning: $1 is not version $2, so outputs may differ from the committed ones" >&2
  fi
}

copySpec() {
  rm -rf "$2.json" "$2.root"
  if [[ -f "$1/spec.json" ]]; then
    cp "$1/spec.json" "$2.json"
  fi
  if [[ -d "$1/root" ]]; then
    cp -r "$1/root" "$2.root"
  fi
}

# A crate with src/lib.rs is a library, built as a core module and then componentized.
compileRust() {
  local crate
  crate=$(basename "$1")
  if [[ -f "$1/src/lib.rs" ]]; then
    (set -x; cd "$1" && cargo build --locked --release --target wasm32-unknown-unknown)
    (set -x; wasm-tools component new \
      "$CARGO_TARGET_DIR/wasm32-unknown-unknown/release/${crate//-/_}.wasm" \
      -o "$compiled/$crate.rs.wasm")
  else
    (set -x; cd "$1" && cargo build --locked --release --target wasm32-wasip2)
    cp "$CARGO_TARGET_DIR/wasm32-wasip2/release/$crate.wasm" "$compiled/$crate.rs.wasm"
  fi
  copySpec "$1" "$compiled/$crate.rs"
}

lang="${1:-all}"
name="${2:-all}"

if [[ "$lang" != "all" && "$lang" != "rust" ]]; then
  echo "Don't know how to compile language [$lang]" >&2
  exit 1
fi

expectVersion rustc 1.96.0
expectVersion wasm-tools 1.245.1

mkdir -p "$compiled"
if [[ "$name" == "all" ]]; then
  echo "Compiling all programs in ./rust/*"
  rm -rf "$compiled"/*.rs.wasm "$compiled"/*.rs.json "$compiled"/*.rs.root
  for dir in ./rust/*/; do
    compileRust "${dir%/}"
  done
else
  compileRust "./rust/$name"
fi

index=$(cd "$compiled" && find . -type f ! -name index.txt | sed 's|^\./||' | LC_ALL=C sort)
echo "$index" > "$compiled/index.txt"

for wasm in "$compiled"/*.wasm; do
  (set -x; wasm-tools validate "$wasm")
done

# Inside a container, hand the outputs back to the owner of the mounted sources.
if [[ $EUID -eq 0 ]]; then
  chown -R "$(stat -c %u:%g .)" "$compiled"
fi
