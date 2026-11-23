# ROYAL+ Weather

Weather-provider requests from ROYAL+ Launcher pass through the dedicated Supabase Edge Function `royalplus-weather` in the existing ROYAL+ project. The gateway uses a shared server-side response cache and one atomic database counter for all launcher devices. No Cloudflare Worker, paid gateway, separate weather project, or paid server is used.

## Providers

- **Primary:** Open-Meteo Forecast API, used under the written permission received for the described free, non-commercial, ad-free ROYAL+ Launcher use.
- **Fallback:** MET Norway Locationforecast, used if Open-Meteo is unavailable or returns unusable data.
- **Geocoding:** Open-Meteo Geocoding API for city search.

The Android app keeps a separate rolling forecast cache for each saved city. Cached weather remains available offline; if both providers fail, the app keeps the last valid saved forecast rather than clearing it. When MET Norway is used, the app resolves the selected city's timezone and converts its UTC forecast timestamps into local calendar days. If timezone lookup is unavailable, it falls back to the device timezone. Older cached forecasts that predate provider metadata remain available offline but are explicitly labelled as legacy data with an unrecorded original provider; the app does not guess which provider supplied them. Weather videos remain local to the device.

## Attribution and license

The forecast screen identifies the actual provider used and links to the provider and its license. Attribution is stored with each city's local cache so it remains visible offline.

- Open-Meteo: Weather data by [Open-Meteo](https://open-meteo.com/), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- MET Norway: Weather data by [MET Norway](https://api.met.no/), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

Do not imply either provider endorses ROYAL+.

## Shared gateway, cache, and quota safeguards

- All Open-Meteo forecast, timezone, and geocoding requests are routed through the dedicated `royalplus-weather` Edge Function.
- All MET Norway forecast requests also pass through the gateway, with a three-hour shared cache per normalized location.
- Open-Meteo responses are shared across devices for three hours; geocoding responses are shared for 30 days.
- A database RPC atomically reserves each actual upstream Open-Meteo request. The hard ceiling is **9,550 requests per UTC day across every ROYAL+ Launcher device**. The 9,551st request is rejected before reaching Open-Meteo.
- Cache hits do not consume the Open-Meteo request allowance. Failed upstream attempts do consume a reserved slot, preventing retries from bypassing the ceiling.
- The Android worker checks cached saved-city forecasts every 30 minutes, but the local and shared three-hour freshness windows prevent a provider request every 30 minutes. MET Norway is contacted only when a refresh is due and the primary provider cannot provide valid data.
- The app retains each city's valid forecast locally for seven days and continues showing the last saved data if both providers or the gateway are unavailable.
- No Cloudflare, paid API, subscription, paid server, paid database, paid storage, payment method, or automatic upgrade is used.

The 9,550 figure is the application's own enforced ceiling, not a claim that either provider authorizes that exact volume. Provider terms, written permission, attribution, rate limits, and service availability still apply. If the provider's terms or written permission require a lower limit, the lower limit must be enforced.

## Provider terms

- [Open-Meteo Terms of Use](https://open-meteo.com/en/terms)
- [Open-Meteo pricing and free API limits](https://open-meteo.com/en/pricing)
- [MET Norway API](https://api.met.no/)
- [MET Norway Locationforecast documentation](https://api.met.no/weatherapi/locationforecast/2.0/documentation)
- [MET Norway Terms of Service](https://docs.api.met.no/doc/TermsOfService)
- [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)
