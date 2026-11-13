create or replace function public.consume_royalplus_sports_request()
returns jsonb
language sql
security invoker
set search_path to pg_catalog, public, private
as $function$
  select private.consume_royalplus_sports_request();
$function$;

revoke all on function public.consume_royalplus_sports_request() from public, anon, authenticated;
grant execute on function public.consume_royalplus_sports_request() to service_role;
