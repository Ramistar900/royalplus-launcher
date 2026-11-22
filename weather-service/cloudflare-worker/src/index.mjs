const OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast";
const MET_NORWAY_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact";
const USER_AGENT = "ROYALPlusLauncherWeather/1.0 (https://github.com/Ramistar900/royalplus-launcher)";
const DEFAULT_TTL_MS = 6 * 60 * 60 * 1000;
const MET_NORWAY_MIN_INTERVAL_MS = 3 * 60 * 60 * 1000;
const STALE_RETENTION_MS = 48 * 60 * 60 * 1000;
const CLIENT_MAX_AGE_SECONDS = 60 * 60;
const inFlightRefreshes = new Map();
const isolateFallbackCache = new Map();

function jsonResponse(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Access-Control-Expose-Headers": "X-Royal-Weather-Source, X-Weather-Attribution, X-Royal-Weather-Cache",
      "Cache-Control": "no-store",
      ...extraHeaders,
    },
  });
}

function normalizeCoordinate(value, min, max) {
  if (value === null || value.trim() === "") return null;
  const number = Number(value);
  if (!Number.isFinite(number) || number < min || number > max) return null;
  return Number(number.toFixed(3));
}

function cacheKeyFor(latitude, longitude) {
  return `forecast/v2/${latitude.toFixed(3)}_${longitude.toFixed(3)}`;
}

function upstreamExpiry(headers, now, source) {
  const cacheControl = headers.get("Cache-Control") || "";
  const maxAge = cacheControl.match(/(?:^|,)\s*max-age=(\d+)/i);
  const minimum = source === "MET Norway" ? MET_NORWAY_MIN_INTERVAL_MS : 0;
  if (maxAge) return now + Math.max(Number(maxAge[1]) * 1000, minimum);

  const expires = Date.parse(headers.get("Expires") || "");
  if (Number.isFinite(expires) && expires > now) return Math.max(expires, now + minimum);
  return now + Math.max(DEFAULT_TTL_MS, minimum);
}

function hasUsableForecast(payload, source) {
  if (source === "Open-Meteo") {
    const days = payload?.daily?.time;
    return Array.isArray(days) && days.length >= 7;
  }
  const series = payload?.properties?.timeseries;
  if (!Array.isArray(series) || series.length === 0) return false;
  const days = new Set(series.map((item) => String(item?.time || "").slice(0, 10)).filter(Boolean));
  return days.size >= 7;
}

function attributionFor(source) {
  return source === "Open-Meteo"
    ? "Weather data by Open-Meteo (https://open-meteo.com/), licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)."
    : "Weather data by MET Norway (https://api.met.no/), licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).";
}

function responseFor(entry, cacheStatus, stale = false) {
  const { payload, metadata } = entry;
  const remainingMs = Math.max(0, Number(metadata.expiresAt) - Date.now());
  const maxAge = stale ? 300 : Math.min(CLIENT_MAX_AGE_SECONDS, Math.floor(remainingMs / 1000));
  const headers = {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Cache-Control": `public, max-age=${maxAge}`,
    "X-Royal-Weather-Cache": cacheStatus,
    "X-Royal-Weather-Source": metadata.source,
    "X-Royal-Weather-Expires-At": String(metadata.expiresAt),
    "X-Royal-Weather-Fetched-At": String(metadata.fetchedAt),
    "X-Weather-Attribution": attributionFor(metadata.source),
  };
  if (metadata.etag) headers.ETag = metadata.etag;
  if (metadata.lastModified) headers["Last-Modified"] = metadata.lastModified;
  if (stale) headers.Warning = '110 - "Response is stale; upstream refresh failed"';
  return jsonResponse(payload, 200, headers);
}

async function readStoredForecast(kv, key) {
  try {
    const stored = await kv.get(key, "json");
    if (stored?.payload && stored?.metadata) return { ...stored, cacheSource: "KV" };
    const local = isolateFallbackCache.get(key);
    return local ? { ...local, cacheSource: "ISOLATE" } : null;
  } catch (error) {
    const local = isolateFallbackCache.get(key);
    if (local) return { ...local, cacheSource: "ISOLATE" };
    throw error;
  }
}

async function storeForecast(kv, key, payload, upstreamHeaders, now, source) {
  const metadata = {
    source,
    expiresAt: upstreamExpiry(upstreamHeaders, now, source),
    fetchedAt: now,
    etag: upstreamHeaders.get("ETag") || "",
    lastModified: upstreamHeaders.get("Last-Modified") || "",
  };
  const entry = { payload, metadata };
  isolateFallbackCache.set(key, entry);
  const ttlSeconds = Math.max(60, Math.ceil((metadata.expiresAt - now + STALE_RETENTION_MS) / 1000));
  try {
    await kv.put(key, JSON.stringify(entry), { expirationTtl: ttlSeconds });
    return { ...entry, cacheStatus: "MISS" };
  } catch (_error) {
    return { ...entry, cacheStatus: "UNCACHED" };
  }
}

