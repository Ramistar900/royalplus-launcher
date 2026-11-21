const UPSTREAM_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact";
const USER_AGENT = "ROYALPlusLauncherWeather/1.0 (https://github.com/Ramistar900/royalplus-launcher/issues)";
const DEFAULT_TTL_MS = 6 * 60 * 60 * 1000;
const MIN_UPSTREAM_INTERVAL_MS = 3 * 60 * 60 * 1000;
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
  return `forecast/v1/${latitude.toFixed(3)}_${longitude.toFixed(3)}`;
}

function upstreamExpiry(headers, now) {
  const cacheControl = headers.get("Cache-Control") || "";
  const maxAge = cacheControl.match(/(?:^|,)\s*max-age=(\d+)/i);
  if (maxAge) {
    return now + Math.max(Number(maxAge[1]) * 1000, MIN_UPSTREAM_INTERVAL_MS);
  }

  const expires = Date.parse(headers.get("Expires") || "");
  if (Number.isFinite(expires) && expires > now) {
    return Math.max(expires, now + MIN_UPSTREAM_INTERVAL_MS);
  }

  // Global Locationforecast is updated every six hours. Never poll a
  // location more frequently than MET Norway's documented minimum interval.
  return now + DEFAULT_TTL_MS;
}

function hasUsableForecast(payload) {
  const series = payload?.properties?.timeseries;
  if (!Array.isArray(series) || series.length === 0) return false;
  const days = new Set(
    series.map((item) => String(item?.time || "").slice(0, 10)).filter(Boolean),
  );
  return days.size >= 7;
}

function responseFor(payload, metadata, cacheStatus, stale = false) {
  const remainingMs = Math.max(0, Number(metadata.expiresAt) - Date.now());
  const maxAge = stale
    ? 300
    : Math.min(CLIENT_MAX_AGE_SECONDS, Math.floor(remainingMs / 1000));
  const headers = {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Cache-Control": `public, max-age=${maxAge}`,
    "X-Royal-Weather-Cache": cacheStatus,
    "X-Royal-Weather-Expires-At": String(metadata.expiresAt),
    "X-Royal-Weather-Fetched-At": String(metadata.fetchedAt),
    "X-Weather-Attribution": "Weather data: MET Norway, CC BY 4.0",
  };
  if (metadata.etag) headers.ETag = metadata.etag;
  if (metadata.lastModified) headers["Last-Modified"] = metadata.lastModified;
  if (stale) headers.Warning = '110 - "Response is stale; upstream refresh failed"';
  return jsonResponse(payload, 200, headers);
}

async function readStoredForecast(kv, key) {
  try {
    const stored = await kv.get(key, "json");
    if (stored?.payload && stored?.metadata) return { ...stored, source: "KV" };
    const local = isolateFallbackCache.get(key);
    return local ? { ...local, source: "ISOLATE" } : null;
  } catch (error) {
    const local = isolateFallbackCache.get(key);
    if (local) return { ...local, source: "ISOLATE" };
    throw error;
  }
}

async function storeForecast(kv, key, payload, upstreamHeaders, now, previousMetadata = {}, cacheStatus = "MISS") {
  const metadata = {
    expiresAt: upstreamExpiry(upstreamHeaders, now),
    fetchedAt: now,
    etag: upstreamHeaders.get("ETag") || previousMetadata.etag || "",
    lastModified: upstreamHeaders.get("Last-Modified") || previousMetadata.lastModified || "",
  };
  const entry = { payload, metadata };
  isolateFallbackCache.set(key, entry);

  const ttlSeconds = Math.max(
    60,
    Math.ceil((metadata.expiresAt - now + STALE_RETENTION_MS) / 1000),
  );
  try {
    await kv.put(key, JSON.stringify(entry), { expirationTtl: ttlSeconds });
    return { ...entry, cacheStatus };
  } catch (_error) {
    // KV Free limits fail closed. Keep this isolate's copy and serve the
    // forecast to this caller; never upgrade the account or incur overage.
    return { ...entry, cacheStatus: cacheStatus === "REVALIDATED" ? "REVALIDATED_UNCACHED" : "UNCACHED" };
  }
}

