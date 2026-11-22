import test from "node:test";
import assert from "node:assert/strict";
import { LlamaCppClient } from "../src/index.js";

function fakeFetch(handler) {
  return async (url, options) => handler(url, options);
}

test("chat sends OpenAI-compatible request to local llama.cpp server", async () => {
  let request;
  const client = new LlamaCppClient({
    fetchImpl: fakeFetch(async (url, options) => {
      request = { url, options, body: JSON.parse(options.body) };
      return Response.json({ model: "tiny-local", choices: [{ message: { content: "Hello from AURA" } }] });
    })
  });
  const answer = await client.chat({ messages: [{ role: "user", content: "Hello" }] });
  assert.equal(answer.content, "Hello from AURA");
  assert.equal(request.url, "http://127.0.0.1:8080/v1/chat/completions");
  assert.equal(request.body.messages[0].content, "Hello");
  assert.equal(request.body.stream, false);
});

test("chat rejects malformed input before network access", async () => {
  let called = false;
  const client = new LlamaCppClient({ fetchImpl: fakeFetch(async () => { called = true; }) });
  await assert.rejects(client.chat({ messages: [{ role: "system", content: "x" }, { role: "tool", content: "bad" }] }), /supported role/);
  await assert.rejects(client.chat({ messages: [{ role: "user", content: "x" }], temperature: 4 }), /temperature/);
  assert.equal(called, false);
});

test("health reports unavailable engine without throwing", async () => {
  const client = new LlamaCppClient({ fetchImpl: fakeFetch(async () => new Response("offline", { status: 503 })) });
  const result = await client.health();
  assert.equal(result.online, false);
  assert.match(result.error, /503/);
});

test("API key is sent as a bearer token, not embedded in URL", async () => {
  let headers;
  const client = new LlamaCppClient({
    apiKey: "local-secret",
    fetchImpl: fakeFetch(async (_url, options) => {
      headers = options.headers;
      return Response.json({ data: [] });
    })
  });
  await client.models();
  assert.equal(headers.get("authorization"), "Bearer local-secret");
});

test("rejects credentials embedded in endpoint URL", () => {
  assert.throws(() => new LlamaCppClient({ baseUrl: "http://user:pass@127.0.0.1:8080" }), /credentials/);
});