async function fetchOpenMeteo(latitude, longitude, cached) {
  const url = new URL(OPEN_METEO_URL);
  url.searchParams.set("latitude", latitude.toFixed(3));
  url.searchParams.set("longitude", longitude.toFixed(3));
  url.searchParams.set("forecast_days", "7");
  url.searchParams.set("timezone", "auto");
  url.searchParams.set("current", "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m");
  url.searchParams.set("hourly", "temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m");
  url.searchParams.set("daily", "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max");
  const headers = new Headers({ Accept: "application/json" });
  if (cached?.metadata.source === "Open-Meteo" && cached.metadata.etag) headers.set("If-None-Match", cached.metadata.etag);
  const response = await fetch(url, { method: "GET", headers });
  if (response.status === 304 && cached?.metadata.source === "Open-Meteo") {
    return { notModified: true, response, payload: cached.payload, source: "Open-Meteo" };
  }
  if (!response.ok) throw new Error(`Open-Meteo returned HTTP ${response.status}`);
  const payload = await response.json();
  if (!hasUsableForecast(payload, "Open-Meteo")) throw new Error("Open-Meteo returned an incomplete seven-day forecast.");
  return { response, payload, source: "Open-Meteo" };
}

async function fetchMetNorway(latitude, longitude, cached) {
  const url = new URL(MET_NORWAY_URL);
  url.searchParams.set("lat", latitude.toFixed(3));
  url.searchParams.set("lon", longitude.toFixed(3));
  const headers = new Headers({ Accept: "application/json", "User-Agent": USER_AGENT });
  if (cached?.metadata.source === "MET Norway" && cached.metadata.etag) headers.set("If-None-Match", cached.metadata.etag);
  if (cached?.metadata.source === "MET Norway" && cached.metadata.lastModified) headers.set("If-Modified-Since", cached.metadata.lastModified);
  const response = await fetch(url, { method: "GET", headers });
  if (response.status === 304 && cached?.metadata.source === "MET Norway") {
    return { notModified: true, response, payload: cached.payload, source: "MET Norway" };
  }
  if (!response.ok) throw new Error(`MET Norway returned HTTP ${response.status}`);
  const payload = await response.json();
  if (!hasUsableForecast(payload, "MET Norway")) throw new Error("MET Norway returned an incomplete seven-day forecast.");
  return { response, payload, source: "MET Norway" };
}

async function reserveOpenMeteoQuota(env) {
  if (!env.OPEN_METEO_QUOTA) return { allowed: false, unavailable: true };
  const id = env.OPEN_METEO_QUOTA.idFromName("open-meteo-global");
  const stub = env.OPEN_METEO_QUOTA.get(id);
  const response = await stub.fetch("https://quota.internal/reserve", { method: "POST" });
  if (!response.ok) return { allowed: false, unavailable: true };
  return response.json();
}

async function refreshForecast(kv, key, latitude, longitude, cached, now, env) {
  let primaryError;
  try {
    const quota = await reserveOpenMeteoQuota(env);
    if (!quota.allowed) throw new Error(quota.unavailable ? "Open-Meteo quota guard unavailable; primary request blocked." : "Open-Meteo free quota reached; primary request blocked.");
    const result = await fetchOpenMeteo(latitude, longitude, cached);
    if (result.notModified) {
      const entry = await storeForecast(kv, key, result.payload, result.response.headers, now, result.source);
      return { ...entry, cacheStatus: entry.cacheStatus === "UNCACHED" ? "UNCACHED" : "REVALIDATED" };
    }
    return storeForecast(kv, key, result.payload, result.response.headers, now, result.source);
  } catch (error) {
    primaryError = error;
  }

  try {
    const fallback = await fetchMetNorway(latitude, longitude, cached);
    if (fallback.notModified) {
      const entry = await storeForecast(kv, key, fallback.payload, fallback.response.headers, now, fallback.source);
      return { ...entry, cacheStatus: entry.cacheStatus === "UNCACHED" ? "UNCACHED" : "FALLBACK_REVALIDATED" };
    }
    const entry = await storeForecast(kv, key, fallback.payload, fallback.response.headers, now, fallback.source);
    return { ...entry, cacheStatus: entry.cacheStatus === "UNCACHED" ? "UNCACHED" : "FALLBACK" };
  } catch (_fallbackError) {
    throw new Error(`Both weather providers failed. Primary: ${primaryError?.message || "unknown"}`);
  }
}

