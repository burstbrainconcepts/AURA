const DEFAULT_TIMEOUT_MS = 120_000;
const MAX_MESSAGES = 80;
const MAX_CONTENT_LENGTH = 32_000;

function normalizeBaseUrl(value) {
  const url = new URL(value);
  if (!["http:", "https:"].includes(url.protocol)) throw new TypeError("Inference URL must use HTTP or HTTPS");
  if (url.username || url.password) throw new TypeError("Do not place credentials in the inference URL");
  return url.toString().replace(/\/$/, "");
}

export class LlamaCppClient {
  constructor({ baseUrl = "http://127.0.0.1:8080", apiKey, timeoutMs = DEFAULT_TIMEOUT_MS, fetchImpl = globalThis.fetch } = {}) {
    if (typeof fetchImpl !== "function") throw new TypeError("A Fetch-compatible implementation is required");
    this.baseUrl = normalizeBaseUrl(baseUrl);
    this.apiKey = apiKey;
    this.timeoutMs = timeoutMs;
    this.fetch = fetchImpl;
  }

  async request(path, init = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(new Error("Inference request timed out")), this.timeoutMs);
    try {
      const headers = new Headers(init.headers ?? {});
      headers.set("accept", "application/json");
      if (init.body) headers.set("content-type", "application/json");
      if (this.apiKey) headers.set("authorization", `Bearer ${this.apiKey}`);
      const response = await this.fetch(`${this.baseUrl}${path}`, {
        ...init, headers, signal: controller.signal
      });
      if (!response.ok) {
        const detail = (await response.text()).slice(0, 500);
        throw new Error(`Local inference failed (${response.status}): ${detail || response.statusText}`);
      }
      return response.status === 204 ? null : response.json();
    } finally {
      clearTimeout(timeout);
    }
  }

  async health() {
    try {
      await this.request("/health");
      return { online: true, engine: "llama.cpp" };
    } catch (error) {
      return { online: false, engine: "llama.cpp", error: error.message };
    }
  }

  async models() {
    const result = await this.request("/v1/models");
    return Array.isArray(result?.data) ? result.data : [];
  }

  async chat({ messages, model, temperature = 0.7, maxTokens = 512, signal } = {}) {
    if (!Array.isArray(messages) || messages.length === 0 || messages.length > MAX_MESSAGES) {
      throw new TypeError(`messages must contain between 1 and ${MAX_MESSAGES} items`);
    }
    const normalized = messages.map((message) => {
      if (!message || !["system", "user", "assistant"].includes(message.role)) {
        throw new TypeError("Each message must have a supported role");
      }
      if (typeof message.content !== "string" || message.content.length > MAX_CONTENT_LENGTH) {
        throw new TypeError("Message content must be a string no longer than 32,000 characters");
      }
      return { role: message.role, content: message.content };
    });
    if (!Number.isFinite(temperature) || temperature < 0 || temperature > 2) {
      throw new RangeError("temperature must be between 0 and 2");
    }
    if (!Number.isInteger(maxTokens) || maxTokens < 1 || maxTokens > 8192) {
      throw new RangeError("maxTokens must be an integer between 1 and 8192");
    }
    const result = await this.request("/v1/chat/completions", {
      method: "POST",
      signal,
      body: JSON.stringify({
        model: model || "local-model",
        messages: normalized,
        temperature,
        max_tokens: maxTokens,
        stream: false
      })
    });
    const content = result?.choices?.[0]?.message?.content;
    if (typeof content !== "string") throw new Error("Inference engine returned an unexpected response shape");
    return { content, usage: result.usage ?? null, model: result.model ?? model ?? "local-model" };
  }
}
