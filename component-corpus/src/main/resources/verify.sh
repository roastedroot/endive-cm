#!/bin/bash

# Runs every compiled program that has a spec on wasmtime, or only the named one.

set -eo pipefail

cd "$(dirname "$0")/compiled"

# Compares one stream when the spec states it.
expectOutput() {
  if jq -e --arg key "$1" 'has($key)' "$spec" > /dev/null; then
    jq -j --arg key "$1" '.[$key]' "$spec" > "$work/expected"
    if ! cmp -s "$work/expected" "$work/$1"; then
      echo "FAIL $base: $1 differs" >&2
      diff "$work/expected" "$work/$1" >&2 || true
      failed=true
    fi
  fi
}

if [[ $# -gt 0 ]]; then
  specs=("$1".*.json)
else
  specs=(*.json)
fi

failures=0
for spec in "${specs[@]}"; do
  base=${spec%.json}
  work=$(mktemp -d)
  failed=false

  options=()
  while IFS= read -r -d '' option; do
    options+=("$option")
  done < <(jq -j '.env // {} | to_entries[] | "--env=\(.key)=\(.value)\u0000"' "$spec")
  root=$(jq -r '.root // empty' "$spec")
  if [[ -n "$root" ]]; then
    cp -r "$base.$root" "$work/root"
    options+=("--dir=$work/root::/")
  fi
  args=()
  while IFS= read -r -d '' arg; do
    args+=("$arg")
  done < <(jq -j '.args // [] | .[] | "\(.)\u0000"' "$spec")

  exitCode=0
  wasmtime run "${options[@]}" "$base.wasm" "${args[@]}" \
    < /dev/null > "$work/stdout" 2> "$work/stderr" || exitCode=$?

  expected=$(jq -r '.exit_code // 0' "$spec")
  if [[ "$exitCode" != "$expected" ]]; then
    echo "FAIL $base: exit code $exitCode, expected $expected" >&2
    cat "$work/stderr" >&2
    failed=true
  fi
  expectOutput stdout
  expectOutput stderr

  if [[ "$failed" == true ]]; then
    failures=$((failures + 1))
  else
    echo "PASS $base"
  fi
  rm -rf "$work"
done

if [[ $failures -gt 0 ]]; then
  echo "$failures program(s) failed" >&2
  exit 1
fi
