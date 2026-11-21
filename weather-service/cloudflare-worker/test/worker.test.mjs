import test from "node:test";
import assert from "node:assert/strict";
import worker, { __test } from "../src/index.mjs";

class MemoryKV {
  constructor() { this.items = new Map(); this.failReads = false; this.failWrites = false; }
  async get(key, type) {
    if (this.failReads) throw new Error("KV daily read limit reached");
    const value = this.items.get(key);
    if (value === undefined) return null;
    return type === "json" ? structuredClone(JSON.parse(value)) : value;
  }
  async put(key, value, options = {}) {
    if (this.failWrites) throw new Error("KV daily write limit reached");
    this.items.set(key, value);
    this.lastOptions = options;
  }
}

function makeForecast(days = 9) {
  const start = new Date();
  start.setUTCHours(0, 0, 0, 0);
  const timeseries = [];
  for (let day = 0; day < days; day += 1) {
    for (const hour of [0, 6, 12, 18]) {
      const time = new Date(start.getTime() + (day * 24 + hour) * 3600000);
      timeseries.push({
        time: time.toISOString().replace(".000Z", "Z"),
        data: { instant: { details: { air_temperature: 12 } } },
      });
    }
  }
  return { properties: { timeseries } };
}

function requestFor(lat = "41.008612", lon = "28.980175") {
  return new Request(`https://weather.example/v1/forecast?lat=${lat}&lon=${lon}`);
}

test.beforeEach(() => __test.clearLocalCache());

test("coordinates are validated and rounded to three decimals", () => {
  assert.equal(__test.normalizeCoordinate("41.008612", -90, 90), 41.009);
  assert.equal(__test.normalizeCoordinate("91", -90, 90), null);
  assert.equal(__test.normalizeCoordinate("", -90, 90), null);
  assert.equal(__test.normalizeCoordinate("not-a-number", -90, 90), null);
});

test("only forecasts covering at least seven distinct days are accepted", () => {
  assert.equal(__test.hasUsableForecast(makeForecast(9)), true);
  assert.equal(__test.hasUsableForecast(makeForecast(6)), false);
  assert.equal(__test.hasUsableForecast({ properties: { timeseries: [] } }), false);
});

test("upstream cache headers are respected without polling more often than three hours", () => {
  const now = 1_000_000;
  assert.equal(
    __test.upstreamExpiry(new Headers({ "Cache-Control": "public, max-age=3600" }), now),
    now + 3 * 60 * 60 * 1000,
  );
  assert.equal(
    __test.upstreamExpiry(new Headers({ "Cache-Control": "public, max-age=21600" }), now),
    now + 6 * 60 * 60 * 1000,
  );
  assert.equal(
    __test.upstreamExpiry(new Headers(), now),
    now + 6 * 60 * 60 * 1000,
  );
});

test("health endpoint does not contact the upstream", async () => {
  const response = await worker.fetch(new Request("https://weather.example/health"), {});
  assert.equal(response.status, 200);
  assert.equal((await response.json()).cache, "Cloudflare KV Free");
});

