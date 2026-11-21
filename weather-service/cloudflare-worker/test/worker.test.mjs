import test from "node:test";
import assert from "node:assert/strict";
import worker, { __test } from "../src/index.mjs";

class MemoryCache {
  constructor() { this.items = new Map(); }
  async match(key) {
    const value = this.items.get(key.url);
    return value ? value.clone() : undefined;
  }
  async put(key, value) {
    this.items.set(key.url, value.clone());
  }
}

function makeForecast(days = 9) {
  const start = new Date("2026-11-21T00:00:00Z");
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

test("coordinates are validated and rounded to four decimals", () => {
  assert.equal(__test.normalizeCoordinate("41.008612", -90, 90), 41.0086);
  assert.equal(__test.normalizeCoordinate("91", -90, 90), null);
  assert.equal(__test.normalizeCoordinate("", -90, 90), null);
  assert.equal(__test.normalizeCoordinate("not-a-number", -90, 90), null);
});

test("only forecasts covering at least seven distinct days are accepted", () => {
  assert.equal(__test.hasUsableForecast(makeForecast(9)), true);
  assert.equal(__test.hasUsableForecast(makeForecast(6)), false);
  assert.equal(__test.hasUsableForecast({ properties: { timeseries: [] } }), false);
});

test("upstream cache headers are respected, with a six-hour fallback", () => {
  const now = 1_000_000;
  assert.equal(
    __test.upstreamExpiry(new Headers({ "Cache-Control": "public, max-age=3600" }), now),
    now + 3_600_000,
  );
  assert.equal(
    __test.upstreamExpiry(new Headers(), now),
    now + 6 * 60 * 60 * 1000,
  );
});

test("health endpoint does not contact the upstream", async () => {
  const response = await worker.fetch(new Request("https://weather.example/health"));
  assert.equal(response.status, 200);
  assert.equal((await response.json()).status, "ok");
});

test("forecast endpoint rejects invalid coordinates before network access", async () => {
  const response = await worker.fetch(new Request("https://weather.example/v1/forecast?lat=95&lon=20"));
  assert.equal(response.status, 400);
});

test("forecast endpoint fetches, normalizes, and caches a valid upstream response", async () => {
  const originalCaches = globalThis.caches;
  const originalFetch = globalThis.fetch;
  const cache = new MemoryCache();
  globalThis.caches = { default: cache };
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
    const request = new Request("https://weather.example/v1/forecast?lat=41.008612&lon=28.980175");
    const first = await worker.fetch(request);
    assert.equal(first.status, 200);
    assert.equal(first.headers.get("X-Royal-Weather-Cache"), "MISS");
    assert.equal((await first.json()).properties.timeseries.length, 36);
    assert.equal(requestedUrl.searchParams.get("lat"), "41.0086");
    assert.equal(requestedUrl.searchParams.get("lon"), "28.9802");
    assert.match(requestedHeaders.get("User-Agent"), /^ROYALPlusLauncherWeather/);

    const second = await worker.fetch(request);
    assert.equal(second.headers.get("X-Royal-Weather-Cache"), "HIT");
    assert.equal(calls, 1);
  } finally {
    globalThis.caches = originalCaches;
    globalThis.fetch = originalFetch;
  }
});

test("stale forecast is returned when the upstream is temporarily unavailable", async () => {
  const originalCaches = globalThis.caches;
  const originalFetch = globalThis.fetch;
  const cache = new MemoryCache();
  globalThis.caches = { default: cache };
  const key = __test.cacheKeyFor(41.0086, 28.9802);
  const stale = new Response(JSON.stringify(makeForecast()), {
    headers: {
      "Content-Type": "application/json",
      "X-Royal-Weather-Expires-At": String(Date.now() - 60_000),
      "X-Royal-Weather-Fetched-At": String(Date.now() - 7 * 60 * 60 * 1000),
      "Cache-Control": "public, max-age=172800",
    },
  });
  await cache.put(key, stale);
  globalThis.fetch = async () => { throw new Error("simulated outage"); };

  try {
    const response = await worker.fetch(new Request("https://weather.example/v1/forecast?lat=41.0086&lon=28.9802"));
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "STALE");
    assert.ok((await response.json()).properties.timeseries.length > 0);
  } finally {
    globalThis.caches = originalCaches;
    globalThis.fetch = originalFetch;
  }
});
