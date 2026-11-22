#!/usr/bin/env bash
set -euo pipefail

# Run from the AURA repository root.
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run this script from inside a cloned AURA Git repository." >&2
  exit 1
}
cd "$ROOT"

add_submodule() {
  local url="$1"
  local path="$2"
  if grep -Fq "path = $path" .gitmodules 2>/dev/null; then
    echo "Already registered: $path"
  elif [ -e "$path" ]; then
    echo "Path already exists; refusing to overwrite: $path" >&2
    return 1
  else
    mkdir -p "$(dirname "$path")"
    git submodule add "$url" "$path"
  fi
}

# Core runtime candidates. Pin the checked-out commit after the licence audit.
add_submodule "https://github.com/ggml-org/llama.cpp.git" "upstream/engines/llama.cpp"
add_submodule "https://github.com/ggml-org/whisper.cpp.git" "upstream/engines/whisper.cpp"
add_submodule "https://github.com/dscripka/openWakeWord.git" "upstream/engines/openWakeWord"

# Research references. Keep separate from approved production dependencies.
add_submodule "https://github.com/tokenarc/open-jarvis.git" "upstream/research/android/open-jarvis"
add_submodule "https://github.com/mukeshchandu/Personal-AI-Assistant.git" "upstream/research/android/naomi"
add_submodule "https://github.com/x1watt/eva.git" "upstream/research/android/eva"
add_submodule "https://github.com/CyberMagician/faraday.git" "upstream/research/android/faraday"
add_submodule "https://github.com/Zabuddia/llmdroid.git" "upstream/research/android/llmdroid"
add_submodule "https://github.com/nano-muse/nanoMuse.git" "upstream/research/cross-platform/nanoMuse"

echo
echo "Submodules registered. Review each licence and pin approved revisions."
echo "Then commit .gitmodules and the upstream gitlinks."