async function handleForecast(request, env) {
  const kv = env.WEATHER_CACHE;
  if (!kv || typeof kv.get !== "function" || typeof kv.put !== "function") {
    return jsonResponse({ error: "Free weather cache is not configured. No upstream request was made." }, 503, { "Retry-After": "3600" });
  }
  const url = new URL(request.url);
  const latitude = normalizeCoordinate(url.searchParams.get("lat"), -90, 90);
  const longitude = normalizeCoordinate(url.searchParams.get("lon"), -180, 180);
  if (latitude === null || longitude === null) {
    return jsonResponse({ error: "Provide valid latitude and longitude coordinates." }, 400);
  }

  const key = cacheKeyFor(latitude, longitude);
  let cached;
  try {
    cached = await readStoredForecast(kv, key);
  } catch (_error) {
    return jsonResponse(
      { error: "Free weather cache limit reached or temporarily unavailable. Use the saved device forecast and retry later." },
      503,
      { "Retry-After": "3600", "X-Royal-Weather-Cache": "LIMIT" },
    );
  }

  const now = Date.now();
  const cachedExpiry = Number(cached?.metadata.expiresAt || 0);
  if (cached && cachedExpiry > now) {
    return responseFor(cached, cached.cacheSource === "ISOLATE" ? "LOCAL" : "HIT");
  }

  let refreshPromise = inFlightRefreshes.get(key);
  const isRefreshLeader = !refreshPromise;
  if (!refreshPromise) {
    refreshPromise = refreshForecast(kv, key, latitude, longitude, cached, now, env);
    inFlightRefreshes.set(key, refreshPromise);
  }

  try {
    const refreshed = await refreshPromise;
    const status = isRefreshLeader ? refreshed.cacheStatus : "COALESCED";
    return responseFor(refreshed, status);
  } catch (_error) {
    if (cached && cachedExpiry > 0 && now - cachedExpiry <= STALE_RETENTION_MS) {
      return responseFor(cached, "STALE", true);
    }
    return jsonResponse(
      { error: "Weather data is temporarily unavailable from both free providers. Please retry later." },
      502,
      { "Retry-After": "3600" },
    );
  } finally {
    if (isRefreshLeader && inFlightRefreshes.get(key) === refreshPromise) inFlightRefreshes.delete(key);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
          "Access-Control-Expose-Headers": "X-Royal-Weather-Source, X-Weather-Attribution, X-Royal-Weather-Cache",
          "Access-Control-Max-Age": "86400",
        },
      });
    }
    if (request.method !== "GET") return jsonResponse({ error: "Method not allowed." }, 405, { Allow: "GET, OPTIONS" });
    if (url.pathname === "/health") {
      return jsonResponse({ status: "ok", service: "ROYAL+ Weather API", cache: "Cloudflare KV Free", primary: "Open-Meteo", fallback: "MET Norway" });
    }
    if (url.pathname !== "/v1/forecast") return jsonResponse({ error: "Not found." }, 404);
    return handleForecast(request, env || {});
  },
};

export class OpenMeteoQuotaDO {
  constructor(ctx) {
    this.ctx = ctx;
    this.ctx.storage.sql.exec(`
      CREATE TABLE IF NOT EXISTS quota (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        day_start INTEGER NOT NULL,
        day_units INTEGER NOT NULL,
        hour_start INTEGER NOT NULL,
        hour_units INTEGER NOT NULL,
        minute_start INTEGER NOT NULL,
        minute_units INTEGER NOT NULL
      )
    `);
  }

  async fetch(request) {
    if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
    const now = Date.now();
    const minuteStart = Math.floor(now / 60_000) * 60_000;
    const hourStart = Math.floor(now / 3_600_000) * 3_600_000;
    const dayStart = Math.floor(now / 86_400_000) * 86_400_000;
    // This request asks for more than ten weather variables, so reserve
    // two Open-Meteo call units conservatively before contacting the API.
    const cost = 2;
    const rows = this.ctx.storage.sql.exec("SELECT * FROM quota WHERE id = 1").toArray();
    const previous = rows[0] || {};
    const dayUnits = previous.day_start === dayStart ? previous.day_units : 0;
    const hourUnits = previous.hour_start === hourStart ? previous.hour_units : 0;
    const minuteUnits = previous.minute_start === minuteStart ? previous.minute_units : 0;

    // Stay below Open-Meteo's published free limits, with headroom for
    // fractional call accounting and operational variance.
    if (dayUnits + cost > 9000 || hourUnits + cost > 4500 || minuteUnits + cost > 540) {
      return Response.json({ allowed: false, unavailable: false, retryAfter: 60 }, { status: 200 });
    }

    this.ctx.storage.sql.exec(
      `INSERT INTO quota (id, day_start, day_units, hour_start, hour_units, minute_start, minute_units)
       VALUES (1, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         day_start = excluded.day_start, day_units = excluded.day_units,
         hour_start = excluded.hour_start, hour_units = excluded.hour_units,
         minute_start = excluded.minute_start, minute_units = excluded.minute_units`,
      dayStart, dayUnits + cost, hourStart, hourUnits + cost, minuteStart, minuteUnits + cost,
    );
    return Response.json({ allowed: true, unavailable: false, reservedUnits: cost });
  }
}

export const __test = {
  normalizeCoordinate,
  upstreamExpiry,
  hasUsableForecast,
  cacheKeyFor,
  attributionFor,
  clearLocalCache: () => isolateFallbackCache.clear(),
};
