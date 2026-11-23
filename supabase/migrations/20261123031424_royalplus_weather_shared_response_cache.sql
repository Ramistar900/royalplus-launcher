-- Server-side cache shared by all ROYAL+ Launcher devices.
CREATE TABLE IF NOT EXISTS public.royalplus_weather_response_cache (
  cache_key text PRIMARY KEY,
  provider text NOT NULL CHECK (provider IN ('open_meteo', 'met_norway')),
  response_body jsonb NOT NULL,
  fetched_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  CONSTRAINT royalplus_weather_cache_key_length CHECK (length(cache_key) BETWEEN 1 AND 128)
);

CREATE INDEX IF NOT EXISTS royalplus_weather_response_cache_expiry_idx
  ON public.royalplus_weather_response_cache (expires_at);

ALTER TABLE public.royalplus_weather_response_cache ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.royalplus_weather_response_cache
  FROM PUBLIC, anon, authenticated;

GRANT ALL ON TABLE public.royalplus_weather_response_cache TO service_role;

COMMENT ON TABLE public.royalplus_weather_response_cache
  IS 'Private server-side response cache for ROYAL+ Launcher weather only. Never expose to client roles.';
