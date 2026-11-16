export class UpstreamThrottle {
  constructor({ max, windowMs = 60_000, now = () => Date.now() }) {
    this.max = max;
    this.windowMs = windowMs;
    this.now = now;
    this.hits = [];
  }

  take() {
    const current = this.now();
    this.hits = this.hits.filter((time) => current - time < this.windowMs);
    if (this.hits.length >= this.max) {
      const retryAfterSeconds = Math.max(1, Math.ceil((this.hits[0] + this.windowMs - current) / 1000));
      return { allowed: false, retryAfterSeconds };
    }
    this.hits.push(current);
    return { allowed: true, retryAfterSeconds: 0 };
  }
}
