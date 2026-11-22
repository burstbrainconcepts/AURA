# Pinned upstream revisions

These are the exact upstream commits recorded as Git submodule gitlinks in the AURA `main` branch. A fresh checkout with submodules recursively initialized will fetch these revisions.

| Project | Path | Upstream commit |
|---|---|---|
| llama.cpp | `upstream/engines/llama.cpp` | `94f3af3d09e74a0fb35f703194318d3b4037714a` |
| whisper.cpp | `upstream/engines/whisper.cpp` | `5eaee0cc9024eb9e9a99469199920c8b7f5cb9ec` |
| openWakeWord | `upstream/engines/openWakeWord` | `368c03716d1e92591906a84949bc477f3a834455` |
| Open Jarvis | `upstream/research/android/open-jarvis` | `04873998933830ce582018fe4664ebce5ec64d2f` |
| Naomi | `upstream/research/android/naomi` | `5e5660016916b07e20b8d978dc04305653ad664c` |
| Eva | `upstream/research/android/eva` | `c2cf522c5bfadc7988aa87f02b15d65f8ecb2b44` |
| Faraday | `upstream/research/android/faraday` | `5779fd39efba9d27a4eac386c36725345fd6b4af` |
| LLMDroid | `upstream/research/android/llmdroid` | `fd82d7c3ff8212ef6968d58f8d412c464b95e66c` |
| nanoMuse | `upstream/research/cross-platform/nanoMuse` | `09850259cab00d27af83786f00faa09b5d8494bf` |

**Important:** pinning a repository does not mean its code is approved for production use. Follow `UPSTREAM_MANIFEST.md` and `THIRD_PARTY_NOTICES.md`. In particular, repositories marked research-only must not be copied into AURA-owned code until their licences are confirmed compatible.

## Clone the complete workspace

```bash
git clone --recurse-submodules https://github.com/burstbrainconcepts/AURA.git
cd AURA
```

If you already cloned without submodules:

```bash
git submodule update --init --recursive
```
