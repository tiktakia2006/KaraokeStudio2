const LEVELS = { debug: 10, info: 20, warn: 30, error: 40, silent: 100 };

export class LogBuffer {
  constructor(capacity = 500) {
    this.capacity = capacity;
    this.items = [];
  }

  push(entry) {
    this.items.push(entry);
    if (this.items.length > this.capacity) this.items.splice(0, this.items.length - this.capacity);
  }

  recent({ limit = 100, level = 'debug' } = {}) {
    const threshold = LEVELS[level] ?? LEVELS.debug;
    return this.items.filter((entry) => (LEVELS[entry.level] ?? 0) >= threshold).slice(-limit).reverse();
  }
}

export function createLogger(level = 'info', sink = (line) => process.stdout.write(`${line}\n`), buffer = null) {
  const threshold = LEVELS[level] ?? LEVELS.info;
  const write = (name, message, fields) => {
    if (LEVELS[name] < threshold) return;
    const entry = { time: new Date().toISOString(), level: name, message, ...fields };
    if (buffer) buffer.push(entry);
    sink(JSON.stringify(entry));
  };
  return {
    debug: (message, fields) => write('debug', message, fields),
    info: (message, fields) => write('info', message, fields),
    warn: (message, fields) => write('warn', message, fields),
    error: (message, fields) => write('error', message, fields),
  };
}
