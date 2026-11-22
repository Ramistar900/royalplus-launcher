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

function makeOpenMeteoForecast(days = 7) {
  const time = [];
  const max = [];
  const min = [];
  for (let day = 0; day < days; day += 1) {
    const date = new Date();
    date.setUTCHours(0, 0, 0, 0);
    date.setUTCDate(date.getUTCDate() + day);
    time.push(date.toISOString().slice(0, 10));
    max.push(18);
    min.push(10);
  }
  return { latitude: 41, longitude: 29, daily: { time, temperature_2m_max: max, temperature_2m_min: min } };
}

function makeMetNorwayForecast(days = 9) {
  const start = new Date();
  start.setUTCHours(0, 0, 0, 0);
  const timeseries = [];
  for (let day = 0; day < days; day += 1) {
    for (const hour of [0, 6, 12, 18]) {
      const time = new Date(start.getTime() + (day * 24 + hour) * 3600000);
      timeseries.push({ time: time.toISOString().replace(".000Z", "Z"), data: { instant: { details: { air_temperature: 12 } } } });
    }
  }
  return { properties: { timeseries } };
}

function quotaBinding(allowed = true, unavailable = false) {
  return {
    idFromName: () => "open-meteo-global",
    get: () => ({
      fetch: async () => Response.json({ allowed, unavailable }),
    }),
  };
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

test("both providers must return at least seven forecast days", () => {
  assert.equal(__test.hasUsableForecast(makeOpenMeteoForecast(7), "Open-Meteo"), true);
  assert.equal(__test.hasUsableForecast(makeOpenMeteoForecast(6), "Open-Meteo"), false);
  assert.equal(__test.hasUsableForecast(makeMetNorwayForecast(9), "MET Norway"), true);
  assert.equal(__test.hasUsableForecast(makeMetNorwayForecast(6), "MET Norway"), false);
});

test("MET Norway polling interval is at least three hours and default cache is six hours", () => {
  const now = 1_000_000;
  assert.equal(__test.upstreamExpiry(new Headers({ "Cache-Control": "public, max-age=3600" }), now, "MET Norway"), now + 3 * 60 * 60 * 1000);
  assert.equal(__test.upstreamExpiry(new Headers({ "Cache-Control": "public, max-age=21600" }), now, "MET Norway"), now + 6 * 60 * 60 * 1000);
  assert.equal(__test.upstreamExpiry(new Headers(), now, "Open-Meteo"), now + 6 * 60 * 60 * 1000);
});

test("attribution identifies the actual weather provider", () => {
  assert.match(__test.attributionFor("Open-Meteo"), /Open-Meteo/);
  assert.match(__test.attributionFor("MET Norway"), /MET Norway/);
  assert.match(__test.attributionFor("MET Norway"), /CC BY 4.0/);
});

test("health endpoint identifies primary and fallback without contacting providers", async () => {
  const response = await worker.fetch(new Request("https://weather.example/health"), {});
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.primary, "Open-Meteo");
  assert.equal(body.fallback, "MET Norway");
});

test("forecast endpoint stops before network access when KV is not configured", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls += 1; throw new Error("must not be called"); };
  try {
    const response = await worker.fetch(requestFor(), {});
    assert.equal(response.status, 503);
    assert.equal(calls, 0);
  } finally { globalThis.fetch = originalFetch; }
});

test("forecast endpoint rejects invalid coordinates before network access", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls += 1; throw new Error("must not be called"); };
  try {
    const response = await worker.fetch(requestFor("95", "20"), { WEATHER_CACHE: new MemoryKV() });
    assert.equal(response.status, 400);
    assert.equal(calls, 0);
  } finally { globalThis.fetch = originalFetch; }
});

test("Open-Meteo is the primary source and its seven-day forecast is cached", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  let calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url: new URL(url), headers: new Headers(options.headers) });
    return new Response(JSON.stringify(makeOpenMeteoForecast()), {
      status: 200,
      headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=21600", ETag: '"open-v1"' },
    });
  };
  try {
    const first = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(first.status, 200);
    assert.equal(first.headers.get("X-Royal-Weather-Cache"), "MISS");
    assert.equal(first.headers.get("X-Royal-Weather-Source"), "Open-Meteo");
    assert.match(first.headers.get("X-Weather-Attribution"), /Open-Meteo/);
    assert.equal((await first.json()).daily.time.length, 7);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url.hostname, "api.open-meteo.com");
    assert.equal(calls[0].url.searchParams.get("latitude"), "41.009");
    assert.equal(calls[0].url.searchParams.get("longitude"), "28.980");
    const second = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(second.headers.get("X-Royal-Weather-Cache"), "HIT");
    assert.equal(calls.length, 1);
    assert.equal(kv.items.size, 1);
  } finally { globalThis.fetch = originalFetch; }
});