async function handleForecast(request, env) {
  const kv = env.WEATHER_CACHE;
  if (!kv || typeof kv.get !== "function" || typeof kv.put !== "function") {
    return jsonResponse(
      { error: "Free weather cache is not configured. No upstream request was made." },
      503,
      { "Retry-After": "3600" },
    );
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
    // KV daily read quota or service errors must stop this request rather
    // than repeatedly hitting MET Norway without a shared cache.
    return jsonResponse(
      { error: "Free weather cache limit reached or temporarily unavailable. Use the saved device forecast and retry later." },
      503,
      { "Retry-After": "3600", "X-Royal-Weather-Cache": "LIMIT" },
    );
  }

  const now = Date.now();
  const cachedExpiry = Number(cached?.metadata.expiresAt || 0);
  if (cached && cachedExpiry > now) {
    return responseFor(cached.payload, cached.metadata, cached.source === "ISOLATE" ? "LOCAL" : "HIT");
  }

  const upstreamUrl = new URL(UPSTREAM_URL);
  upstreamUrl.searchParams.set("lat", latitude.toFixed(3));
  upstreamUrl.searchParams.set("lon", longitude.toFixed(3));
  const headers = new Headers({ Accept: "application/json", "User-Agent": USER_AGENT });
  if (cached?.metadata.etag) headers.set("If-None-Match", cached.metadata.etag);
  if (cached?.metadata.lastModified) headers.set("If-Modified-Since", cached.metadata.lastModified);

  let refreshPromise = inFlightRefreshes.get(key);
  const isRefreshLeader = !refreshPromise;
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const upstream = await fetch(upstreamUrl, { method: "GET", headers });
      if (upstream.status === 304 && cached) {
        return storeForecast(kv, key, cached.payload, upstream.headers, now, cached.metadata, "REVALIDATED");
      }
      if (!upstream.ok) throw new Error(`MET Norway returned HTTP ${upstream.status}`);

      const payload = await upstream.json();
      if (!hasUsableForecast(payload)) throw new Error("MET Norway returned an incomplete forecast.");
      return storeForecast(kv, key, payload, upstream.headers, now);
    })();
    inFlightRefreshes.set(key, refreshPromise);
  }

  try {
    const refreshed = await refreshPromise;
    const cacheStatus = isRefreshLeader
      ? (refreshed.cacheStatus === "MISS" ? "MISS" : refreshed.cacheStatus)
      : "COALESCED";
    return responseFor(refreshed.payload, refreshed.metadata, cacheStatus);
  } catch (_error) {
    if (cached && cachedExpiry > 0 && now - cachedExpiry <= STALE_RETENTION_MS) {
      return responseFor(cached.payload, cached.metadata, "STALE", true);
    }
    return jsonResponse(
      { error: "Weather data is temporarily unavailable. Please retry later." },
      502,
      { "Retry-After": "3600", "X-Weather-Source": "MET Norway" },
    );
  } finally {
    if (isRefreshLeader && inFlightRefreshes.get(key) === refreshPromise) {
      inFlightRefreshes.delete(key);
    }
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
          "Access-Control-Max-Age": "86400",
        },
      });
    }
    if (request.method !== "GET") {
      return jsonResponse({ error: "Method not allowed." }, 405, { Allow: "GET, OPTIONS" });
    }
    if (url.pathname === "/health") {
      return jsonResponse({ status: "ok", service: "ROYAL+ Weather API", cache: "Cloudflare KV Free" });
    }
    if (url.pathname !== "/v1/forecast") {
      return jsonResponse({ error: "Not found." }, 404);
    }
    return handleForecast(request, env || {});
  },
};

export const __test = {
  normalizeCoordinate,
  upstreamExpiry,
  hasUsableForecast,
  cacheKeyFor,
  clearLocalCache: () => isolateFallbackCache.clear(),
};
