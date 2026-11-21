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
  return Number(number.toFixed(4));
}

function cacheKeyFor(latitude, longitude, origin = "https://weather.example") {
  // Cloudflare Cache API keys must stay on the Worker zone's own hostname.
  const url = new URL("/__royalplus_weather_cache/v1/forecast", origin);
  url.searchParams.set("lat", latitude.toFixed(4));
  url.searchParams.set("lon", longitude.toFixed(4));
  return new Request(url.toString(), { method: "GET" });
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

function clientResponse(cached, cacheStatus, stale = false) {
  const headers = new Headers(cached.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "GET, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type");
  headers.set("Cache-Control", `public, max-age=${stale ? 300 : CLIENT_MAX_AGE_SECONDS}`);
  headers.set("X-Royal-Weather-Cache", cacheStatus);
  if (stale) headers.set("Warning", '110 - "Response is stale; upstream refresh failed"');
  return new Response(cached.body, { status: 200, headers });
}

async function storeCache(cache, key, payload, upstreamHeaders, now) {
  const expiresAt = upstreamExpiry(upstreamHeaders, now);
  const headers = new Headers({
    "Content-Type": "application/json; charset=utf-8",
    "X-Royal-Weather-Expires-At": String(expiresAt),
    "X-Royal-Weather-Fetched-At": String(now),
    "X-Weather-Attribution": "Weather data: MET Norway, CC BY 4.0",
    // Retain a stale copy for outage fallback; freshness is checked separately.
    "Cache-Control": `public, max-age=${Math.ceil(STALE_RETENTION_MS / 1000)}`,
  });
  const etag = upstreamHeaders.get("ETag");
  const lastModified = upstreamHeaders.get("Last-Modified");
  if (etag) headers.set("ETag", etag);
  if (lastModified) headers.set("Last-Modified", lastModified);

  const response = new Response(JSON.stringify(payload), { status: 200, headers });
  await cache.put(key, response.clone());
  return response;
}

async function handleForecast(request) {
  const url = new URL(request.url);
  const latitude = normalizeCoordinate(url.searchParams.get("lat"), -90, 90);
  const longitude = normalizeCoordinate(url.searchParams.get("lon"), -180, 180);
  if (latitude === null || longitude === null) {
    return jsonResponse({ error: "Provide valid latitude and longitude coordinates." }, 400);
  }

  const cache = caches.default;
  const key = cacheKeyFor(latitude, longitude, url.origin);
  const cached = await cache.match(key);
  const now = Date.now();
  const cachedExpiry = Number(cached?.headers.get("X-Royal-Weather-Expires-At") || 0);

  if (cached && cachedExpiry > now) {
    return clientResponse(cached, "HIT");
  }

  const upstreamUrl = new URL(UPSTREAM_URL);
  upstreamUrl.searchParams.set("lat", latitude.toFixed(4));
  upstreamUrl.searchParams.set("lon", longitude.toFixed(4));
  const headers = new Headers({ Accept: "application/json", "User-Agent": USER_AGENT });
  if (cached?.headers.get("ETag")) headers.set("If-None-Match", cached.headers.get("ETag"));
  if (cached?.headers.get("Last-Modified")) headers.set("If-Modified-Since", cached.headers.get("Last-Modified"));

  try {
    const upstream = await fetch(upstreamUrl, { method: "GET", headers });
    if (upstream.status === 304 && cached) {
      const refreshedHeaders = new Headers(upstream.headers);
      if (!refreshedHeaders.has("ETag") && cached.headers.has("ETag")) {
        refreshedHeaders.set("ETag", cached.headers.get("ETag"));
      }
      if (!refreshedHeaders.has("Last-Modified") && cached.headers.has("Last-Modified")) {
        refreshedHeaders.set("Last-Modified", cached.headers.get("Last-Modified"));
      }
      const refreshed = await storeCache(cache, key, await cached.json(), refreshedHeaders, now);
      return clientResponse(refreshed, "REVALIDATED");
    }
    if (!upstream.ok) throw new Error(`MET Norway returned HTTP ${upstream.status}`);

    const payload = await upstream.json();
    if (!hasUsableForecast(payload)) throw new Error("MET Norway returned an incomplete forecast.");

    const stored = await storeCache(cache, key, payload, upstream.headers, now);
    const result = clientResponse(stored, "MISS");
    if (upstream.status === 203) result.headers.set("X-Weather-Upstream-Warning", "Upstream API version is deprecated.");
    return result;
  } catch (error) {
    if (cached && cachedExpiry > 0 && now - cachedExpiry <= STALE_RETENTION_MS) {
      return clientResponse(cached, "STALE", true);
    }
    return jsonResponse(
      { error: "Weather data is temporarily unavailable. Please retry later." },
      502,
      { "X-Weather-Source": "MET Norway" },
    );
  }
}

export default {
  async fetch(request) {
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
    return handleForecast(request);
  },
};

export const __test = {
  normalizeCoordinate,
  upstreamExpiry,
  hasUsableForecast,
  cacheKeyFor,
};
