import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

const PUBLIC_KEY = "sb_publishable_rltTQCQ_SlGkj_eRecMVaQ_gu-Sw_bW";
const THREE_HOURS_MS = 3 * 60 * 60 * 1000;
const GEOCODING_CACHE_MS = 30 * 24 * 60 * 60 * 1000;

function json(body: unknown, status = 200, extra: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, ...extra },
  });
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function validateTarget(value: unknown): { url: URL; provider: "open_meteo" | "met_norway"; ttlMs: number } | null {
  if (typeof value !== "string" || value.length > 2048) return null;

  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return null;
  }
  if (url.protocol !== "https:" || url.username || url.password || url.hash) return null;

  const host = url.hostname.toLowerCase();
  let provider: "open_meteo" | "met_norway";
  let ttlMs = THREE_HOURS_MS;

  if (host === "api.open-meteo.com" && url.pathname === "/v1/forecast") {
    provider = "open_meteo";
    const latitudeValue = url.searchParams.get("latitude");
    const longitudeValue = url.searchParams.get("longitude");
    if (url.port || latitudeValue === null || longitudeValue === null) return null;
    const latitude = Number(latitudeValue);
    const longitude = Number(longitudeValue);
    const days = Number(url.searchParams.get("forecast_days") ?? "7");
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
        !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
        ![1, 7].includes(days)) return null;
    const allowed = new Set([
      "latitude", "longitude", "current", "wind_speed_unit",
      "daily", "forecast_days", "timezone",
    ]);
    for (const key of url.searchParams.keys()) if (!allowed.has(key)) return null;
  } else if (host === "geocoding-api.open-meteo.com" && url.pathname === "/v1/search") {
    provider = "open_meteo";
    const name = url.searchParams.get("name") ?? "";
    const count = Number(url.searchParams.get("count") ?? "10");
    if (url.port || name.trim().length < 2 || name.length > 100 ||
        !Number.isInteger(count) || count < 1 || count > 100 ||
        url.searchParams.getAll("name").length !== 1) return null;
    const allowed = new Set(["name", "count", "language", "format"]);
    for (const key of url.searchParams.keys()) if (!allowed.has(key)) return null;
    ttlMs = GEOCODING_CACHE_MS;
  } else if (host === "api.met.no" &&
      url.pathname === "/weatherapi/locationforecast/2.0/compact") {
    provider = "met_norway";
    const latitudeValue = url.searchParams.get("lat");
    const longitudeValue = url.searchParams.get("lon");
    if (url.port || latitudeValue === null || longitudeValue === null) return null;
    const latitude = Number(latitudeValue);
    const longitude = Number(longitudeValue);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
        !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
        [...url.searchParams.keys()].some((key) => key !== "lat" && key !== "lon") ||
        url.searchParams.getAll("lat").length !== 1 ||
        url.searchParams.getAll("lon").length !== 1) return null;
  } else {
    return null;
  }

  url.searchParams.sort();
  return { url, provider, ttlMs };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  // This key is public in the Android app. It is an API identifier, not a secret.
  // The strict server-side quota remains the protection against unbounded upstream use.
  if (req.headers.get("apikey") !== PUBLIC_KEY) {
    return json({ error: "invalid_api_key" }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return json({ error: "server_configuration_incomplete" }, 503);
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json() as Record<string, unknown>;
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const target = validateTarget(body.url);
  if (!target) return json({ error: "unsupported_weather_request" }, 400);

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const cacheKey = await sha256(`${target.provider}|${target.url.toString()}`);

  const { data: cached, error: cacheReadError } = await admin
    .from("royalplus_weather_response_cache")
    .select("response_body, expires_at")
    .eq("cache_key", cacheKey)
    .maybeSingle();

  if (cacheReadError) return json({ error: "weather_cache_unavailable" }, 503);
  if (cached && Date.parse(cached.expires_at) > Date.now()) {
    return json(cached.response_body, 200, {
      "X-Royal-Weather-Provider": target.provider,
      "X-Royal-Weather-Cache": "HIT",
    });
  }

  const leaseSeconds = target.url.pathname === "/v1/search" ? 300 : 10800;
  const { data: reservation, error: reservationError } = await admin
    .rpc("reserve_royalplus_weather_fetch", {
      p_cache_key: cacheKey,
      p_provider: target.provider,
      p_lease_seconds: leaseSeconds,
    });
  if (reservationError) return json({ error: "weather_reservation_unavailable" }, 503);

  if (reservation === "quota_exhausted") {
    const now = new Date();
    const nextUtcDay = Date.UTC(
      now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1
    );
    const retryAfter = Math.max(1, Math.ceil((nextUtcDay - now.getTime()) / 1000));
    return json(
      { error: "open_meteo_daily_limit_reached", daily_limit: 9550 },
      429,
      { "Retry-After": String(retryAfter), "X-Royal-Weather-Provider": target.provider },
    );
  }
  if (reservation === "in_progress") {
    return json(
      { error: "weather_fetch_in_progress" },
      503,
      { "Retry-After": "3", "X-Royal-Weather-Provider": target.provider },
    );
  }
  if (reservation !== "reserved") return json({ error: "weather_reservation_invalid" }, 400);

  const upstreamHeaders: Record<string, string> = { "Accept": "application/json" };
  if (target.provider === "met_norway") {
    upstreamHeaders["User-Agent"] =
      "ROYALPlusLauncher/1.0 (Android TV; https://github.com/Ramistar900/royalplus-launcher)";
  }

  async function storeCache(responseBody: Record<string, unknown>, ttlMs: number) {
    const fetchedAt = new Date();
    const expiresAt = new Date(fetchedAt.getTime() + ttlMs);
    return await admin
      .from("royalplus_weather_response_cache")
      .upsert({
        cache_key: cacheKey,
        provider: target.provider,
        response_body: responseBody,
        fetched_at: fetchedAt.toISOString(),
        expires_at: expiresAt.toISOString(),
      }, { onConflict: "cache_key" });
  }

  async function providerFailure(errorCode: string, upstreamStatus?: number) {
    // Negative-cache failed locations too: otherwise a 30-minute worker could
    // repeatedly hit MET Norway while its primary provider remains unavailable.
    const failure: Record<string, unknown> = {
      _royal_weather_error: errorCode,
      ...(upstreamStatus ? { upstream_status: upstreamStatus } : {}),
    };
    const failureTtl = target.provider === "met_norway"
      ? THREE_HOURS_MS
      : target.url.pathname === "/v1/search"
      ? 5 * 60 * 1000
      : THREE_HOURS_MS;
    await storeCache(failure, failureTtl);
    return json(failure, 502, {
      "X-Royal-Weather-Provider": target.provider,
      "X-Royal-Weather-Cache": "NEGATIVE",
    });
  }

  let upstream: Response;
  try {
    upstream = await fetch(target.url, {
      method: "GET",
      headers: upstreamHeaders,
      signal: AbortSignal.timeout(15000),
    });
  } catch {
    return await providerFailure("weather_provider_unreachable");
  }

  if (!upstream.ok) {
    return await providerFailure("weather_provider_error", upstream.status);
  }

  let payload: unknown;
  try {
    payload = await upstream.json();
  } catch {
    return await providerFailure("weather_provider_invalid_json");
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return await providerFailure("weather_provider_invalid_payload");
  }

  const { error: cacheWriteError } = await storeCache(payload as Record<string, unknown>, target.ttlMs);
  if (cacheWriteError) {
    // The live response is still valid; the Open-Meteo slot remains reserved.
    return json(payload, 200, {
      "X-Royal-Weather-Provider": target.provider,
      "X-Royal-Weather-Cache": "MISS-STORE-FAILED",
    });
  }

  return json(payload, 200, {
    "X-Royal-Weather-Provider": target.provider,
    "X-Royal-Weather-Cache": "MISS",
  });
});
