import test from "node:test";
import assert from "node:assert/strict";
import { createRateLimiter } from "../src/rate-limit.js";

test("rate limiter allows the configured request count and blocks excess requests", () => {
  let now = 1_000;
  const limit = createRateLimiter({ windowMs: 60_000, maxRequests: 2, now: () => now });
  assert.deepEqual(limit("client-a"), { allowed: true, remaining: 1, retryAfterMs: 0 });
  assert.deepEqual(limit("client-a"), { allowed: true, remaining: 0, retryAfterMs: 0 });
  assert.deepEqual(limit("client-a"), { allowed: false, remaining: 0, retryAfterMs: 60_000 });
  now += 60_000;
  assert.deepEqual(limit("client-a"), { allowed: true, remaining: 1, retryAfterMs: 0 });
});

test("rate limits are isolated by key", () => {
  const limit = createRateLimiter({ windowMs: 1_000, maxRequests: 1 });
  assert.equal(limit("one").allowed, true);
  assert.equal(limit("two").allowed, true);
  assert.equal(limit("one").allowed, false);
});

test("invalid configuration and keys are rejected", () => {
  assert.throws(() => createRateLimiter({ windowMs: 0, maxRequests: 1 }), TypeError);
  const limit = createRateLimiter({ windowMs: 1_000, maxRequests: 1 });
  assert.throws(() => limit(""), TypeError);
});
