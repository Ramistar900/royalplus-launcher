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

type SportsResource = "fixtures" | "standings" | "fifa_rankings";

function cacheTtlSeconds(resource: SportsResource, params: URLSearchParams): number {
  if (resource === "fifa_rankings") return 24 * 60 * 60;
  if (resource === "standings") return 60 * 60;
  // Protect the shared 90/day provider budget: live scores refresh every 20 min,
  // upcoming fixtures are cached 3 hours, and finished results 6 hours.
  if (params.get("live") === "all" || params.has("date")) return 20 * 60;
  if (params.has("next") || params.has("from") || params.has("to")) return 3 * 60 * 60;
  if (params.has("last")) return 6 * 60 * 60;
  return 10 * 60;
}

function localizedName(value: unknown): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) {
    const first = value.find((entry) => entry && typeof entry === "object") as Record<string, unknown> | undefined;
    if (first) return String(first.Description ?? first.description ?? first.Name ?? "");
  }
  if (value && typeof value === "object") {
    const item = value as Record<string, unknown>;
    return String(item.Description ?? item.description ?? item.Name ?? item.name ?? "");
  }
  return "";
}

function asNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

async function fetchFifaRanking(gender: string): Promise<Record<string, unknown>> {
  const base = "https://api.fifa.com/api/v3";
  const headers = {
    "Accept": "application/json",
    "Accept-Language": "en",
    "Origin": "https://www.fifa.com",
    "Referer": "https://www.fifa.com/",
    "User-Agent": "Mozilla/5.0 (compatible; ROYALPlusSports/1.0)",
  };
  const scheduleUrl = new URL(`${base}/rankingschedules/all`);
  scheduleUrl.searchParams.set("type", "0");
  scheduleUrl.searchParams.set("gender", gender);
  scheduleUrl.searchParams.set("language", "en");
  scheduleUrl.searchParams.set("idClient", "64e9afa8-c5c0-413d-882b-bc9e6a81e264");

  const scheduleResponse = await fetch(scheduleUrl, {
    headers,
    signal: AbortSignal.timeout(12000),
  });
  if (!scheduleResponse.ok) throw new Error("fifa_schedule_unavailable");
  const scheduleBody = await scheduleResponse.json() as Record<string, unknown>;
  const schedules = Array.isArray(scheduleBody.Results)
    ? scheduleBody.Results as Record<string, unknown>[]
    : Array.isArray(scheduleBody.results)
    ? scheduleBody.results as Record<string, unknown>[]
    : [];
  if (schedules.length === 0) throw new Error("fifa_schedule_empty");

  const now = Date.now();
  const dated = schedules.map((entry) => ({
    entry,
    date: Date.parse(String(entry.OfficialDate ?? entry.RankingDate ?? entry.PubDate ?? entry.PublicationDate ?? "")),
  })).filter((item) => Number.isFinite(item.date));
  const eligible = dated.filter((item) => item.date <= now + 24 * 60 * 60 * 1000);
  const selected = (eligible.length ? eligible : dated)
    .sort((a, b) => b.date - a.date)[0]?.entry;
  const scheduleId = selected?.IdRankingSchedule ?? selected?.idRankingSchedule ??
    selected?.RankingScheduleId ?? selected?.Id ?? selected?.id;
  if (scheduleId === undefined || scheduleId === null || String(scheduleId).trim() === "") {
    throw new Error("fifa_schedule_id_missing");
  }

  const rankingUrl = new URL(`${base}/rankingsbyschedule`);
  rankingUrl.searchParams.set("rankingScheduleId", String(scheduleId));
  rankingUrl.searchParams.set("language", "en");
  const rankingResponse = await fetch(rankingUrl, {
    headers,
    signal: AbortSignal.timeout(15000),
  });
  if (!rankingResponse.ok) throw new Error("fifa_ranking_unavailable");
  const rankingBody = await rankingResponse.json() as Record<string, unknown>;
  const resultRows = Array.isArray(rankingBody.Results)
    ? rankingBody.Results as Record<string, unknown>[]
    : Array.isArray(rankingBody.results)
    ? rankingBody.results as Record<string, unknown>[]
    : [];
  if (resultRows.length === 0) throw new Error("fifa_ranking_empty");

  const response = resultRows.map((row) => {
    const previousRank = asNumber(row.PrevRank ?? row.PreviousRank ?? row.previousRank);
    const movementValue = asNumber(row.RankingMovement ?? row.Movement ?? row.movement);
    const rank = asNumber(row.Rank ?? row.rank) ?? 0;
    const points = asNumber(row.DecimalTotalPoints ?? row.TotalPoints ?? row.Points ?? row.points) ?? 0;
    const country = localizedName(row.TeamName ?? row.CountryName ?? row.Country ?? row.Name);
    const code = String(row.IdCountry ?? row.CountryCode ?? row.Code ?? "").toUpperCase();
    // FIFA IdCountry is the official three-letter country code (ISO-style alpha-3).
    // Build the documented FIFA flag-image URL from that validated code.
    const countryCode = /^[A-Z]{3}$/.test(code) ? code : "";
    const flag = countryCode
      ? `https://api.fifa.com/api/v3/picture/flags-sq-2/${countryCode}`
      : "";
    const movement = movementValue ?? (previousRank !== null ? previousRank - rank : null);
    return {
      rank,
      country,
      code: countryCode,
      points,
      previousRank,
      movement,
      flag,
    };
  }).filter((row) => row.rank >= 1 && row.rank <= 100 && row.country.length > 0)
    .sort((a, b) => a.rank - b.rank)
    .slice(0, 100);

  if (response.length === 0) throw new Error("fifa_ranking_rows_invalid");
  return {
    response,
    rankingDate: String(selected?.OfficialDate ?? selected?.RankingDate ?? selected?.PubDate ?? ""),
    gender,
  };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return json({ error: "method_not_allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const sportsKey = Deno.env.get("API_SPORTS_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
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
  const resourceValue = incoming.searchParams.get("resource") ?? "fixtures";
  if (!["fixtures", "standings", "fifa_rankings"].includes(resourceValue)) {
    return json({ error: "unsupported_resource" }, 400);
  }
  const resource = resourceValue as SportsResource;
  if (resource !== "fifa_rankings" && !sportsKey) {
    return json({ error: "server_configuration_incomplete" }, 503);
  }

  const allowedByResource: Record<SportsResource, Set<string>> = {
    fixtures: new Set([
      "date", "league", "season", "team", "id", "live", "last", "next",
      "from", "to", "round", "status", "timezone", "venue",
    ]),
    standings: new Set(["league", "season"]),
    fifa_rankings: new Set(["gender"]),
  };
  const params = new URLSearchParams();
  for (const [key, value] of incoming.searchParams.entries()) {
    if (key === "resource") continue;
    if (!allowedByResource[resource].has(key) || value.length > 100) {
      return json({ error: "unsupported_query_parameter", parameter: key }, 400);
    }
    params.append(key, value);
  }

  if (resource === "standings") {
    const league = params.get("league") ?? "";
    const season = params.get("season") ?? "";
    if (!/^\d{1,6}$/.test(league) || !/^\d{4}$/.test(season)) {
      return json({ error: "standings_require_league_and_season" }, 400);
    }
  } else if (resource === "fifa_rankings") {
    const gender = params.get("gender") ?? "1";
    if (gender !== "1" && gender !== "2") {
      return json({ error: "unsupported_fifa_gender" }, 400);
    }
    params.set("gender", gender);
  } else if ([...params.keys()].length === 0) {
    return json({ error: "fixture_filter_required" }, 400);
  }

  const canonicalQuery = [...params.entries()]
    .sort(([keyA, valueA], [keyB, valueB]) =>
      keyA.localeCompare(keyB) || valueA.localeCompare(valueB)
    )
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
  if (canonicalQuery.length + resource.length + 1 > 512) {
    return json({ error: "query_too_large" }, 400);
  }
  const cacheKey = `${resource}:${canonicalQuery}`;
  const ttlSeconds = cacheTtlSeconds(resource, params);
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

  let budget: Record<string, unknown> | null = null;
  if (resource !== "fifa_rankings") {
    const { data, error: budgetError } = await admin.rpc("consume_royalplus_sports_request");
    if (budgetError || !data) {
      return json({ error: "request_budget_unavailable" }, 503);
    }
    budget = data as Record<string, unknown>;
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
  }

  try {
    let payload: unknown;
    if (resource === "fifa_rankings") {
      payload = await fetchFifaRanking(params.get("gender") ?? "1");
    } else {
      const upstreamUrl = new URL(
        resource === "standings"
          ? "https://v3.football.api-sports.io/standings"
          : "https://v3.football.api-sports.io/fixtures",
      );
      upstreamUrl.search = canonicalQuery;
      const upstream = await fetch(upstreamUrl, {
        method: "GET",
        headers: { "x-apisports-key": sportsKey! },
        signal: AbortSignal.timeout(15000),
      });
      const bodyText = await upstream.text();
      try {
        payload = bodyText.trim() ? JSON.parse(bodyText) : {};
      } catch {
        return json({ error: "upstream_invalid_response" }, 502);
      }
      if (!upstream.ok) {
        return json({ error: "football_provider_error", provider_status: upstream.status }, 502);
      }
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

    return json({
      data: payload,
      cache: { hit: false, stale: false, cached_at: cachedAt, ttl_seconds: ttlSeconds },
      cache_warning: cacheWriteError ? "cache_write_failed" : undefined,
      quota: resource === "fifa_rankings"
        ? { source: "FIFA", cache_ttl_seconds: ttlSeconds }
        : {
          limit: 90,
          rolling_24h_remaining: budget?.rolling_24h_remaining,
          utc_day_remaining: budget?.utc_day_remaining,
          minute_remaining: budget?.minute_remaining,
        },
    });
  } catch (error) {
    const errorCode = error instanceof Error ? error.message : "upstream_unavailable";
    if (resource === "fifa_rankings") {
      return json({ error: errorCode.startsWith("fifa_") ? errorCode : "fifa_ranking_unavailable" }, 502);
    }
    return json({ error: "football_provider_unreachable" }, 502);
  }
});
