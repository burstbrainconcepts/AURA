export function createRateLimiter({ windowMs, maxRequests, maxKeys = 10_000, now = () => Date.now() }) {
  if (!Number.isSafeInteger(windowMs) || windowMs < 1 ||
      !Number.isSafeInteger(maxRequests) || maxRequests < 1 ||
      !Number.isSafeInteger(maxKeys) || maxKeys < 1) {
    throw new TypeError("Rate-limit settings must be positive safe integers.");
  }
  const buckets = new Map();

  return function checkRateLimit(key) {
    if (typeof key !== "string" || !key || key.length > 256) {
      throw new TypeError("Rate-limit key must be a non-empty string of at most 256 characters.");
    }
    const timestamp = now();
    let bucket = buckets.get(key);
    if (!bucket || timestamp >= bucket.resetAt) {
      if (!bucket && buckets.size >= maxKeys) {
        for (const [candidate, value] of buckets) {
          if (timestamp >= value.resetAt) buckets.delete(candidate);
        }
        if (buckets.size >= maxKeys) {
          const oldest = buckets.keys().next().value;
          if (oldest !== undefined) buckets.delete(oldest);
        }
      }
      bucket = { count: 0, resetAt: timestamp + windowMs };
      buckets.set(key, bucket);
    }
    bucket.count += 1;
    const allowed = bucket.count <= maxRequests;
    return Object.freeze({
      allowed,
      remaining: Math.max(0, maxRequests - bucket.count),
      retryAfterMs: allowed ? 0 : Math.max(1, bucket.resetAt - timestamp)
    });
  };
}
