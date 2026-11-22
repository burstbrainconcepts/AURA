function assertText(value, name) {
  if (typeof value !== "string" || !value.trim()) throw new TypeError(name + " must be a non-empty string.");
}

/** Adapter boundary: native/mobile apps provide the actual whisper.cpp bridge. */
export class SpeechToTextAdapter {
  #engine;
  constructor(engine) {
    if (!engine || typeof engine.transcribe !== "function") throw new TypeError("A transcription engine is required.");
    this.#engine = engine;
  }
  async transcribe({ audio, language, signal } = {}) {
    if (!(audio instanceof Uint8Array) || audio.byteLength === 0) throw new TypeError("audio must be a non-empty Uint8Array.");
    if (audio.byteLength > 50 * 1024 * 1024) throw new RangeError("Audio exceeds the 50 MiB limit.");
    if (language !== undefined) assertText(language, "language");
    const result = await this.#engine.transcribe({audio,language,signal});
    if (!result || typeof result.text !== "string") throw new Error("Speech engine returned an invalid transcription.");
    return {text:result.text.trim(),language:result.language ?? language ?? null,segments:Array.isArray(result.segments)?result.segments:[]};
  }
}

/** Text-to-speech adapter contract; a platform engine owns audio rendering. */
export class TextToSpeechAdapter {
  #engine;
  constructor(engine) {
    if (!engine || typeof engine.synthesize !== "function") throw new TypeError("A synthesis engine is required.");
    this.#engine = engine;
  }
  async synthesize({text,voice,signal} = {}) {
    assertText(text,"text");
    if (text.length > 12000) throw new RangeError("Text exceeds the 12,000 character limit.");
    const result = await this.#engine.synthesize({text,voice,signal});
    if (!(result instanceof Uint8Array) || result.byteLength === 0) throw new Error("Speech engine returned invalid audio.");
    return result;
  }
}

/** Wake-word detection is intentionally local and returns events, not background recording controls. */
export class WakeWordAdapter {
  #engine;
  constructor(engine) {
    if (!engine || typeof engine.start !== "function" || typeof engine.stop !== "function") throw new TypeError("A wake-word engine is required.");
    this.#engine = engine;
  }
  start(onWake) {
    if (typeof onWake !== "function") throw new TypeError("onWake callback is required.");
    return this.#engine.start(onWake);
  }
  stop() { return this.#engine.stop(); }
}
