# ROYAL+ multilingual translation fallback

This function is the authenticated text-translation fallback for languages that are
not available in the on-device ML Kit translation models.

- Audio capture and Whisper speech recognition stay on the device.
- The app sends recognized caption text only.
- The function validates the Supabase user JWT.
- Google Cloud Translation detects the source language automatically and translates
  to Arabic.
- The Google API key is stored only as a Supabase Edge Function secret.

## One-time provider setup

1. In Google Cloud Console, select a billing-enabled project and enable **Cloud Translation API**.
2. Create an API key restricted to the Cloud Translation API. Add application/API restrictions
   and set appropriate daily quotas/budget alerts in Google Cloud.
3. In Supabase Dashboard, open project `ymujjcdsdclptbpgckll` → Edge Functions → Secrets.
4. Add the secret named `GOOGLE_TRANSLATE_API_KEY` with the restricted Google API key.
5. Deploy this function with JWT verification enabled:

   `supabase functions deploy royalplus-translate --project-ref ymujjcdsdclptbpgckll`

The Android client uses the existing anonymous Supabase Auth session and publishable
project key. Do not place the Google key or a Supabase secret/service-role key in the APK.

## Coverage note

Automatic language detection removes the app's hard-coded language allowlist. Actual
coverage still depends on the recognition model identifying the spoken language and
Google Cloud Translation supporting that language. It cannot promise every language
or every dialect in existence.
