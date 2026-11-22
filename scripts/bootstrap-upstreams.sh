#!/usr/bin/env bash
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "Run inside a cloned AURA Git repository." >&2; exit 1; }
cd "$ROOT"
add_pinned_submodule() {
  local url="$1" path="$2" revision="$3"
  if [ -e "$path/.git" ] || [ -f "$path/.git" ]; then
    echo "Submodule already exists: $path"
  elif [ -e "$path" ]; then
    echo "Refusing to overwrite non-submodule path: $path" >&2; return 1
  else
    mkdir -p "$(dirname "$path")"
    git submodule add "$url" "$path"
  fi
  git -C "$path" fetch --quiet origin "$revision"
  git -C "$path" checkout --detach "$revision"
  git add "$path"
  echo "Pinned $path -> $revision"
}
add_pinned_submodule "https://github.com/ggml-org/llama.cpp.git" "upstream/engines/llama.cpp" "94f3af3d09e74a0fb35f703194318d3b4037714a"
add_pinned_submodule "https://github.com/ggml-org/whisper.cpp.git" "upstream/engines/whisper.cpp" "5eaee0cc9024eb9e9a99469199920c8b7f5cb9ec"
add_pinned_submodule "https://github.com/dscripka/openWakeWord.git" "upstream/engines/openWakeWord" "368c03716d1e92591906a84949bc477f3a834455"
echo "Pinned upstreams checked out. Review notices, then commit .gitmodules and gitlinks."
