# AURA native inference

The Android build fetches the exact llama.cpp commit recorded in `UPSTREAM_MANIFEST.md` and supplies its path as `LLAMA_CPP_DIR` to CMake. This directory builds the `aura_llama` JNI library against llama.cpp.

The JNI layer is CPU-first, limits context to 2,048 tokens and generation to 512 tokens, uses the model's GGUF chat template when available, and serializes model access. It does not spawn a subprocess or expose a generic shell interface.
