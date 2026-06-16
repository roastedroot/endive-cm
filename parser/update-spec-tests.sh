#!/usr/bin/env bash
set -euo pipefail

REPO="WebAssembly/component-model"
BRANCH="main"
REMOTE_TEST_DIR="test"
LOCAL_TEST_DIR="src/test/resources/spec-tests"

API_BASE="https://api.github.com/repos/${REPO}/contents"
RAW_BASE="https://raw.githubusercontent.com/${REPO}/${BRANCH}"

# Tracks every remote .wast path (relative to LOCAL_TEST_DIR) seen during the run
REMOTE_FILES=()

collect_and_download() {
  local api_path="$1"
  local local_path="$2"

  local entries
  entries=$(curl -fsSL "${API_BASE}/${api_path}")

  while IFS= read -r entry; do
    local type name
    type=$(printf '%s' "$entry" | grep -o '"type":"[^"]*"' | head -1 | cut -d'"' -f4)
    name=$(printf '%s' "$entry" | grep -o '"name":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [[ "$type" == "dir" ]]; then
      collect_and_download "${api_path}/${name}" "${local_path}/${name}"
    elif [[ "$type" == "file" && "$name" == *.wast ]]; then
      mkdir -p "$local_path"
      local dest="${local_path}/${name}"
      REMOTE_FILES+=("$dest")
      echo "  Downloading ${api_path}/${name}"
      curl -fsSL "${RAW_BASE}/${api_path}/${name}" -o "$dest"
    fi
  done < <(printf '%s' "$entries" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for item in data:
    print('{\"type\":\"' + item['type'] + '\",\"name\":\"' + item['name'] + '\"}')")
}

mkdir -p "$LOCAL_TEST_DIR"

echo "Fetching .wast tests from ${REPO}/${REMOTE_TEST_DIR} (branch: ${BRANCH})..."
collect_and_download "$REMOTE_TEST_DIR" "$LOCAL_TEST_DIR"

echo ""
echo "Removing local files no longer present in remote..."
while IFS= read -r local_file; do
  if [[ ! " ${REMOTE_FILES[*]} " == *" ${local_file} "* ]]; then
    echo "  Deleting ${local_file}"
    rm -f "$local_file"
  fi
done < <(find "$LOCAL_TEST_DIR" -name "*.wast" | sort)

# Remove any directories that are now empty
find "$LOCAL_TEST_DIR" -type d -empty -delete 2>/dev/null || true

echo ""
echo "Done. Current files:"
find "$LOCAL_TEST_DIR" -name "*.wast" | sort | sed 's|^|  |'
