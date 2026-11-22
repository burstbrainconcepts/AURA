import { LlamaCppClient } from "@aura/runtime";
import { AssistantSession } from "./session.js";

/** Compose the AURA conversation manager with a local llama.cpp server. */
export function createLocalAssistantSession(options = {}) {
  const { inference = {}, ...sessionOptions } = options;
  const client = new LlamaCppClient(inference);
  const session = new AssistantSession({ ...sessionOptions, inference: client });
  return Object.freeze({ session, engine: client });
}
