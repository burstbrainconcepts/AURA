# Local inference setup for AURA desktop and iOS

The desktop and iOS assistant clients use the OpenAI-compatible HTTP API exposed by llama.cpp `llama-server`. The model server is a separate local process; AURA does not currently bundle model weights or the llama.cpp server binary into these application builds.

## Start the model server

Build or install llama.cpp for the computer that will host inference, then start a GGUF model:

```sh
llama-server -m /path/to/model.gguf --host 127.0.0.1 --port 8080 -c 4096
```

The desktop client defaults to `http://127.0.0.1:8080`. It uses a native Rust HTTP bridge and deliberately restricts its inference target to loopback addresses.

## Connect an iPhone to a computer-hosted model

1. Put the iPhone and computer on the same trusted Wi-Fi network.
2. Find the computer's private LAN IP address.
3. Start llama-server bound to the computer's LAN interface, for example:

   ```sh
   llama-server -m /path/to/model.gguf --host 0.0.0.0 --port 8080 -c 4096 --api-key YOUR_LONG_RANDOM_KEY
   ```

4. Allow inbound TCP port 8080 only on the trusted private network in the computer firewall.
5. In AURA iOS Settings, enter `http://COMPUTER-LAN-IP:8080`, enter the API key, and tap **Test local connection**.
6. Return to Assistant and start a conversation.

The iOS app requests Local Network access for this user-initiated connection. The API key field is held in app memory and is not written to preferences. Use a strong random key whenever the server is reachable from other devices on the LAN. Do not expose an unauthenticated llama-server directly to the public internet.

## Privacy and boundaries

- The desktop native bridge only accepts localhost inference URLs.
- The iOS client can connect to a local service on the phone or a computer on the same network.
- A request leaves the device only to the exact inference endpoint configured by the user.
- No hosted model provider is configured as a fallback.
- Chat history currently lives in app memory and is not persisted as encrypted AURA memory.
- Voice, offline transcription, encrypted native memory and recovery-service enrollment remain separate integration milestones.
- A successful cloud build and a real-device inference test are required before calling either client production-ready.
