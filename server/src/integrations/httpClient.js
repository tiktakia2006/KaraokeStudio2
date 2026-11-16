export class UpstreamError extends Error {
  constructor(message, status, retriable = true) {
    super(message);
    this.status = status;
    this.retriable = retriable;
  }
}

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function fetchJson(url, { timeoutMs, retries, fetchImpl = fetch, headers = {}, sleep = delay }) {
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetchImpl(url, {
        headers: { Accept: 'application/json', 'User-Agent': 'KaraokeStudio/1.0', ...headers },
        signal: controller.signal,
      });
      if (response.status === 404) return { status: 404, data: null };
      if (response.status >= 500) throw new UpstreamError(`Сервіс відповів ${response.status}`, response.status);
      if (!response.ok) throw new UpstreamError(`Сервіс відхилив запит: ${response.status}`, response.status, false);
      return { status: response.status, data: await response.json() };
    } catch (error) {
      lastError = error;
      if (error instanceof UpstreamError && !error.retriable) break;
    } finally {
      clearTimeout(timer);
    }
    if (attempt < retries) await sleep(150 * (attempt + 1));
  }
  if (lastError instanceof UpstreamError) throw lastError;
  const reason = lastError?.name === 'AbortError' ? 'перевищено час очікування' : lastError?.message;
  throw new UpstreamError(`Сервіс недоступний: ${reason}`);
}
