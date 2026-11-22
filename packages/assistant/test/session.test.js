import test from "node:test";
import assert from "node:assert/strict";
import { AssistantSession } from "../src/index.js";

function fakeInference(reply = "AURA response") {
  const calls = [];
  return { calls, async chat(input) { calls.push(input); return { content: reply, model: "test-model" }; } };
}

test("session sends system prompt and user message to inference", async () => {
  const inference = fakeInference();
  const session = new AssistantSession({ inference, systemPrompt: "You are AURA." });
  const result = await session.send(" Hello ");
  assert.equal(result.content, "AURA response");
  assert.deepEqual(inference.calls[0].messages, [
    { role: "system", content: "You are AURA." },
    { role: "user", content: "Hello" }
  ]);
});

test("session keeps bounded history and returns defensive copies", async () => {
  const session = new AssistantSession({ inference: fakeInference(), maxHistoryMessages: 4 });
  await session.send("one");
  await session.send("two");
  const history = session.getHistory();
  assert.equal(history.length, 4);
  history[0].content = "tampered";
  assert.notEqual(session.getHistory()[0].content, "tampered");
});

test("failed inference does not store a partial user turn", async () => {
  const session = new AssistantSession({ inference: { async chat() { throw new Error("engine down"); } } });
  await assert.rejects(session.send("hello"), /engine down/);
  assert.deepEqual(session.getHistory(), []);
});

test("empty messages are rejected", async () => {
  const session = new AssistantSession({ inference: fakeInference() });
  await assert.rejects(session.send("   "), /cannot be empty/);
});

test("concurrent generation is rejected and pending state resets", async () => {
  let release;
  const session = new AssistantSession({ inference: { chat: () => new Promise((resolve) => { release = resolve; }) } });
  const first = session.send("first");
  await assert.rejects(session.send("second"), /already being generated/);
  release({ content: "done" });
  await first;
  assert.equal(session.getHistory().length, 2);
});
