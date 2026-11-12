create table public.royalplus_sports_response_cache (
  cache_key text primary key
    check (char_length(cache_key) between 1 and 512),
  payload jsonb not null,
  cached_at timestamptz not null default now(),
  expires_at timestamptz not null,
  constraint royalplus_sports_cache_expiry_valid check (expires_at > cached_at)
);

create index royalplus_sports_cache_expires_at_idx
  on public.royalplus_sports_response_cache (expires_at);

alter table public.royalplus_sports_response_cache enable row level security;

revoke all on table public.royalplus_sports_response_cache from public, anon, authenticated;
grant select, insert, update, delete on table public.royalplus_sports_response_cache to service_role;
