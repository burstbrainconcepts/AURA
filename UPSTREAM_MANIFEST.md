# Upstream manifest

Reviewed: 2026-11-22. Exact revisions inspected in this audit are recorded below.

## Approved runtime candidates

| Component | Source | Pinned commit | Detected licence | Role |
|---|---|---|---|---|
| llama.cpp | https://github.com/ggml-org/llama.cpp | `94f3af3d09e74a0fb35f703194318d3b4037714a` | MIT | Local LLM inference |
| whisper.cpp | https://github.com/ggml-org/whisper.cpp | `5eaee0cc9024eb9e9a99469199920c8b7f5cb9ec` | MIT | Offline speech-to-text |
| openWakeWord | https://github.com/dscripka/openWakeWord | `368c03716d1e92591906a84949bc477f3a834455` | Apache-2.0 | Wake-word detection |

## Research-only candidates

| Project | Source | Status / gate |
|---|---|---|
| Jarvis Android | https://github.com/alwin123098/jarvis-android | GitHub API reports `NOASSERTION`; README describes MIT. Verify actual LICENSE and dependencies before reuse. |
| Llamatik | https://github.com/llamatik/Llamatik | GitHub API reports `NOASSERTION`; verify root/subproject licences, security and platform coverage. |
| The Machine | https://github.com/hasan-ismail/themachine | AGPL-3.0; reference only unless AURA accepts AGPL obligations or obtains separate permission. |
| Piper | https://github.com/rhasspy/piper | Current upstream, code licence and voice/model licences not yet verified. |
| Naomi | https://github.com/mukeshchandu/Personal-AI-Assistant | Research only until licence is confirmed. |
| Eva | https://github.com/x1watt/eva | Custom licence previously reported; review exact current terms. |
| Faraday | https://github.com/CyberMagician/faraday | PolyForm Noncommercial previously reported; no commercial incorporation without permission. |
| LLMDroid | https://github.com/Zabuddia/llmdroid | Research only until licence is confirmed. |
| nanoMuse | https://github.com/nano-muse/nanoMuse | GPL-3.0-or-later previously reported; assess copyleft obligations. |
| Open Jarvis (tokenarc) | https://github.com/tokenarc/open-jarvis | Current status and licence not yet verified. |

## Rules

- The three approved candidates are pinned to exact commits in `scripts/bootstrap-upstreams.sh`.
- Submodule registration is not the same as integration, compilation or device testing.
- Review nested dependencies, models, tokenizer, voices, datasets and generated assets separately from source-code licences.
- Keep third-party source inside `upstream/`; AURA adapters and product code belong outside submodule directories.
- Preserve upstream notices and re-review every update before shipping.
