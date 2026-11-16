export class MonotonicClock {
  constructor(now = () => Date.now(), start = 0) {
    this.now = now;
    this.last = start;
  }

  stamp() {
    this.last = Math.max(this.now(), this.last + 1);
    return this.last;
  }
}

export function readMaxStamp(db) {
  const row = db
    .prepare(
      `SELECT MAX(value) AS value FROM (
        SELECT MAX(updated_at) AS value FROM songs
        UNION ALL SELECT MAX(updated_at) FROM favorites
        UNION ALL SELECT MAX(updated_at) FROM performances
      )`,
    )
    .get();
  return row?.value ?? 0;
}
