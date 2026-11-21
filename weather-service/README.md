# ROYAL+ Weather Service

A dedicated weather gateway for ROYAL+ Launcher only. It is not shared with ROYAL TV, RS TV, or any other application.

## Zero-billing architecture

- **Weather source:** MET Norway Locationforecast (global coverage; up to nine forecast days).
- **Gateway:** Cloudflare Workers Free.
- **Shared cache:** Cloudflare Workers KV on the Free plan. No R2 bucket is used.
- **Device cache:** the existing ROYAL+ seven-day local forecast cache remains the offline source of truth.
- **No paid plan, R2, card, PayPal, or paid API subscription is required for this design.**

All ROYAL+ devices use one gateway. Coordinates are rounded to three decimals so nearby devices in the same city can share a forecast. KV keeps the shared forecast and its expiry/validator metadata. The Worker follows upstream cache headers, uses conditional revalidation, and can serve the last valid forecast for up to 48 hours during an upstream outage.

If a Free-plan limit is reached, Cloudflare Free operations fail rather than becoming paid overage. The Worker fails closed when it cannot read the shared cache; it does not bypass the cache and send repeated upstream requests. If a KV write limit is reached, it may serve the just-fetched forecast from the current Worker isolate, but it does not retry writes or enable a paid plan. The Android app must keep using its existing local seven-day cache when the gateway returns 503/502.

**Account safety rule:** keep the Cloudflare account on Workers Free. Do not activate Workers Paid, R2 billing, or any paid add-on. Never enter a payment method for this weather service. Free allowances are finite, not unlimited.

## Free limits used by this service

Current published Workers Free limits include 100,000 Worker requests/day and 10 ms CPU per invocation. Workers KV Free includes 100,000 reads/day, 1,000 writes/day, and 1 GB stored data. When an individual KV Free daily limit is exceeded, further operations of that type fail until reset; this service treats that as a temporary outage and relies on device caches. These limits are not guarantees of unlimited capacity.

## Why MET Norway

MET Norway states that its open weather data, including global Locationforecast, is available for commercial use under Creative Commons Attribution 4.0. Its service terms require a truthful identifying User-Agent, attribution, caching, and responsible traffic; the service does not provide an SLA. Global forecasts are updated every six hours, and its documentation says identical requests outside the Nordic region should not be repeated more often than every three hours. This Worker also respects longer upstream cache headers.

Required visible attribution in ROYAL+:
"Weather data: MET Norway, licensed under CC BY 4.0. Modified for ROYAL+."
Include a link to https://creativecommons.org/licenses/by/4.0/ and indicate that ROYAL+ transforms the source data.

## One-time Cloudflare setup (Free plan only)

1. Create or sign in to Cloudflare. Do not select a paid Workers plan.
2. Open **Workers & Pages → KV** and create a namespace named `ROYALPLUS_WEATHER_CACHE`. Creating a KV namespace uses the Workers Free allowance; do not create an R2 bucket.
3. Copy the KV namespace ID into `weather-service/cloudflare-worker/wrangler.toml` at `id = "REPLACE_WITH_KV_NAMESPACE_ID"`.
4. For deployment only, create a Cloudflare API token with the minimum permission **Workers Scripts: Edit** for this account. Do not grant R2 permissions.
5. In GitHub repository **Settings → Secrets and variables → Actions**, add:
   - `CLOUDFLARE_API_TOKEN` — the restricted deployment token.
   - `CLOUDFLARE_ACCOUNT_ID` — the Cloudflare account ID.
   Never send either value in chat or commit it to the repository.
6. After the tested PR is merged to `main`, run **Deploy ROYAL+ Weather Service** manually from GitHub Actions.
7. Record the resulting `https://royalplus-weather-api.<account-subdomain>.workers.dev` URL. The Android client migration and visible attribution must be completed and tested in a separate change before the gateway is treated as the app's active source.

The deploy workflow runs only from `main`. Deployment credentials are used only to publish the Worker; they do not enable paid billing.

## Local tests

```sh
npm test
```

## Upstream and platform documentation

- MET Norway API and global products: https://api.met.no/
- Locationforecast documentation: https://api.met.no/weatherapi/locationforecast/2.0/documentation
- MET Norway Terms of Service: https://docs.api.met.no/doc/TermsOfService
- MET Norway Locationforecast FAQ: https://api.met.no/doc/locationforecast/FAQ
- CC BY 4.0: https://creativecommons.org/licenses/by/4.0/
- Cloudflare Workers Free limits: https://developers.cloudflare.com/workers/platform/limits/
- Cloudflare Workers KV pricing and Free limits: https://developers.cloudflare.com/kv/platform/pricing/
