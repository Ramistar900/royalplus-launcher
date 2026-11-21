# ROYAL+ Weather Service

A dedicated, globally shared weather gateway for ROYAL+ Launcher only. It is not shared with ROYAL TV, RS TV, or other applications.

## Architecture

- **Weather source:** MET Norway Locationforecast, with global coverage and forecasts for the next nine days.
- **Gateway:** Cloudflare Workers Free.
- **Shared global storage:** Cloudflare R2 Standard bucket.
- **Android cache:** the existing ROYAL+ seven-day local forecast cache remains in place.
- **No paid API key or subscription.**

All launcher devices request the same service endpoint. Forecasts for the same rounded coordinates are stored once in the shared R2 bucket, then reused by devices in every region. The Worker only refreshes a location after the upstream cache expiry, uses conditional requests where available, and serves the last valid forecast for up to 48 hours if the upstream service is temporarily unavailable.

## Why MET Norway

MET Norway states that its open weather data, including global Locationforecast, is available for commercial use under Creative Commons Attribution 4.0. Its service terms require an identifying User-Agent, attribution, caching, and responsible traffic; the service does not provide an SLA. Global forecasts are updated every six hours, and its API documentation asks clients outside the Nordic region to wait at least half that interval before repeating an identical request.

Required attribution must be visible in ROYAL+:
"Weather data: MET Norway, licensed under CC BY 4.0."
Include a link to https://creativecommons.org/licenses/by/4.0/ and indicate that ROYAL+ transforms the source data.

## Free-tier capacity (current published limits)

- Workers Free: 100,000 requests/day and 10 ms CPU per request.
- R2 Standard: 10 GB-month storage, 1 million Class A operations/month, 10 million Class B operations/month, and free egress.

These are free-tier allowances, not unlimited capacity. If usage approaches a limit, the service must stop and be reviewed; it must never silently activate paid billing.

## One-time Cloudflare setup

1. Create or sign in to a Cloudflare account and stay on the Free plan.
2. Create an R2 Standard bucket named `royalplus-weather-cache`.
3. From this directory run `npx wrangler login`.
4. Run `npx wrangler deploy`.
5. Record the resulting `https://royalplus-weather-api.<account-subdomain>.workers.dev` URL.
6. Only after deployment is verified, connect the Android app to that URL in a separate tested change.

No Cloudflare token, account identifier, or other secret is committed to this repository.

## Local tests

```sh
npm test
```

## Upstream documentation

- MET Norway API and global products: https://api.met.no/
- Locationforecast documentation: https://api.met.no/weatherapi/locationforecast/2.0/documentation
- MET Norway Terms of Service: https://docs.api.met.no/doc/TermsOfService
- MET Norway Locationforecast FAQ: https://api.met.no/doc/locationforecast/FAQ
- CC BY 4.0: https://creativecommons.org/licenses/by/4.0/
- Cloudflare Workers limits: https://developers.cloudflare.com/workers/platform/limits/
- Cloudflare R2 pricing and free tier: https://developers.cloudflare.com/r2/pricing/
