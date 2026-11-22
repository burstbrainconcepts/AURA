#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run inside a cloned AURA Git repository." >&2
  exit 1
}
cd "$ROOT"

# The exact submodule URLs and gitlink commits are already committed in .gitmodules
# and the Git tree. This command fetches the upstream source into the local clone.
git submodule sync --recursive
git submodule update --init --recursive

pin_submodule() {
  local path="$1"
  local revision="$2"
  if [ ! -d "$path/.git" ] && [ ! -f "$path/.git" ]; then
    echo "Submodule was not initialized: $path" >&2
    return 1
  fi
  git -C "$path" fetch --quiet origin "$revision"
  git -C "$path" checkout --detach "$revision"
  echo "Ready: $path @ $revision"
}

pin_submodule "upstream/engines/llama.cpp" "94f3af3d09e74a0fb35f703194318d3b4037714a"
pin_submodule "upstream/engines/whisper.cpp" "5eaee0cc9024eb9e9a99469199920c8b7f5cb9ec"
pin_submodule "upstream/engines/openWakeWord" "368c03716d1e92591906a84949bc477f3a834455"

echo "AURA upstream source checkouts are ready."
