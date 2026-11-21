import test from "node:test";
import assert from "node:assert/strict";
import worker, { __test } from "../src/index.mjs";

class MemoryR2 {
  constructor() { this.items = new Map(); }
  async get(key) {
    const item = this.items.get(key);
    if (!item) return null;
    return {
      customMetadata: { ...item.customMetadata },
      json: async () => structuredClone(item.payload),
    };
  }
  async put(key, value, options = {}) {
    this.items.set(key, {
      payload: JSON.parse(value),
      customMetadata: { ...(options.customMetadata || {}) },
    });
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
  const response = await worker.fetch(new Request("https://weather.example/health"), {});
  assert.equal(response.status, 200);
  assert.equal((await response.json()).status, "ok");
});

test("forecast endpoint rejects invalid coordinates before network access", async () => {
  const response = await worker.fetch(
    new Request("https://weather.example/v1/forecast?lat=95&lon=20"),
    { WEATHER_CACHE: new MemoryR2() },
  );
  assert.equal(response.status, 400);
});

test("forecast endpoint requires the configured shared R2 bucket", async () => {
  const response = await worker.fetch(
    new Request("https://weather.example/v1/forecast?lat=41&lon=29"),
    {},
  );
  assert.equal(response.status, 503);
});

test("forecast endpoint fetches, normalizes, and globally caches a valid response", async () => {
  const originalFetch = globalThis.fetch;
  const bucket = new MemoryR2();
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
    const first = await worker.fetch(request, { WEATHER_CACHE: bucket });
    assert.equal(first.status, 200);
    assert.equal(first.headers.get("X-Royal-Weather-Cache"), "MISS");
    assert.equal((await first.json()).properties.timeseries.length, 36);
    assert.equal(requestedUrl.searchParams.get("lat"), "41.009");
    assert.equal(requestedUrl.searchParams.get("lon"), "28.980");
    assert.match(requestedHeaders.get("User-Agent"), /^ROYALPlusLauncherWeather/);

    const second = await worker.fetch(request, { WEATHER_CACHE: bucket });
    assert.equal(second.headers.get("X-Royal-Weather-Cache"), "HIT");
    assert.equal(calls, 1);
    assert.equal(bucket.items.size, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("conditional revalidation preserves cached data after upstream 304", async () => {
  const originalFetch = globalThis.fetch;
  const bucket = new MemoryR2();
  let calls = 0;
  let conditionalTag;
  globalThis.fetch = async (_url, options) => {
    calls += 1;
    conditionalTag = new Headers(options.headers).get("If-None-Match");
    if (calls === 1) {
      return new Response(JSON.stringify(makeForecast()), {
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "Cache-Control": "public, max-age=0",
          ETag: '"weather-v2"',
        },
      });
    }
    return new Response(null, {
      status: 304,
      headers: { "Cache-Control": "public, max-age=21600" },
    });
  };

  try {
    const request = new Request("https://weather.example/v1/forecast?lat=41&lon=29");
    await worker.fetch(request, { WEATHER_CACHE: bucket });
    const response = await worker.fetch(request, { WEATHER_CACHE: bucket });
    assert.equal(conditionalTag, '"weather-v2"');
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "REVALIDATED");
    assert.ok((await response.json()).properties.timeseries.length > 0);
    assert.equal(calls, 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("stale forecast is returned when the upstream is temporarily unavailable", async () => {
  const originalFetch = globalThis.fetch;
  const bucket = new MemoryR2();
  const key = __test.cacheKeyFor(41.0086, 28.9802);
  await bucket.put(key, JSON.stringify(makeForecast()), {
    customMetadata: {
      expiresAt: String(Date.now() - 60_000),
      fetchedAt: String(Date.now() - 7 * 60 * 60 * 1000),
      etag: '"weather-old"',
      lastModified: "",
    },
  });
  globalThis.fetch = async () => { throw new Error("simulated outage"); };

  try {
    const response = await worker.fetch(
      new Request("https://weather.example/v1/forecast?lat=41.0086&lon=28.9802"),
      { WEATHER_CACHE: bucket },
    );
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Royal-Weather-Cache"), "STALE");
    assert.ok((await response.json()).properties.timeseries.length > 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
