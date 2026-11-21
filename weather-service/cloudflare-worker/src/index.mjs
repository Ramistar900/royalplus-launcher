const UPSTREAM_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact";
const USER_AGENT = "ROYALPlusLauncherWeather/1.0 (https://github.com/Ramistar900/royalplus-launcher/issues)";
const DEFAULT_TTL_MS = 6 * 60 * 60 * 1000;
const STALE_RETENTION_MS = 48 * 60 * 60 * 1000;
const CLIENT_MAX_AGE_SECONDS = 15 * 60;

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
  // MET Norway explicitly requires no more than four decimal places.
  return Number(number.toFixed(3));
}

function cacheKeyFor(latitude, longitude) {
  return `forecast/v1/${latitude.toFixed(3)}_${longitude.toFixed(3)}.json`;
}

function upstreamExpiry(headers, now) {
  const cacheControl = headers.get("Cache-Control") || "";
  const maxAge = cacheControl.match(/(?:^|,)\s*max-age=(\d+)/i);
  if (maxAge) return now + Number(maxAge[1]) * 1000;

  const expires = Date.parse(headers.get("Expires") || "");
  if (Number.isFinite(expires) && expires > now) return expires;

  // Locationforecast is updated every six hours outside the Nordic region.
  // The product FAQ says to wait at least half that interval before repeating.
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
  const headers = {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Cache-Control": `public, max-age=${stale ? 300 : CLIENT_MAX_AGE_SECONDS}`,
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

async function readStoredForecast(bucket, key) {
  const object = await bucket.get(key);
  if (!object) return null;
  return {
    payload: await object.json(),
    metadata: object.customMetadata || {},
  };
}

async function storeForecast(bucket, key, payload, upstreamHeaders, now, previousMetadata = {}) {
  const metadata = {
    expiresAt: String(upstreamExpiry(upstreamHeaders, now)),
    fetchedAt: String(now),
    etag: upstreamHeaders.get("ETag") || previousMetadata.etag || "",
    lastModified: upstreamHeaders.get("Last-Modified") || previousMetadata.lastModified || "",
  };
  await bucket.put(key, JSON.stringify(payload), {
    httpMetadata: { contentType: "application/json; charset=utf-8" },
    customMetadata: metadata,
  });
  return {
    payload,
    metadata: {
      expiresAt: Number(metadata.expiresAt),
      fetchedAt: Number(metadata.fetchedAt),
      etag: metadata.etag,
      lastModified: metadata.lastModified,
    },
  };
}

async function handleForecast(request, env) {
  if (!env.WEATHER_CACHE) {
    return jsonResponse({ error: "Weather cache storage is not configured." }, 503);
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
    cached = await readStoredForecast(env.WEATHER_CACHE, key);
  } catch (_error) {
    return jsonResponse({ error: "Weather cache storage is temporarily unavailable." }, 503);
  }

  const now = Date.now();
  const cachedExpiry = Number(cached?.metadata.expiresAt || 0);
  if (cached && cachedExpiry > now) {
    return responseFor(cached.payload, {
      expiresAt: cachedExpiry,
      fetchedAt: Number(cached.metadata.fetchedAt || 0),
      etag: cached.metadata.etag || "",
      lastModified: cached.metadata.lastModified || "",
    }, "HIT");
  }

  const upstreamUrl = new URL(UPSTREAM_URL);
  upstreamUrl.searchParams.set("lat", latitude.toFixed(3));
  upstreamUrl.searchParams.set("lon", longitude.toFixed(3));
  const headers = new Headers({ Accept: "application/json", "User-Agent": USER_AGENT });
  if (cached?.metadata.etag) headers.set("If-None-Match", cached.metadata.etag);
  if (cached?.metadata.lastModified) headers.set("If-Modified-Since", cached.metadata.lastModified);

  try {
    const upstream = await fetch(upstreamUrl, { method: "GET", headers });
    if (upstream.status === 304 && cached) {
      const refreshed = await storeForecast(
        env.WEATHER_CACHE, key, cached.payload, upstream.headers, now, cached.metadata,
      );
      return responseFor(refreshed.payload, refreshed.metadata, "REVALIDATED");
    }
    if (!upstream.ok) throw new Error(`MET Norway returned HTTP ${upstream.status}`);

    const payload = await upstream.json();
    if (!hasUsableForecast(payload)) throw new Error("MET Norway returned an incomplete forecast.");

    const stored = await storeForecast(env.WEATHER_CACHE, key, payload, upstream.headers, now);
    const result = responseFor(stored.payload, stored.metadata, "MISS");
    if (upstream.status === 203) {
      result.headers.set("X-Weather-Upstream-Warning", "Upstream API version is deprecated.");
    }
    return result;
  } catch (_error) {
    if (cached && cachedExpiry > 0 && now - cachedExpiry <= STALE_RETENTION_MS) {
      return responseFor(cached.payload, {
        expiresAt: cachedExpiry,
        fetchedAt: Number(cached.metadata.fetchedAt || 0),
        etag: cached.metadata.etag || "",
        lastModified: cached.metadata.lastModified || "",
      }, "STALE", true);
    }
    return jsonResponse(
      { error: "Weather data is temporarily unavailable. Please retry later." },
      502,
      { "X-Weather-Source": "MET Norway" },
    );
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
      return jsonResponse({ status: "ok", service: "ROYAL+ Weather API" });
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
};