test("MET Norway is used only when Open-Meteo fails", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const parsed = new URL(url);
    calls.push(parsed.hostname);
    if (parsed.hostname === "api.open-meteo.com") return new Response("upstream unavailable", { status: 503 });
    assert.match(new Headers(options.headers).get("User-Agent"), /^ROYALPlusLauncherWeather/);
    return new Response(JSON.stringify(makeMetNorwayForecast()), {
      status: 200,
      headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=21600" },
    });
  };
  try {
    const response = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Source"), "MET Norway");
    assert.match(response.headers.get("X-Weather-Attribution"), /CC BY 4.0/);
    assert.deepEqual(calls, ["api.open-meteo.com", "api.met.no"]);
  } finally { globalThis.fetch = originalFetch; }
});

test("Open-Meteo quota exhaustion skips the primary provider and uses the fallback", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  const calls = [];
  globalThis.fetch = async (url) => {
    const host = new URL(url).hostname;
    calls.push(host);
    return new Response(JSON.stringify(makeMetNorwayForecast()), {
      status: 200,
      headers: { "Cache-Control": "public, max-age=21600" },
    });
  };
  try {
    const response = await worker.fetch(requestFor(), {
      WEATHER_CACHE: kv,
      OPEN_METEO_QUOTA: quotaBinding(false, false),
    });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Source"), "MET Norway");
    assert.deepEqual(calls, ["api.met.no"]);
  } finally { globalThis.fetch = originalFetch; }
});

test("incomplete Open-Meteo data triggers the MET Norway fallback", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  const calls = [];
  globalThis.fetch = async (url) => {
    const host = new URL(url).hostname;
    calls.push(host);
    const payload = host === "api.open-meteo.com" ? makeOpenMeteoForecast(5) : makeMetNorwayForecast();
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Cache-Control": "public, max-age=21600" } });
  };
  try {
    const response = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Source"), "MET Norway");
    assert.deepEqual(calls, ["api.open-meteo.com", "api.met.no"]);
  } finally { globalThis.fetch = originalFetch; }
});

test("simultaneous requests for one location share a single primary refresh", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    await new Promise((resolve) => setTimeout(resolve, 20));
    return new Response(JSON.stringify(makeOpenMeteoForecast()), { status: 200, headers: { "Cache-Control": "public, max-age=21600" } });
  };
  try {
    const [first, second] = await Promise.all([
      worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() }),
      worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() }),
    ]);
    const statuses = [first.headers.get("X-Royal-Weather-Cache"), second.headers.get("X-Royal-Weather-Cache")].sort();
    assert.deepEqual(statuses, ["COALESCED", "MISS"]);
    assert.equal(calls, 1);
    assert.equal(kv.items.size, 1);
  } finally { globalThis.fetch = originalFetch; }
});

test("last valid cached forecast is returned when both providers fail", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  const key = __test.cacheKeyFor(41.009, 28.98);
  const now = Date.now();
  kv.items.set(key, JSON.stringify({
    payload: makeOpenMeteoForecast(),
    metadata: { source: "Open-Meteo", expiresAt: now - 60_000, fetchedAt: now - 7 * 60 * 60 * 1000, etag: "", lastModified: "" },
  }));
  globalThis.fetch = async () => new Response("provider down", { status: 503 });
  try {
    const response = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "STALE");
    assert.equal(response.headers.get("X-Royal-Weather-Source"), "Open-Meteo");
    assert.equal((await response.json()).daily.time.length, 7);
  } finally { globalThis.fetch = originalFetch; }
});

test("KV read quota errors fail closed without contacting either provider", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  kv.failReads = true;
  let calls = 0;
  globalThis.fetch = async () => { calls += 1; throw new Error("must not be called"); };
  try {
    const response = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(response.status, 503);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "LIMIT");
    assert.equal(calls, 0);
  } finally { globalThis.fetch = originalFetch; }
});

test("KV write quota errors retain isolate-local cache without requiring paid storage", async () => {
  const originalFetch = globalThis.fetch;
  const kv = new MemoryKV();
  kv.failWrites = true;
  let calls = 0;
  globalThis.fetch = async () => {
    calls += 1;
    return new Response(JSON.stringify(makeOpenMeteoForecast()), { status: 200, headers: { "Cache-Control": "public, max-age=21600" } });
  };
  try {
    const first = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(first.status, 200);
    assert.equal(first.headers.get("X-Royal-Weather-Cache"), "UNCACHED");
    const second = await worker.fetch(requestFor(), { WEATHER_CACHE: kv, OPEN_METEO_QUOTA: quotaBinding() });
    assert.equal(second.headers.get("X-Royal-Weather-Cache"), "LOCAL");
    assert.equal(calls, 1);
  } finally { globalThis.fetch = originalFetch; }
});
