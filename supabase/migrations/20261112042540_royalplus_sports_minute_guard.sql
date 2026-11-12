create or replace function private.consume_royalplus_sports_request()
returns jsonb
language plpgsql
security definer
set search_path to pg_catalog, private
as $function$
declare
  v_now timestamptz := clock_timestamp();
  v_rolling_count integer;
  v_daily_count integer;
  v_minute_count integer;
  v_rolling_remaining integer;
  v_daily_remaining integer;
  v_allowed boolean;
begin
  perform pg_advisory_xact_lock(hashtext('royalplus_sports_global_budget'));

  delete from private.royalplus_sports_api_requests
  where requested_at < v_now - interval '48 hours';

  select count(*)::integer into v_rolling_count
  from private.royalplus_sports_api_requests
  where requested_at > v_now - interval '24 hours';

  select count(*)::integer into v_daily_count
  from private.royalplus_sports_api_requests
  where requested_at >= (date_trunc('day', v_now at time zone 'UTC') at time zone 'UTC');

  select count(*)::integer into v_minute_count
  from private.royalplus_sports_api_requests
  where requested_at > v_now - interval '1 minute';

  v_rolling_remaining := greatest(0, 90 - v_rolling_count);
  v_daily_remaining := greatest(0, 90 - v_daily_count);
  v_allowed := v_rolling_count < 90 and v_daily_count < 90 and v_minute_count < 8;

  if v_allowed then
    insert into private.royalplus_sports_api_requests(requested_at) values (v_now);
    v_rolling_remaining := greatest(0, 90 - v_rolling_count - 1);
    v_daily_remaining := greatest(0, 90 - v_daily_count - 1);
  end if;

  return jsonb_build_object(
    'allowed', v_allowed,
    'limit', 90,
    'rolling_24h_remaining', v_rolling_remaining,
    'utc_day_remaining', v_daily_remaining,
    'minute_remaining', greatest(0, 8 - v_minute_count - case when v_allowed then 1 else 0 end),
    'resets_at_utc', (date_trunc('day', v_now at time zone 'UTC') + interval '1 day')::text || 'Z'
  );
end;
$function$;

revoke all on function private.consume_royalplus_sports_request() from public, anon, authenticated;
grant execute on function private.consume_royalplus_sports_request() to service_role;
