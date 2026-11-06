#!/bin/bash

# Takes rebuild, verify with an optional program name, wasmtime, or the arguments of compile.sh.

set -euo pipefail

cd "$(dirname "$0")"

container="${CONTAINER:-docker}"
image=endive/component-corpus
mount=(-v "$PWD/src/main/resources:/usr/code" --security-opt label=disable)

case "${1:-all}" in
  rebuild)
    "$container" build . --platform linux/amd64 -t "$image"
    ;;
  wasmtime)
    "$container" build . --platform linux/amd64 -f wasmtime.Dockerfile -t "$image-wasmtime"
    "$container" run --platform linux/amd64 --rm "${mount[@]}" "$image-wasmtime"
    ;;
  verify)
    "$container" run --platform linux/amd64 --rm "${mount[@]}" --entrypoint ./verify.sh \
      "$image" "${@:2}"
    ;;
  *)
    "$container" run --platform linux/amd64 --rm "${mount[@]}" "$image" "$@"
    ;;
esac
