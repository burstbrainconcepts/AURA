# Upstream manifest

This is the source-of-truth register for external repositories considered for AURA. Revisions must be pinned in Git submodule metadata and recorded here after checkout. A repository being listed does not grant permission to copy, modify or redistribute its code.

| Project | Source | Intended role | Initial status | Licence gate |
|---|---|---|---|---|
| Open Jarvis | https://github.com/tokenarc/open-jarvis | Android assistant / automation reference | Candidate; verify current repo state | Reconfirm licence and dependencies at pinned commit |
| Naomi / Personal AI Assistant | https://github.com/mukeshchandu/Personal-AI-Assistant | Android voice assistant reference | Research-only pending licence confirmation | No licence confirmed in initial review; do not copy |
| Eva | https://github.com/x1watt/eva | Offline knowledge and retrieval reference | Research-only / conditional | Custom licence; confirm commercial eligibility or obtain permission |
| Faraday | https://github.com/CyberMagician/faraday | Offline voice-agent reference | Research-only | PolyForm Noncommercial; no commercial incorporation without separate permission |
| LLMDroid | https://github.com/Zabuddia/llmdroid | Android accessibility automation reference | Research-only pending licence confirmation | No licence confirmed in initial review; do not copy |
| nanoMuse | https://github.com/nano-muse/nanoMuse | Cross-platform agent architecture reference | Research-only / conditional | GPL-3.0-or-later; assess distribution and copyleft obligations |
| llama.cpp | https://github.com/ggml-org/llama.cpp | On-device LLM inference | Reuse candidate | MIT; retain notices and review bundled dependencies |
| whisper.cpp | https://github.com/ggml-org/whisper.cpp | Offline speech-to-text | Reuse candidate | MIT; retain notices and review bundled dependencies |
| openWakeWord | https://github.com/dscripka/openWakeWord | Wake-word detection | Reuse candidate | Verify licence at pinned revision before integration |
| Piper | https://github.com/rhasspy/piper | Local text-to-speech | Reuse candidate pending verification | Verify current repository status, licence and model licences |

## Required audit fields for every pinned dependency

- Exact upstream URL and commit SHA
- Date reviewed
- Licence identifier and full licence text location
- Copyright / attribution requirements
- Dependency and model-weight licences
- Security and maintenance review
- AURA adapter path and integration tests
- Approved use: reference-only, internal use, or redistribution

## Rules

- Preserve upstream history; prefer Git submodules over copying whole repositories.
- Do not modify files inside a submodule as if they were AURA-owned code.
- Do not commit model weights, personal data, credentials, signing keys or generated build outputs.
- A licence for source code does not automatically cover model weights, datasets, trademarks or hosted services.
- Mark a dependency blocked if its licence is missing, ambiguous or incompatible with the intended AURA distribution.
