-- Keep the shared weather cache, fetch leases, and daily counters bounded on the free project.
CREATE INDEX IF NOT EXISTS royalplus_weather_fetch_locks_lease_idx
  ON private.royalplus_weather_fetch_locks (lease_until);

CREATE OR REPLACE FUNCTION public.cleanup_royalplus_weather_data()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, private
AS $function$
BEGIN
  DELETE FROM public.royalplus_weather_response_cache
  WHERE expires_at < clock_timestamp();

  DELETE FROM private.royalplus_weather_fetch_locks
  WHERE lease_until < clock_timestamp();

  DELETE FROM private.royalplus_weather_open_meteo_daily_usage
  WHERE usage_date < (clock_timestamp() AT TIME ZONE 'UTC')::date - 30;
END;
$function$;

REVOKE ALL ON FUNCTION public.cleanup_royalplus_weather_data()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.cleanup_royalplus_weather_data()
  TO service_role;

DO $schedule$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM cron.job
    WHERE jobname = 'royalplus-weather-cache-cleanup'
  ) THEN
    PERFORM cron.schedule(
      'royalplus-weather-cache-cleanup',
      '17 3 * * *',
      'SELECT public.cleanup_royalplus_weather_data();'
    );
  END IF;
END;
$schedule$;
