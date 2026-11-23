-- Serialize cache misses per provider/location and reserve the Open-Meteo slot atomically.
CREATE TABLE IF NOT EXISTS private.royalplus_weather_fetch_locks (
  cache_key text PRIMARY KEY,
  provider text NOT NULL CHECK (provider IN ('open_meteo', 'met_norway')),
  lease_until timestamptz NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT royalplus_weather_fetch_lock_key_length CHECK (length(cache_key) = 64)
);

ALTER TABLE private.royalplus_weather_fetch_locks ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.royalplus_weather_fetch_locks
  FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.reserve_royalplus_weather_fetch(
  p_cache_key text,
  p_provider text,
  p_lease_seconds integer
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, private
AS $function$
DECLARE
  v_acquired boolean;
BEGIN
  IF p_cache_key IS NULL OR p_cache_key !~ '^[a-f0-9]{64}$'
     OR p_provider IS NULL OR p_provider NOT IN ('open_meteo', 'met_norway')
     OR p_lease_seconds IS NULL OR p_lease_seconds < 60 OR p_lease_seconds > 2592000 THEN
    RETURN 'invalid_request';
  END IF;

  INSERT INTO private.royalplus_weather_fetch_locks
    (cache_key, provider, lease_until, updated_at)
  VALUES
    (p_cache_key, p_provider,
     clock_timestamp() + make_interval(secs => p_lease_seconds),
     clock_timestamp())
  ON CONFLICT (cache_key)
  DO UPDATE SET
    provider = EXCLUDED.provider,
    lease_until = EXCLUDED.lease_until,
    updated_at = clock_timestamp()
  WHERE private.royalplus_weather_fetch_locks.lease_until <= clock_timestamp()
  RETURNING true INTO v_acquired;

  IF NOT FOUND THEN
    RETURN 'in_progress';
  END IF;

  IF p_provider = 'open_meteo'
     AND NOT public.reserve_royalplus_open_meteo_request() THEN
    DELETE FROM private.royalplus_weather_fetch_locks
    WHERE cache_key = p_cache_key;
    RETURN 'quota_exhausted';
  END IF;

  RETURN 'reserved';
END;
$function$;

REVOKE ALL ON FUNCTION public.reserve_royalplus_weather_fetch(text, text, integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.reserve_royalplus_weather_fetch(text, text, integer)
  TO service_role;

COMMENT ON FUNCTION public.reserve_royalplus_weather_fetch(text, text, integer)
  IS 'Atomically leases one weather cache key to a single gateway request and reserves the global Open-Meteo daily slot when applicable.';
