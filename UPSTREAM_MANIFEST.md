# Upstream manifest

Reviewed: 2026-11-22. This manifest records exact upstream revisions inspected during the initial audit. A listed candidate is not automatically approved for redistribution.

## Approved runtime candidates

| Component | Source | Pinned commit | Detected licence | Intended role | Status |
|---|---|---|---|---|---|
| llama.cpp | https://github.com/ggml-org/llama.cpp | `94f3af3d09e74a0fb35f703194318d3b4037714a` | MIT | Local LLM inference | Approved for integration subject to bundled dependency and model licence review |
| whisper.cpp | https://github.com/ggml-org/whisper.cpp | `5eaee0cc9024eb9e9a99469199920c8b7f5cb9ec` | MIT | Offline speech-to-text | Approved for integration subject to model and asset licence review |
| openWakeWord | https://github.com/dscripka/openWakeWord | `368c03716d1e92591906a84949bc477f3a834455` | Apache-2.0 | Wake-word detection | Approved for integration subject to model/voice asset licence review |

## Research-only candidates (not approved for incorporation)

| Project | Source | Detected status | Reason |
|---|---|---|---|
| Jarvis Android | https://github.com/alwin123098/jarvis-android | GitHub API reports `NOASSERTION` | README describes MIT, but no machine-detected repository licence was available in the metadata audit. Verify the actual LICENSE file and dependencies before reuse. |
| Llamatik | https://github.com/llamatik/Llamatik | GitHub API reports `NOASSERTION` | Potentially relevant Kotlin Multiplatform SDK; verify root and subproject licences, maturity, security and platform coverage before adoption. |
| The Machine | https://github.com/hasan-ismail/themachine | AGPL-3.0 | Reference only unless AURA explicitly accepts AGPL distribution obligations or obtains separate permission. |
| Piper | https://github.com/rhasspy/piper | Not yet verified | Verify current upstream location, code licence, voice/model licences and maintenance before integration. |
| Naomi / Personal AI Assistant | https://github.com/mukeshchandu/Personal-AI-Assistant | Not yet verified | Research only until a valid licence and current source are confirmed. |
| Eva | https://github.com/x1watt/eva | Custom licence previously reported | Research only pending review of exact current licence terms and commercial eligibility. |
| Faraday | https://github.com/CyberMagician/faraday | PolyForm Noncommercial 1.0.0 previously reported | No commercial incorporation without separate permission. |
| LLMDroid | https://github.com/Zabuddia/llmdroid | Not yet verified | Research only until a valid licence and current source are confirmed. |
| nanoMuse | https://github.com/nano-muse/nanoMuse | GPL-3.0-or-later previously reported | Research only; assess copyleft obligations before distribution. |
| Open Jarvis (tokenarc) | https://github.com/tokenarc/open-jarvis | Not yet verified | Previous candidate; current status and licence must be independently confirmed. |

## Pinned revision policy

- The three approved candidates above are pinned to exact commit SHAs in `scripts/bootstrap-upstreams.sh`.
- Submodule registration and checkout are separate from code integration. A successful bootstrap does not mean an engine has been compiled, wrapped or tested on a device.
- Do not update pins without reviewing the new revision's licence, dependency tree, security advisories and release notes.
- Keep third-party code inside `upstream/`; write AURA adapters and product code outside submodule directories.
- Preserve upstream notices and include relevant third-party notices in any distributed binary.

## Mandatory checks before shipping

1. Inspect the complete upstream licence and all nested dependency licences.
2. Review model-weight, tokenizer, voice, dataset and generated-asset terms separately from source-code licences.
3. Record the reviewed commit SHA and date.
4. Run security and dependency scans.
5. Document the AURA adapter, supported platforms, fallback behaviour and integration tests.
6. Re-review before every upstream version update.
