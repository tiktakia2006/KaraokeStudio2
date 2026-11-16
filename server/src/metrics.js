const BUCKETS = [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000];

const escapeLabel = (value) => String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n');
const labels = (map) =>
  Object.entries(map)
    .map(([key, value]) => `${key}="${escapeLabel(value)}"`)
    .join(',');

export class Metrics {
  constructor(now = () => Date.now()) {
    this.now = now;
    this.startedAt = now();
    this.requests = new Map();
    this.durations = new Map();
    this.events = new Map();
  }

  observe({ method, route, status, ms }) {
    const requestKey = `${method}|${route}|${status}`;
    this.requests.set(requestKey, (this.requests.get(requestKey) ?? 0) + 1);
    const durationKey = `${method}|${route}`;
    let histogram = this.durations.get(durationKey);
    if (!histogram) {
      histogram = { buckets: new Array(BUCKETS.length).fill(0), sum: 0, count: 0 };
      this.durations.set(durationKey, histogram);
    }
    BUCKETS.forEach((limit, index) => {
      if (ms <= limit) histogram.buckets[index] += 1;
    });
    histogram.sum += ms;
    histogram.count += 1;
  }

  count(event) {
    this.events.set(event, (this.events.get(event) ?? 0) + 1);
  }

  render(gauges = {}) {
    const lines = [];
    lines.push('# HELP karaoke_http_requests_total Кількість оброблених запитів');
    lines.push('# TYPE karaoke_http_requests_total counter');
    for (const [key, value] of [...this.requests].sort()) {
      const [method, route, status] = key.split('|');
      lines.push(`karaoke_http_requests_total{${labels({ method, route, status })}} ${value}`);
    }
    lines.push('# HELP karaoke_http_request_duration_ms Тривалість обробки запиту, мс');
    lines.push('# TYPE karaoke_http_request_duration_ms histogram');
    for (const [key, histogram] of [...this.durations].sort()) {
      const [method, route] = key.split('|');
      BUCKETS.forEach((limit, index) => {
        lines.push(
          `karaoke_http_request_duration_ms_bucket{${labels({ method, route, le: limit })}} ${histogram.buckets[index]}`,
        );
      });
      lines.push(`karaoke_http_request_duration_ms_bucket{${labels({ method, route, le: '+Inf' })}} ${histogram.count}`);
      lines.push(`karaoke_http_request_duration_ms_sum{${labels({ method, route })}} ${histogram.sum}`);
      lines.push(`karaoke_http_request_duration_ms_count{${labels({ method, route })}} ${histogram.count}`);
    }
    lines.push('# HELP karaoke_events_total Службові події');
    lines.push('# TYPE karaoke_events_total counter');
    for (const [event, value] of [...this.events].sort()) {
      lines.push(`karaoke_events_total{${labels({ event })}} ${value}`);
    }
    lines.push('# HELP karaoke_uptime_seconds Час роботи сервера');
    lines.push('# TYPE karaoke_uptime_seconds gauge');
    lines.push(`karaoke_uptime_seconds ${Math.floor((this.now() - this.startedAt) / 1000)}`);
    for (const [name, value] of Object.entries(gauges)) {
      lines.push(`# TYPE karaoke_${name} gauge`);
      lines.push(`karaoke_${name} ${value}`);
    }
    return `${lines.join('\n')}\n`;
  }
}
