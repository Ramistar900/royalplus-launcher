# ROYAL+ Weather

Weather data is fetched directly by ROYAL+ Launcher. No Cloudflare Worker, gateway, shared server, KV namespace, Durable Object, or server deployment is required.

## Providers

- **Primary:** Open-Meteo Forecast API, used under the written permission received for the described free, non-commercial, ad-free ROYAL+ Launcher use.
- **Fallback:** MET Norway Locationforecast, used if Open-Meteo is unavailable or returns unusable data.
- **Geocoding:** Open-Meteo Geocoding API for city search.

The Android app keeps a separate rolling forecast cache for each saved city. Cached weather remains available offline; if both providers fail, the app keeps the last valid saved forecast rather than clearing it. Weather videos remain local to the device.

## Attribution and license

The forecast screen identifies the actual provider used and links to the provider and its license. Attribution is stored with each city's local cache so it remains visible offline.

- Open-Meteo: Weather data by [Open-Meteo](https://open-meteo.com/), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- MET Norway: Weather data by [MET Norway](https://api.met.no/), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

Do not imply either provider endorses ROYAL+.

## Cost and quota safeguards

- No Cloudflare account or deployment.
- No paid API, subscription, server, database, storage, or payment method.
- Use only the providers' free public endpoints under their applicable terms and limits.
- Keep request frequency conservative and use the local cache to avoid unnecessary requests.
- If a provider's free allowance or service is unavailable, retain local data and retry later; never switch to a paid provider.

Free-provider availability and quotas are controlled by the providers and may change. The app must not create paid usage or automatically upgrade any service.

## Provider terms

- [Open-Meteo Terms of Use](https://open-meteo.com/en/terms)
- [Open-Meteo pricing and free API limits](https://open-meteo.com/en/pricing)
- [MET Norway API](https://api.met.no/)
- [MET Norway Locationforecast documentation](https://api.met.no/weatherapi/locationforecast/2.0/documentation)
- [MET Norway Terms of Service](https://docs.api.met.no/doc/TermsOfService)
- [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)
