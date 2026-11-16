export class RateLimiter {
  constructor(windowMs, now = () => Date.now()) {
    this.windowMs = windowMs;
    this.now = now;
    this.buckets = new Map();
  }

  hit(key, max) {
    const current = this.now();
    if (this.buckets.size > 10_000) this.sweep(current);
    let bucket = this.buckets.get(key);
    if (!bucket || bucket.resetAt <= current) {
      bucket = { count: 0, resetAt: current + this.windowMs };
      this.buckets.set(key, bucket);
    }
    bucket.count += 1;
    return {
      allowed: bucket.count <= max,
      remaining: Math.max(0, max - bucket.count),
      retryAfterSeconds: Math.max(1, Math.ceil((bucket.resetAt - current) / 1000)),
      limit: max,
    };
  }

  sweep(current) {
    for (const [key, bucket] of this.buckets) {
      if (bucket.resetAt <= current) this.buckets.delete(key);
    }
  }
}
