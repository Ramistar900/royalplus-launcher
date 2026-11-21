# ROYAL+ Weather Service

A small, dedicated weather gateway for ROYAL+ Launcher. It is not shared with ROYAL TV, RS TV, or other applications.

## First implementation target

- Cloudflare Workers Free as an edge gateway.
- MET Norway Locationforecast as the initial global forecast source.
- Seven-day minimum validation before a response is cached.
- Coordinate normalization to four decimal places, as required by MET Norway.
- Respect upstream cache headers; use a six-hour fallback only when the source omits cache timing.
- Edge caching, conditional requests, and stale-cache fallback during upstream outages.
- No API key, paid weather subscription, database, or paid server in this first design.

## Why MET Norway

MET Norway states that its open weather data, including global Locationforecast, is available for commercial use under Creative Commons attribution terms. Its service terms require an identifying User-Agent, attribution, caching, and responsible traffic; the service has no SLA. Global forecasts are updated every six hours, and the API documentation asks clients outside the Nordic region to wait at least half that interval before repeating an identical request.

Required attribution must be displayed in ROYAL+:
"Weather data: MET Norway, licensed under CC BY 4.0."
Include a link to https://creativecommons.org/licenses/by/4.0/ and indicate that ROYAL+ transforms the source data.

## Deploy

1. Create a Cloudflare account on its Free plan.
2. From this directory run `npx wrangler login`.
3. Run `npx wrangler deploy`.
4. Record the resulting `https://royalplus-weather-api.<account-subdomain>.workers.dev` URL.
5. Configure the Android client to use that URL in a separate, tested change.

No Cloudflare token or account identifier is committed to this repository.

## Free-tier boundaries

The current Cloudflare Workers Free plan documents 100,000 requests/day and 10 ms CPU per invocation. The Worker uses only lightweight validation, edge cache operations, and one upstream HTTP request on a cache miss. This is a quota, not an unlimited-capacity guarantee. Check the current limits before public rollout.

The Cloudflare Cache API is edge-local (per data center), not a globally replicated database. Popular cities share cached responses within the same edge location. If ROYAL+ grows beyond this design's free-tier or cache behavior, stop and review capacity rather than silently enabling paid usage.

## Local tests

```sh
npm test
```

## Upstream documentation

- MET Norway API: https://api.met.no/
- Locationforecast documentation: https://api.met.no/weatherapi/locationforecast/2.0/documentation
- MET Norway Terms of Service: https://docs.api.met.no/doc/TermsOfService
- MET Norway Locationforecast FAQ: https://api.met.no/doc/locationforecast/FAQ
- CC BY 4.0: https://creativecommons.org/licenses/by/4.0/
- Cloudflare Workers limits: https://developers.cloudflare.com/workers/platform/limits/
