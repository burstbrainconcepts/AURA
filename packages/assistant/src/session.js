const DEFAULT_SYSTEM_PROMPT = "You are AURA, a helpful private assistant. Be clear, practical and honest. Never claim to have performed an action unless a connected tool confirms it.";
const MAX_HISTORY_MESSAGES = 40;

export class AssistantSession {
  constructor({ inference, systemPrompt = DEFAULT_SYSTEM_PROMPT, maxHistoryMessages = MAX_HISTORY_MESSAGES } = {}) {
    if (!inference || typeof inference.chat !== "function") {
      throw new TypeError("An inference adapter with a chat() method is required");
    }
    if (!Number.isInteger(maxHistoryMessages) || maxHistoryMessages < 2) {
      throw new RangeError("maxHistoryMessages must be an integer of at least 2");
    }
    this.inference = inference;
    this.systemPrompt = systemPrompt;
    this.maxHistoryMessages = maxHistoryMessages;
    this.messages = [];
    this.pending = false;
  }

  getHistory() {
    return this.messages.map((message) => ({ ...message }));
  }

  clear() {
    if (this.pending) throw new Error("Cannot clear a conversation while a response is pending");
    this.messages = [];
  }

  async send(text, options = {}) {
    if (this.pending) throw new Error("A response is already being generated for this conversation");
    if (typeof text !== "string" || !text.trim()) throw new TypeError("Message cannot be empty");
    if (text.length > 32_000) throw new RangeError("Message exceeds 32,000 characters");

    this.pending = true;
    const userMessage = { role: "user", content: text.trim() };
    const context = [
      { role: "system", content: this.systemPrompt },
      ...this.messages,
      userMessage
    ].slice(-(this.maxHistoryMessages + 1));

    try {
      const result = await this.inference.chat({
        messages: context,
        model: options.model,
        temperature: options.temperature,
        maxTokens: options.maxTokens,
        signal: options.signal
      });
      if (!result || typeof result.content !== "string") {
        throw new Error("Inference adapter returned an invalid assistant response");
      }
      this.messages = [...this.messages, userMessage, { role: "assistant", content: result.content }]
        .slice(-this.maxHistoryMessages);
      return { ...result };
    } finally {
      this.pending = false;
    }
  }
}