test("forecast endpoint stops before network access when KV is not configured", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls += 1; throw new Error("must not be called"); };
  try {
    const response = await worker.fetch(requestFor(), {});
    assert.equal(response.status, 503);
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("forecast endpoint rejects invalid coordinates before network access", async () => {
  const response = await worker.fetch(requestFor("95", "20"), { WEATHER_CACHE: new MemoryKV() });
  assert.equal(response.status, 400);
});

test("forecast endpoint fetches, normalizes, and caches a valid response in KV", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  let calls = 0;
  let requestedUrl;
  let requestedHeaders;
  globalThis.fetch = async (url, options) => {
    calls += 1;
    requestedUrl = new URL(url);
    requestedHeaders = new Headers(options.headers);
    return new Response(JSON.stringify(makeForecast()), {
      status: 200,
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "public, max-age=21600",
        ETag: '"weather-v1"',
      },
    });
  };

  try {
    const first = await worker.fetch(requestFor(), { WEATHER_CACHE: kv });
    assert.equal(first.status, 200);
    assert.equal(first.headers.get("X-Royal-Weather-Cache"), "MISS");
    assert.equal((await first.json()).properties.timeseries.length, 36);
    assert.equal(requestedUrl.searchParams.get("lat"), "41.009");
    assert.equal(requestedUrl.searchParams.get("lon"), "28.980");
    assert.match(requestedHeaders.get("User-Agent"), /^ROYALPlusLauncherWeather/);
    assert.ok(kv.lastOptions.expirationTtl >= 48 * 60 * 60);
    const second = await worker.fetch(requestFor(), { WEATHER_CACHE: kv });
    assert.equal(second.headers.get("X-Royal-Weather-Cache"), "HIT");
    assert.equal(calls, 1);
    assert.equal(kv.items.size, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("conditional revalidation preserves cached data after upstream 304", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  const key = __test.cacheKeyFor(41.009, 28.98);
  const now = Date.now();
  kv.items.set(key, JSON.stringify({
    payload: makeForecast(),
    metadata: {
      expiresAt: now - 60_000,
      fetchedAt: now - 7 * 60 * 60 * 1000,
      etag: '"weather-v2"',
      lastModified: "",
    },
  }));
  let calls = 0;
  let conditionalTag;
  globalThis.fetch = async (_url, options) => {
    calls += 1;
    conditionalTag = new Headers(options.headers).get("If-None-Match");
    return new Response(null, {
      status: 304,
      headers: { "Cache-Control": "public, max-age=21600" },
    });
  };

  try {
    const response = await worker.fetch(requestFor(), { WEATHER_CACHE: kv });
    assert.equal(conditionalTag, '"weather-v2"');
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "REVALIDATED");
    assert.ok((await response.json()).properties.timeseries.length > 0);
    assert.equal(calls, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("simultaneous requests for one location share a single upstream refresh", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    await new Promise((resolve) => setTimeout(resolve, 20));
    return new Response(JSON.stringify(makeForecast()), {
      status: 200,
      headers: { "Cache-Control": "public, max-age=21600" },
    });
  };

  try {
    const [first, second] = await Promise.all([
      worker.fetch(requestFor(), { WEATHER_CACHE: kv }),
      worker.fetch(requestFor(), { WEATHER_CACHE: kv }),
    ]);
    const statuses = [
      first.headers.get("X-Royal-Weather-Cache"),
      second.headers.get("X-Royal-Weather-Cache"),
    ].sort();
    assert.deepEqual(statuses, ["COALESCED", "MISS"]);
    assert.equal(calls, 1);
    assert.equal(kv.items.size, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("last valid forecast is returned when upstream is unavailable", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  const key = __test.cacheKeyFor(41.009, 28.98);
  const now = Date.now();
  kv.items.set(key, JSON.stringify({
    payload: makeForecast(),
    metadata: {
      expiresAt: now - 60_000,
      fetchedAt: now - 7 * 60 * 60 * 1000,
      etag: '"weather-old"',
      lastModified: "",
    },
  }));
  globalThis.fetch = async () => { throw new Error("simulated outage"); };

  try {
    const response = await worker.fetch(requestFor(), { WEATHER_CACHE: kv });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "STALE");
    assert.ok((await response.json()).properties.timeseries.length > 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("KV quota errors fail closed without bypassing the shared cache", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  kv.failReads = true;
  let calls = 0;
  globalThis.fetch = async () => { calls += 1; throw new Error("must not be called"); };
  try {
    const response = await worker.fetch(requestFor(), { WEATHER_CACHE: kv });
    assert.equal(response.status, 503);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "LIMIT");
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("KV write quota errors keep an isolate-local fallback and never require paid storage", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  kv.failWrites = true;
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    return new Response(JSON.stringify(makeForecast()), {
      status: 200,
      headers: { "Cache-Control": "public, max-age=21600" },
    });
  };
  try {
    const first = await worker.fetch(requestFor(), { WEATHER_CACHE: kv });
    assert.equal(first.status, 200);
    assert.equal(first.headers.get("X-Royal-Weather-Cache"), "UNCACHED");
    const second = await worker.fetch(requestFor(), { WEATHER_CACHE: kv });
    assert.equal(second.headers.get("X-Royal-Weather-Cache"), "LOCAL");
    assert.equal(calls, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
