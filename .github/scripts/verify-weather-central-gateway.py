from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPO = ROOT
WEATHER = (REPO / "app/src/main/java/com/royalplus/launcher/WeatherLocationRepository.kt").read_text()
WORKER = (REPO / "app/src/main/java/com/royalplus/launcher/RoyalWeatherForecastWorker.kt").read_text()
ACTIVITY = (REPO / "app/src/main/java/com/royalplus/launcher/MainActivity.kt").read_text()
GATEWAY = (REPO / "supabase/functions/royalplus-weather/index.ts").read_text()
QUOTA = (REPO / "supabase/migrations/20261123030843_royalplus_weather_global_open_meteo_daily_cap.sql").read_text()
CACHE = (REPO / "supabase/migrations/20261123031424_royalplus_weather_shared_response_cache.sql").read_text()
LOCKS = (REPO / "supabase/migrations/20261123031909_royalplus_weather_fetch_singleflight_lock.sql").read_text()
CLEANUP = (REPO / "supabase/migrations/20261123032127_royalplus_weather_daily_cache_cleanup.sql").read_text()
README = (REPO / "weather-service/README.md").read_text()

assert 'WEATHER_GATEWAY_URL = "https://ymujjcdsdclptbpgckll.supabase.co/functions/v1/royalplus-weather"' in WEATHER
assert 'requestWeatherGatewayJson(url)' in WEATHER
for host in ('api.open-meteo.com', 'geocoding-api.open-meteo.com', 'api.met.no'):
    assert host in WEATHER, f"Missing gateway routing for {host}"
assert 'requestMethod = "POST"' in WEATHER
assert 'setRequestProperty("apikey", WEATHER_SUPABASE_PUBLISHABLE_KEY)' in WEATHER
assert 'service_role' not in WEATHER.lower(), "Privileged service role must never ship in the Android app"
assert 'WEATHER_CACHE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L' in WEATHER
assert '3 * 60 * 60 * 1000L' in WEATHER
assert '30, TimeUnit.MINUTES' in WORKER
assert 'cancelUniqueWork(LEGACY_UNIQUE_WORK_NAME)' in WORKER
assert '3 * 60 * 60 * 1000L' in ACTIVITY

assert 'request_count <= 9550' in QUOTA
assert 'request_count < 9550' in QUOTA
assert 'reserve_royalplus_open_meteo_request' in QUOTA
assert 'REVOKE ALL ON FUNCTION' in QUOTA
assert 'GRANT EXECUTE' in QUOTA and 'TO service_role' in QUOTA
assert 'ENABLE ROW LEVEL SECURITY' in QUOTA

assert 'royalplus_weather_response_cache' in CACHE
assert 'ENABLE ROW LEVEL SECURITY' in CACHE
assert 'REVOKE ALL' in CACHE and 'GRANT ALL' in CACHE
assert '9550' in GATEWAY
assert 'api.open-meteo.com' in GATEWAY
assert 'geocoding-api.open-meteo.com' in GATEWAY
assert 'api.met.no' in GATEWAY
assert 'THREE_HOURS_MS = 3 * 60 * 60 * 1000' in GATEWAY
assert 'GEOCODING_CACHE_MS = 30 * 24 * 60 * 60 * 1000' in GATEWAY
assert GATEWAY.index('rpc("reserve_royalplus_weather_fetch")') < GATEWAY.index('fetch(target.url')
assert 'reservation === "quota_exhausted"' in GATEWAY
assert 'reservation === "in_progress"' in GATEWAY
assert 'async function providerFailure' in GATEWAY
assert 'royalplus_weather_fetch_locks' in LOCKS
assert 'reserve_royalplus_weather_fetch' in LOCKS
assert 'p_lease_seconds' in LOCKS
assert 'reserve_royalplus_open_meteo_request' in LOCKS
assert 'cleanup_royalplus_weather_data' in CLEANUP
assert 'cron.schedule' in CLEANUP
assert 'DELETE FROM public.royalplus_weather_response_cache' in CLEANUP
assert 'DELETE FROM private.royalplus_weather_fetch_locks' in CLEANUP
assert 'failureTtl = target.provider === "met_norway"' in GATEWAY
assert 'verify_jwt' not in GATEWAY
assert 'service_role' not in GATEWAY.split('Deno.serve', 1)[0], "No service key may be embedded in source"
assert '9,550 requests per UTC day across every ROYAL+ Launcher device' in README
assert 'No Cloudflare' in README

print("ROYAL+ central weather gateway, routing, shared cache, 9,550/day atomic cap, seven-day offline cache and 30-minute check guards passed.")
