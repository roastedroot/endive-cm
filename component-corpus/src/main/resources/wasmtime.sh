#!/bin/bash

# Builds wasmtime's WASI 0.2 socket test programs into compiled/wasmtime, the way wasmtime does,
# except in release mode to keep them small. WASMTIME_SOURCE names a checkout of the pinned tag
# holding the release's command adapter.

set -euo pipefail

cd "$(dirname "$0")"

compiled="$PWD/compiled"
out="$compiled/wasmtime"
source="${WASMTIME_SOURCE:?set WASMTIME_SOURCE to a wasmtime checkout}"
adapter="$source/wasi_snapshot_preview1.command.wasm"
export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$source/target}"

programs=()
for file in "$source"/crates/test-programs/src/bin/{p2_tcp_,p2_udp_,p2_ip_name_lookup,p2_cli_no_}*.rs; do
  programs+=("$(basename "$file" .rs)")
done

(set -x; cd "$source" && cargo build --locked --release -p test-programs \
  --target wasm32-wasip1 "${programs[@]/#/--bin=}")

rm -rf "$out"
mkdir -p "$out"
for program in "${programs[@]}"; do
  (set -x; wasm-tools component new "$CARGO_TARGET_DIR/wasm32-wasip1/release/$program.wasm" \
    --adapt "wasi_snapshot_preview1=$adapter" -o "$out/$program.wasm")
  (set -x; wasm-tools validate "$out/$program.wasm")
done

index=$(cd "$compiled" && find . -type f ! -name index.txt | sed 's|^\./||' | LC_ALL=C sort)
echo "$index" > "$compiled/index.txt"

# Inside a container, hand the outputs back to the owner of the mounted sources.
if [[ $EUID -eq 0 ]]; then
  chown -R "$(stat -c %u:%g .)" "$compiled"
fi
