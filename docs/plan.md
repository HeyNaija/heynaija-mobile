# Mobile plan

What the apps do now, what comes next, and what the backend
([HeyNaija/heynaija](https://github.com/HeyNaija/heynaija)) has to add first.

## Now

- Android and iOS apps from one Kotlin Multiplatform project ([ADR 0001](adr/0001-kotlin-multiplatform.md)).
- HeyNaija's look: the web app's colour tokens in light and dark, Plus Jakarta
  Sans, the wordmark, and the HN mark as the app icon.
- **Latest, read-only and signed out**, from staging: post cards with names,
  handles, times, communities, images, video posters, quotes and counts, pull to
  refresh, and the next page loaded before you reach the end.

## What the backend needs before members can sign in

1. **Session tokens, not only cookies.** Kratos's native flows
   (`/self-service/login/api`, `/self-service/registration/api`) return a
   `session_token` instead of setting a cookie. The API only reads the
   `hn_session` cookie (`authRequest` in `apps/api/src/auth/auth.service.ts`,
   `whoami` in `kratos.service.ts`). It needs to accept
   `Authorization: Bearer <token>` too and pass it to Kratos as
   `X-Session-Token`. ADR 0012 already puts this in one place, `AuthService`.
2. **Invite code and date of birth without cookies.** `infra/kratos/invite-check.jsonnet`
   reads them from the `hn_invite` and `hn_age` cookies that `/join` sets. The
   app has to send them in the registration flow's `transient_payload`, and the
   webhook has to read them from there as well.
3. **Google sign-in, and Sign in with Apple.** On a phone, Google sign-in
   gives an ID token, which Kratos accepts in its native flows. **App Store
   Review Guideline 4.8:** an iOS app that offers Google sign-in must also offer
   Sign in with Apple (or a sign-in that meets the same privacy rules). Signing
   in by emailed code alone needs neither.
4. **Push to phones.** ADR 0011 covers web push only. The apps need endpoints
   to register an FCM token (Android) or an APNs token (iOS), and the API needs
   to send through FCM and APNs.
5. **Links that open the app.** For `heynaija.ng/p/...` links to open in the
   app, the web app has to serve `/.well-known/assetlinks.json` (Android App
   Links) and `/.well-known/apple-app-site-association` (iOS Universal Links).

## Next in the app, roughly in order

1. Sign in by emailed code (after 1 above), with the session token in the
   platform's secure storage (Android Keystore, iOS Keychain).
2. Threads, profiles and communities; tappable mentions, hashtags and links.
3. Posting: text, then images (the same signed-upload API as the web), then
   video, shrunk on the phone before upload (ADR 0021).
4. Video playback with Media3 ExoPlayer and AVPlayer, behind one interface.
5. Notifications, with push (after 4 above).
6. Gists, with LiveKit's Android and Swift SDKs.
7. Store listings: Play Console and App Store Connect accounts, privacy labels,
   age rating (18 and over during the beta), and signing keys kept out of the repo.

## Decisions still open

- **App IDs.** Android `applicationId` and iOS bundle ID are both
  `ng.heynaija.app`. Once a store listing exists they cannot change.
- **Licence on the App Store.** This repo is AGPL-3.0-or-later, like the main
  one. Apple's terms add restrictions the GPL family forbids, so only the
  copyright holders can ship it there (Signal ships its AGPL iOS app the same
  way). Outside contributions then need a contributor agreement, or a licence
  exception that lets HeyNaija distribute through the App Store. Get advice on
  this before taking outside pull requests.
