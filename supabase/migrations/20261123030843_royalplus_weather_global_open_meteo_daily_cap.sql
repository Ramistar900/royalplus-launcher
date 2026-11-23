-- Hard global Open-Meteo request ceiling for ROYAL+ Launcher.
CREATE TABLE IF NOT EXISTS private.royalplus_weather_open_meteo_daily_usage (
  usage_date date PRIMARY KEY,
  request_count integer NOT NULL DEFAULT 0
    CHECK (request_count >= 0 AND request_count <= 9550),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE private.royalplus_weather_open_meteo_daily_usage
  ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE private.royalplus_weather_open_meteo_daily_usage
  FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.reserve_royalplus_open_meteo_request()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, private
AS $function$
DECLARE
  v_date date := (clock_timestamp() AT TIME ZONE 'UTC')::date;
  v_reserved integer;
BEGIN
  INSERT INTO private.royalplus_weather_open_meteo_daily_usage
    (usage_date, request_count, updated_at)
  VALUES
    (v_date, 1, clock_timestamp())
  ON CONFLICT (usage_date)
  DO UPDATE SET
    request_count = private.royalplus_weather_open_meteo_daily_usage.request_count + 1,
    updated_at = clock_timestamp()
  WHERE private.royalplus_weather_open_meteo_daily_usage.request_count < 9550
  RETURNING request_count INTO v_reserved;

  RETURN FOUND;
END;
$function$;

REVOKE ALL ON FUNCTION public.reserve_royalplus_open_meteo_request()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.reserve_royalplus_open_meteo_request()
  TO service_role;

COMMENT ON FUNCTION public.reserve_royalplus_open_meteo_request()
  IS 'Atomically reserves one Open-Meteo upstream request for ROYAL+ Launcher only; hard daily UTC cap 9550 across all devices.';
