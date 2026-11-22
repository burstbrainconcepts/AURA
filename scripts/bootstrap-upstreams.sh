#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run this script from inside a cloned AURA Git repository." >&2
  exit 1
}
cd "$ROOT"

add_pinned_submodule() {
  local url="$1"
  local path="$2"
  local revision="$3"

  if [ -e "$path/.git" ] || [ -f "$path/.git" ]; then
    echo "Submodule already exists: $path"
  elif [ -e "$path" ]; then
    echo "Path exists but is not an initialized submodule; refusing to overwrite: $path" >&2
    return 1
  else
    mkdir -p "$(dirname "$path")"
    git submodule add "$url" "$path"
  fi

  git -C "$path" fetch --quiet origin "$revision"
  git -C "$path" checkout --detach "$revision"
  git add "$path"
  echo "Pinned $path -> $revision"
}

# Approved runtime candidates. Revisions and licences are recorded in UPSTREAM_MANIFEST.md.
add_pinned_submodule "https://github.com/ggml-org/llama.cpp.git" \
  "upstream/engines/llama.cpp" "94f3af3d09e74a0fb35f703194318d3b4037714a"
add_pinned_submodule "https://github.com/ggml-org/whisper.cpp.git" \
  "upstream/engines/whisper.cpp" "5eaee0cc9024eb9e9a99469199920c8b7f5cb9ec"
add_pinned_submodule "https://github.com/dscripka/openWakeWord.git" \
  "upstream/engines/openWakeWord" "368c03716d1e92591906a84949bc477f3a834455"

echo
echo "Pinned submodules are checked out."
echo "Review UPSTREAM_MANIFEST.md and THIRD_PARTY_NOTICES.md before building or distributing."
echo "Commit .gitmodules and the upstream gitlinks from this local clone."
