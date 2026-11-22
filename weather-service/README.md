# ROYAL+ Weather Service

A dedicated weather gateway for ROYAL+ Launcher only. It is not shared with ROYAL TV, RS TV, or any other application.

## Zero-billing architecture

- **Primary weather source:** Open-Meteo Free API, used only under the written permission received for the described non-commercial, ad-free ROYAL+ Launcher use.
- **Fallback weather source:** MET Norway Locationforecast.
- **Gateway and shared cache:** Cloudflare Workers Free + Workers KV Free.
- **Strict Open-Meteo quota guard:** one SQLite-backed Cloudflare Durable Object on the Workers Free plan. It serializes quota reservations globally before an Open-Meteo request is sent.
- **Device cache:** the existing ROYAL+ seven-day local forecast cache remains the offline source of truth.
- **No paid plan, R2 bucket, paid storage backend, card, PayPal, or paid API subscription is part of this design.**

The Open-Meteo request is conservatively charged as two call units because it requests more than ten weather variables. The global quota guard allows at most 9,000 weighted units/day, 4,500/hour, and 540/minute. These safety ceilings are intentionally below Open-Meteo's published free limits (10,000/day, 5,000/hour, 600/minute). The daily ceiling also keeps a 31-day month below the published 300,000-call monthly limit. Quota is reserved before the primary upstream request; failed upstream requests still consume the reservation so retries cannot evade the cap.

If the quota guard is missing, unavailable, or reports that a ceiling has been reached, the Worker does not call Open-Meteo. It may try MET Norway as the fallback. If both providers fail, or the free cache is unavailable, the Worker returns an error and ROYAL+ must continue using its saved local seven-day forecast and offline videos.

Coordinates are rounded to three decimals so nearby devices can share one forecast. The shared KV cache follows upstream cache headers and retains the last valid forecast for up to 48 hours during provider outages. Open-Meteo is refreshed no more often than the shared cache allows. MET Norway requests respect its documented minimum polling interval outside the Nordic region.

## Free-plan safeguards

- Keep the Cloudflare account on **Workers Free**.
- Use only SQLite-backed Durable Objects, which are available on Workers Free; do not select a paid storage backend.
- Use Workers KV Free for weather payload caching; do not create an R2 bucket.
- Do not activate Workers Paid, R2 billing, a paid add-on, or enter a payment method for this service.
- Cloudflare Free allowances are finite. If a Workers, KV, or Durable Objects free allowance is exhausted, operations fail until reset; the app falls back to local cached weather.
- Never enable automatic paid overage or upgrade billing to keep the service running.

## Attribution

The response identifies the actual provider in `X-Royal-Weather-Source` and provides provider attribution in `X-Weather-Attribution`.

The Android client in this PR now routes forecast requests through the gateway, retains provider attribution in its local city cache, and displays the provider plus CC BY 4.0 links in the forecast panel. Before live gateway use, configure the real Worker URL in the GitHub Actions variable and verify the complete Android build:

- Open-Meteo: **Weather data by Open-Meteo.com**, licensed under CC BY 4.0. Include https://open-meteo.com/ and https://creativecommons.org/licenses/by/4.0/.
- MET Norway: **Weather data by MET Norway**, licensed under CC BY 4.0. Include https://api.met.no/ and https://creativecommons.org/licenses/by/4.0/.

Do not imply either provider endorses ROYAL+.


## Android gateway connection

The Android weather repository now sends forecast requests only to this gateway at `/v1/forecast?lat=...&lon=...`. It no longer calls either forecast provider directly, so devices cannot bypass the centralized Open-Meteo quota guard. The app continues to keep each city's rolling seven-day forecast locally and returns the last saved forecast if the gateway is unavailable.

Set the GitHub Actions repository variable `ROYAL_WEATHER_API_BASE_URL` to the deployed Worker origin, for example `https://royalplus-weather-api.<your-account-subdomain>.workers.dev` (replace the placeholder with the actual Cloudflare account subdomain). The Android Gradle build reads this variable into `BuildConfig.ROYAL_WEATHER_API_BASE_URL`. Do not set a guessed URL: until the real Worker URL is configured, Android deliberately makes no direct provider forecast calls and uses saved weather only.

The app displays the actual source returned by the gateway and links both the provider and the CC BY 4.0 license. The source name is persisted with the local forecast so attribution remains visible offline.

## One-time Cloudflare setup (Free plan only)

1. Create or sign in to Cloudflare. Keep the account on Workers Free.
2. Open **Workers & Pages → KV** and create a namespace named `ROYALPLUS_WEATHER_CACHE`. Do not create an R2 bucket.
3. Copy the KV namespace ID into `weather-service/cloudflare-worker/wrangler.toml` at `id = "REPLACE_WITH_KV_NAMESPACE_ID"`.
4. The SQLite Durable Object quota class is declared in Wrangler migrations. It must remain SQLite-backed; do not switch to a paid KV-backed Durable Object.
5. For deployment only, create a Cloudflare API token with the minimum permission **Workers Scripts: Edit** for this account. Do not grant R2 permissions.
6. In GitHub repository **Settings → Secrets and variables → Actions**, add:
   - `CLOUDFLARE_API_TOKEN` — the restricted deployment token.
   - `CLOUDFLARE_ACCOUNT_ID` — the Cloudflare account ID.
   Never send either value in chat or commit it to the repository.
7. After the complete PR is tested and merged to `main`, run **Deploy ROYAL+ Weather Service** manually from GitHub Actions.
8. Record the resulting `https://royalplus-weather-api.<account-subdomain>.workers.dev` URL. The Android client migration and visible attribution must be completed and tested before the gateway becomes the app's active weather source.

The deploy workflow runs only from `main`. No service has been deployed or activated by this PR change.

## Local tests

```sh
npm test
```

## Provider and platform terms

- Open-Meteo Terms of Use: https://open-meteo.com/en/terms
- Open-Meteo pricing and free API limits: https://open-meteo.com/en/pricing
- Open-Meteo attribution: https://open-meteo.com/
- MET Norway API: https://api.met.no/
- MET Norway Locationforecast documentation: https://api.met.no/weatherapi/locationforecast/2.0/documentation
- MET Norway Terms of Service: https://docs.api.met.no/doc/TermsOfService
- CC BY 4.0: https://creativecommons.org/licenses/by/4.0/
- Cloudflare Workers Free limits: https://developers.cloudflare.com/workers/platform/limits/
- Cloudflare Workers KV pricing: https://developers.cloudflare.com/kv/platform/pricing/
- Cloudflare Durable Objects pricing: https://developers.cloudflare.com/durable-objects/platform/pricing/
