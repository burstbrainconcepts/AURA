#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM="$ROOT/upstream/engines/whisper.cpp"
BUILD="$UPSTREAM/build"
MODEL_NAME="${WHISPER_MODEL_NAME:-base}"
MODEL_PATH="${WHISPER_MODEL_PATH:-$UPSTREAM/models/ggml-$MODEL_NAME.bin}"
PORT="${WHISPER_PORT:-8081}"

for command in git cmake; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing prerequisite: $command" >&2
    exit 1
  fi
done

if ! command -v c++ >/dev/null 2>&1 && ! command -v clang++ >/dev/null 2>&1; then
  echo "A C++ compiler is required. Install Xcode Command Line Tools, build-essential, or the Windows C++ build tools." >&2
  exit 1
fi

cd "$ROOT"
git submodule update --init upstream/engines/whisper.cpp

if [[ ! -f "$UPSTREAM/CMakeLists.txt" ]]; then
  echo "Pinned whisper.cpp source was not initialized correctly." >&2
  exit 1
fi

cmake -S "$UPSTREAM" -B "$BUILD" \
  -DCMAKE_BUILD_TYPE=Release \
  -DWHISPER_BUILD_SERVER=ON
cmake --build "$BUILD" --config Release --target whisper-server --parallel

if [[ ! -f "$MODEL_PATH" ]]; then
  echo "Downloading the upstream whisper.cpp $MODEL_NAME model..."
  bash "$UPSTREAM/models/download-ggml-model.sh" "$MODEL_NAME"
fi

if [[ ! -f "$MODEL_PATH" ]]; then
  echo "Model not found at $MODEL_PATH. Set WHISPER_MODEL_PATH to an existing GGML model." >&2
  exit 1
fi

SERVER="$BUILD/bin/whisper-server"
if [[ ! -x "$SERVER" && -x "$BUILD/Release/whisper-server.exe" ]]; then
  SERVER="$BUILD/Release/whisper-server.exe"
fi
if [[ ! -x "$SERVER" && ! -f "$SERVER" ]]; then
  echo "whisper-server binary was not produced at the expected build path." >&2
  exit 1
fi

echo "Starting local-only whisper.cpp server on 127.0.0.1:$PORT"
echo "Model: $MODEL_PATH"
exec "$SERVER" -m "$MODEL_PATH" -l auto --host 127.0.0.1 --port "$PORT"
