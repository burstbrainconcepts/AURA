# AURA local inference runtime

The first adapter targets the local `llama.cpp` HTTP server. It uses the server's OpenAI-compatible chat-completions endpoint, so the same AURA interface can later be backed by a mobile-native llama.cpp binding without changing assistant orchestration.

## Start the engine

Build llama.cpp with its server target and start it with a downloaded GGUF model. For example:

```sh
./llama-server -m ./models/model.gguf --host 127.0.0.1 --port 8080 -c 4096
```

Keep the server bound to loopback for local desktop development. Do not expose an unauthenticated inference server to the public internet.

## Use the adapter

```js
import { LlamaCppClient } from "@aura/runtime";

const llm = new LlamaCppClient({ baseUrl: "http://127.0.0.1:8080" });
const result = await llm.chat({
  messages: [{ role: "user", content: "Hello AURA" }]
});
console.log(result.content);
```

The adapter also exposes `health()` and `models()`. It validates roles, message count, message size, temperature and output token limits before sending requests. API keys are passed as bearer headers and must not be placed in URLs.

## Current limitation

This is the first desktop/local-server bridge. Android and iOS need native bindings or an embedded engine process; they cannot assume a desktop localhost server exists. Model download, checksum verification, hardware profiling, streaming UI and native mobile packaging are next integration tasks.
