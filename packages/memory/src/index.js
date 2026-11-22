import { randomUUID } from "node:crypto";

const MAX_ITEMS = 1000;
const MAX_TEXT = 8000;

/** In-memory, opt-in memory store. Persistence/encryption is supplied by a platform adapter. */
export class MemoryStore {
  #items = new Map();
  #enabled;
  #clock;

  constructor({ enabled = false, clock = () => Date.now() } = {}) {
    this.#enabled = Boolean(enabled);
    this.#clock = clock;
  }

  get enabled() { return this.#enabled; }
  setEnabled(enabled) {
    this.#enabled = Boolean(enabled);
    if (!this.#enabled) this.clear();
    return this.#enabled;
  }

  add({ text, category = "general", source = "user", metadata = {} } = {}) {
    if (!this.#enabled) throw new Error("Memory is disabled; enable it explicitly before saving.");
    if (typeof text !== "string" || !text.trim() || text.length > MAX_TEXT) throw new TypeError("Memory text must be 1–8000 characters.");
    if (typeof category !== "string" || category.length > 80) throw new TypeError("Invalid memory category.");
    const item = Object.freeze({
      id: randomUUID(), text: text.trim(), category, source,
      metadata: structuredClone(metadata), createdAt: new Date(this.#clock()).toISOString()
    });
    this.#items.set(item.id, item);
    while (this.#items.size > MAX_ITEMS) this.#items.delete(this.#items.keys().next().value);
    return structuredClone(item);
  }

  list({ category, limit = 100 } = {}) {
    if (!this.#enabled) return [];
    const safeLimit = Math.max(0, Math.min(500, Number.isInteger(limit) ? limit : 100));
    return [...this.#items.values()]
      .filter(item => !category || item.category === category)
      .slice(-safeLimit).reverse().map(item => structuredClone(item));
  }

  delete(id) { return this.#items.delete(id); }
  clear() { const count = this.#items.size; this.#items.clear(); return count; }
  export() { return { version: 1, exportedAt: new Date(this.#clock()).toISOString(), items: this.list({limit:500}) }; }
  import(snapshot) {
    if (!this.#enabled) throw new Error("Enable memory before importing.");
    if (!snapshot || snapshot.version !== 1 || !Array.isArray(snapshot.items) || snapshot.items.length > MAX_ITEMS) throw new TypeError("Invalid memory export.");
    for (const item of snapshot.items) this.add({text:item.text,category:item.category,source:item.source,metadata:item.metadata});
    return snapshot.items.length;
  }
}
