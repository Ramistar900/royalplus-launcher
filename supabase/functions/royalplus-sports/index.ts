import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

function json(body: unknown, status = 200, extra: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, ...extra },
  });
}

function cacheTtlSeconds(params: URLSearchParams): number {
  if (params.has("date") || params.get("live") === "all") return 20 * 60;
  if (params.has("next") || params.has("from") || params.has("to")) return 3 * 60 * 60;
  return 10 * 60;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return json({ error: "method_not_allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const sportsKey = Deno.env.get("API_SPORTS_KEY");
  if (!supabaseUrl || !serviceRoleKey || !sportsKey) {
    return json({ error: "server_configuration_incomplete" }, 503);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  if (!token) return json({ error: "authentication_required" }, 401);

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: authData, error: authError } = await admin.auth.getUser(token);
  if (authError || !authData.user) return json({ error: "invalid_session" }, 401);

  const incoming = new URL(req.url);
  const allowedParams = new Set([
    "date", "league", "season", "team", "id", "live", "last", "next",
    "from", "to", "round", "status", "timezone", "venue",
  ]);
  const params = new URLSearchParams();
  for (const [key, value] of incoming.searchParams.entries()) {
    if (!allowedParams.has(key) || value.length > 100) {
      return json({ error: "unsupported_query_parameter", parameter: key }, 400);
    }
    params.append(key, value);
  }
  if ([...params.keys()].length === 0) {
    return json({ error: "fixture_filter_required" }, 400);
  }

  const canonicalQuery = [...params.entries()]
    .sort(([keyA, valueA], [keyB, valueB]) =>
      keyA.localeCompare(keyB) || valueA.localeCompare(valueB)
    )
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
  if (canonicalQuery.length > 512) {
    return json({ error: "query_too_large" }, 400);
  }
  const cacheKey = canonicalQuery;
  const ttlSeconds = cacheTtlSeconds(params);
  const now = new Date();

  const { data: cached, error: cacheReadError } = await admin
    .from("royalplus_sports_response_cache")
    .select("payload,cached_at,expires_at")
    .eq("cache_key", cacheKey)
    .maybeSingle();

  if (cacheReadError) {
    return json({ error: "sports_cache_unavailable" }, 503);
  }

  const isFreshCache = cached && Date.parse(cached.expires_at) > now.getTime();
  if (isFreshCache) {
    return json({
      data: cached.payload,
      cache: { hit: true, stale: false, cached_at: cached.cached_at },
      quota: { limit: 90, cache_hit: true },
    });
  }

  const { data: budget, error: budgetError } = await admin
    .schema("private")
    .rpc("consume_royalplus_sports_request");

  if (budgetError || !budget) {
    return json({ error: "request_budget_unavailable" }, 503);
  }

  if (budget.allowed !== true) {
    const staleIsUsable = cached &&
      Date.parse(cached.cached_at) > now.getTime() - 24 * 60 * 60 * 1000;

    if (staleIsUsable) {
      return json({
        data: cached.payload,
        cache: { hit: true, stale: true, cached_at: cached.cached_at },
        quota: {
          limit: 90,
          rolling_24h_remaining: budget.rolling_24h_remaining,
          utc_day_remaining: budget.utc_day_remaining,
          minute_remaining: budget.minute_remaining,
          exhausted: true,
        },
      });
    }

    return json({
      error: "daily_or_rate_limit_reached",
      limit: budget.limit,
      rolling_24h_remaining: budget.rolling_24h_remaining,
      utc_day_remaining: budget.utc_day_remaining,
      minute_remaining: budget.minute_remaining,
      resets_at_utc: budget.resets_at_utc,
    }, 429, { "Retry-After": "60" });
  }

  const upstreamUrl = new URL("https://v3.football.api-sports.io/fixtures");
  upstreamUrl.search = canonicalQuery;

  try {
    const upstream = await fetch(upstreamUrl, {
      method: "GET",
      headers: { "x-apisports-key": sportsKey },
      signal: AbortSignal.timeout(15000),
    });
    const bodyText = await upstream.text();
    let payload: unknown;
    try {
      payload = JSON.parse(bodyText);
    } catch {
      return json({ error: "upstream_invalid_response" }, 502);
    }

    if (!upstream.ok) {
      return json({ error: "football_provider_error", provider_status: upstream.status }, 502);
    }

    const cachedAt = new Date().toISOString();
    const expiresAt = new Date(now.getTime() + ttlSeconds * 1000).toISOString();
    const { error: cacheWriteError } = await admin
      .from("royalplus_sports_response_cache")
      .upsert({
        cache_key: cacheKey,
        payload,
        cached_at: cachedAt,
        expires_at: expiresAt,
      }, { onConflict: "cache_key" });

    const providerRemaining = upstream.headers.get("x-ratelimit-requests-remaining");
    return json({
      data: payload,
      cache: { hit: false, stale: false, cached_at: cachedAt, ttl_seconds: ttlSeconds },
      cache_warning: cacheWriteError ? "cache_write_failed" : undefined,
      quota: {
        limit: 90,
        rolling_24h_remaining: budget.rolling_24h_remaining,
        utc_day_remaining: budget.utc_day_remaining,
        provider_remaining: providerRemaining ? Number(providerRemaining) : null,
      },
    });
  } catch {
    return json({ error: "football_provider_unreachable" }, 502);
  }
});
