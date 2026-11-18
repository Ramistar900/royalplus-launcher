const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

const MAX_TEXT_LENGTH = 2_000;
const WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 120;

// Per-isolate abuse guard. Configure a hard daily quota in Google Cloud as well.
const requestWindows = new Map<string, number[]>();

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function allowedByRateLimit(userId: string): boolean {
  const now = Date.now();
  const recent = (requestWindows.get(userId) ?? []).filter((timestamp) => now - timestamp < WINDOW_MS);
  if (recent.length >= MAX_REQUESTS_PER_WINDOW) {
    requestWindows.set(userId, recent);
    return false;
  }
  recent.push(now);
  requestWindows.set(userId, recent);

  // Keep the in-memory guard bounded when an isolate serves many anonymous users.
  if (requestWindows.size > 2_000) {
    for (const [key, values] of requestWindows) {
      if (values.every((timestamp) => now - timestamp >= WINDOW_MS)) requestWindows.delete(key);
      if (requestWindows.size <= 1_500) break;
    }
  }
  return true;
}

function getPublishableKey(): string | null {
  const dictionary = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (dictionary) {
    try {
      const keys = JSON.parse(dictionary) as Record<string, string>;
      if (keys.default) return keys.default;
    } catch {
      // Fall back to the legacy anon key for projects that have not migrated keys.
    }
  }
  return Deno.env.get("SUPABASE_ANON_KEY") ?? null;
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const authorization = request.headers.get("Authorization") ?? "";
  const bearerMatch = authorization.match(/^Bearer\s+(.+)$/i);
  if (!bearerMatch) return json({ error: "unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const publishableKey = getPublishableKey();
  if (!supabaseUrl || !publishableKey) {
    return json({ error: "supabase_auth_not_configured" }, 503);
  }

  // Verify the actual Supabase user JWT; the publishable key alone is not authentication.
  const userResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    method: "GET",
    headers: {
      apikey: publishableKey,
      Authorization: `Bearer ${bearerMatch[1]}`,
    },
  });
  if (!userResponse.ok) return json({ error: "unauthorized" }, 401);

  const user = await userResponse.json().catch(() => null) as { id?: string } | null;
  if (!user?.id) return json({ error: "unauthorized" }, 401);
  if (!allowedByRateLimit(user.id)) {
    return json({ error: "translation_rate_limit" }, 429);
  }

  let body: { text?: unknown };
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const text = typeof body.text === "string" ? body.text.trim() : "";
  if (!text) return json({ error: "empty_text" }, 400);
  if (text.length > MAX_TEXT_LENGTH) return json({ error: "text_too_long" }, 413);

  const googleKey = Deno.env.get("GOOGLE_TRANSLATE_API_KEY");
  if (!googleKey) return json({ error: "translator_not_configured" }, 503);

  try {
    // Omit "source": Google detects the source language instead of relying on a
    // short app-maintained list of language codes.
    const parameters = new URLSearchParams();
    parameters.set("q", text);
    parameters.set("target", "ar");
    parameters.set("format", "text");

    const translateResponse = await fetch(
      `https://translation.googleapis.com/language/translate/v2?key=${encodeURIComponent(googleKey)}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: parameters,
        signal: AbortSignal.timeout(20_000),
      },
    );

    const result = await translateResponse.json().catch(() => null) as {
      data?: { translations?: Array<{ translatedText?: string; detectedSourceLanguage?: string }> };
    } | null;

    if (!translateResponse.ok) {
      // Do not forward provider payloads that may contain diagnostic details or keys.
      return json({ error: "translation_provider_failed" }, 502);
    }

    const translation = result?.data?.translations?.[0];
    const translatedText = translation?.translatedText?.trim();
    if (!translatedText) return json({ error: "empty_translation_response" }, 502);

    return json({
      translatedText,
      detectedSourceLanguage: translation?.detectedSourceLanguage ?? null,
      targetLanguage: "ar",
    });
  } catch {
    return json({ error: "translation_provider_unavailable" }, 502);
  }
});
