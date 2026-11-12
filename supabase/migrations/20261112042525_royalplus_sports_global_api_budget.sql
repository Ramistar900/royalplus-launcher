create schema if not exists private;

create table private.royalplus_sports_api_requests (
  id bigint generated always as identity primary key,
  requested_at timestamptz not null default now()
);

create index royalplus_sports_api_requests_requested_at_idx
  on private.royalplus_sports_api_requests (requested_at desc);

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

  v_rolling_remaining := greatest(0, 90 - v_rolling_count);
  v_daily_remaining := greatest(0, 90 - v_daily_count);
  v_allowed := v_rolling_count < 90 and v_daily_count < 90;

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
    'resets_at_utc', (date_trunc('day', v_now at time zone 'UTC') + interval '1 day')::text || 'Z'
  );
end;
$function$;

revoke all on schema private from public, anon, authenticated;
grant usage on schema private to service_role;
revoke all on table private.royalplus_sports_api_requests from public, anon, authenticated, service_role;
revoke all on function private.consume_royalplus_sports_request() from public, anon, authenticated;
grant execute on function private.consume_royalplus_sports_request() to service_role;
