# Releasing to Google Play and the App Store

Everything that has to be done, once, to get the apps into the stores, and then
for each release. What only the account holders can do (consoles, keys, forms)
is marked **you**; the rest is in the repositories.

## 1. The server first

Production has to be ready before a reviewer opens the app.

- [ ] Deploy the web repository's iOS push and Sign in with Apple change (ADR 0026
      there: migration 0044 and the rebuilt Kratos image).
- [ ] **You:** the Apple key on production and staging: `APPLE_TEAM_ID`,
      `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY` (`docs/deployment/sign-in-with-apple.md`
      in the web repository). It also needs `GOOGLE_SIGN_IN=true`, which switches
      on Sign in with Apple in Kratos.
- [ ] **You:** Apple's email relay: an SPF record on `mail.heynaija.ng`, then
      `mail.heynaija.ng` and `no-reply@mail.heynaija.ng` registered for Sign in
      with Apple (same document, step 4). Without it, members who hide their
      email get no sign-in codes.
- [ ] **You:** `FCM_SERVICE_ACCOUNT` on production, for Android push
      (`docs/deployment/app-push.md`).
- [ ] **You:** the review account on production, with a feed to look at
      (`docs/deployment/store-review.md`).

## 2. Apple developer account (once)

- [ ] **You:** App ID `ng.heynaija.app` with **Push Notifications**, **Sign In
      with Apple**, **Declared Age Range**, **Associated Domains** (links that
      open the app) and **App Groups** (`group.ng.heynaija.app`); and
      `ng.heynaija.app.NotificationService` with **App Groups**. Xcode's automatic
      signing makes both, and the group, once the team is set below.
- [ ] Put the team ID in `iosApp/Configuration/Config.xcconfig` (`TEAM_ID=`). It
      is not secret: every signed app carries it.

## 3. App Store Connect (once)

**You**, at [App Store Connect](https://appstoreconnect.apple.com) → Apps → +:

- Name **HeyNaija**, primary language English (U.K.), bundle ID `ng.heynaija.app`,
  SKU `heynaija-ios`.
- **App Information:** category Social Networking. Content rights: the app shows
  what members post. License agreement: the standard one, or the terms at
  `https://heynaija.ng/terms`.
- **Age rating:** answer the questionnaire honestly; the beta is 18+, and members
  post and talk to each other (user-generated content, messaging). Social media
  features: **Yes**. Social media turned off for users under 13: **No** (that
  answer is about checking everyone's age with Apple; the app asks only where a
  law requires it). Age assurance: **Yes**, Apple's Declared Age Range, where a
  law requires it (see "Age checks" below).
- **App Privacy:** privacy policy `https://heynaija.ng/privacy`. Data collected,
  all *linked to the person*, *not used for tracking*, for *App Functionality*:
  Contact Info → Email Address and Name (the display name, if they add one);
  Identifiers → User ID; User Content → Photos or Videos, and Other User Content
  (posts and replies). Nothing for tracking, advertising or analytics. Gists' sound
  passes through the server and is not kept, so it is not "collected" in Apple's
  sense. `iosApp/iosApp/PrivacyInfo.xcprivacy` says the same.
- **Pricing:** free. **Availability:** everywhere, the United States included:
  the app checks age with Apple where a US state's law requires it.
- **Support URL:** `https://heynaija.ng/privacy#rights`, which gives the contact
  address (Guideline 1.2 asks apps with posts to publish one); a support page of
  its own would be better. **Marketing URL:** `https://heynaija.ng/about`.

For each version:

- Screenshots: 6.9-inch iPhone (1320 × 2868) at least. The app is iPhone only
  (`TARGETED_DEVICE_FAMILY = 1`), so no iPad screenshots are needed; it still runs
  on iPads, scaled.
- **App Review Information:** sign-in required, with the review account's address
  as the user name and its code as the password, and the note from
  `docs/deployment/store-review.md`. Add: "HeyNaija is invite-only during its
  beta. Members can report posts and accounts, block and mute, and the rules are
  at heynaija.ng/rules. Accounts are deleted from Settings → Delete your account."
- **Export compliance:** asked for each build. The app uses HTTPS, Apple's own
  AES-GCM (CryptoKit) for push, and WebRTC's DTLS-SRTP for Gists' sound, all
  standard algorithms. Decide the answer once (with advice if unsure), then add
  `ITSAppUsesNonExemptEncryption` to `iosApp/iosApp/Info.plist` so builds stop
  asking.

## 4. An iOS build

1. Raise `CURRENT_PROJECT_VERSION` in `Config.xcconfig` (every upload needs a new
   one), and `MARKETING_VERSION` for a new version. `MARKETING_VERSION` is the
   version both stores show: the Android build reads it from there too, so the
   two apps always match.
2. Xcode → iosApp scheme → **Any iOS Device (arm64)** → Product → **Archive**.
3. Organizer → **Distribute App** → App Store Connect → Upload. Automatic signing
   makes the distribution profiles for the app and its extension.
4. In App Store Connect it appears under TestFlight a few minutes later. Test it
   there (push and Sign in with Apple use the production services in TestFlight
   builds), then add it to the version and **Submit for Review**.

## 5. Google Play (once)

**You**, at [Play Console](https://play.google.com/console):

- **Create app:** HeyNaija, English (United Kingdom), app, free.
- **The upload key.** Play signs the app with its own key (Play App Signing); you
  sign uploads with an upload key, made once and kept in the password manager:

  ```bash
  keytool -genkeypair -v -keystore heynaija-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
  ```

  Then give it to the Release workflow as secrets:

  ```bash
  base64 -i heynaija-upload.jks | gh secret set UPLOAD_KEYSTORE --repo HeyNaija/heynaija-mobile
  ```

  and `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS` (`upload`) and
  `UPLOAD_KEY_PASSWORD` the same way (`gh secret set NAME`, then paste).
- **Google sign-in:** after the first upload, Play Console → Test and release →
  App integrity shows Play's signing certificate. Its SHA-1, and the upload key's
  (`keytool -list -v -keystore heynaija-upload.jks`), each need an Android OAuth
  client in Google Cloud ([plan.md](plan.md#google-sign-in)), or Google sign-in
  fails in the Play build.
- **Links that open the app:** nothing to do. Play's signing certificate is in
  the web repository's `apps/web/lib/app-links.ts` (HeyNaija/heynaija#141), and
  links open in copies installed from Play. A copy not from Play (the Release
  workflow's `.aab`, a debug build) asks first: `docs/deployment/app-links.md`
  there.
- **App content** (Policy → App content):
  - Privacy policy: `https://heynaija.ng/privacy`.
  - App access: the review account (`docs/deployment/store-review.md`).
  - Ads: no ads.
  - Content rating: the IARC questionnaire, as a social app where people
    communicate and share content.
  - Target audience: 18 and over.
  - Data safety: collected and shared as for Apple above (email, name, user ID,
    photos and videos, other user-generated content), all for app functionality,
    encrypted in transit, and deletable: in the app (Settings → Delete your
    account) and on the web. **Delete account URL:** `https://heynaija.ng/privacy#rights`,
    which says how, links to Settings → Delete your account, and gives the
    address to write to without signing in.
  - News app, health, financial features, government: no.
- **Testing first.** A personal developer account made after November 2023 must
  run a closed test with at least 12 testers for 14 days before it can publish to
  production. An organisation account can go straight on.

## 6. An Android build

1. Actions → **Release** → Run workflow (or push a `v*` tag). It builds the app
   bundle signed with the upload key, with the run number as its version code and
   `MARKETING_VERSION` from `iosApp/Configuration/Config.xcconfig` as its version
   name, and fails if a secret is missing.
2. Download the `heynaija-android-<run>` artifact, and upload the `.aab` in Play
   Console → Test and release → Internal testing (then closed, then production).

On a computer instead: the same four settings as `heynaija.upload.keystore`,
`heynaija.upload.store_password`, `heynaija.upload.key_alias` and
`heynaija.upload.key_password` in `~/.gradle/gradle.properties`, then
`./gradlew :androidApp:bundleRelease -PversionCode=<n>`.

## Age checks

Some places' laws (Texas's so far, with Utah's and Louisiana's to follow) make an
18+ app check age with the app store's own signal, not a box the person ticks.
Before signing in or joining, the app asks the store:

- **iOS 26.2 and later:** Apple's Declared Age Range, only where Apple says a law
  requires it (`isEligibleForAgeFeatures`). Apple asks the person to share their
  age range with HeyNaija. Under 18, or not shared: they cannot sign in or join,
  and the app says why. Elsewhere, and on older iOS, nobody is asked. Needs the
  **Declared Age Range** capability on the App ID (the entitlement is in the app).
- **Android:** Google Play's Age Signals (beta, `age-signals` 0.0.4). Where a law
  requires it Play shares the age band, or says the person has to confirm their
  age with Play first (they cannot sign in or join until they do). Elsewhere it
  depends on the person's own Play setting: Play may ask once whether to share,
  and not sharing is their choice. Nothing to switch on in Play Console. A build
  not installed from Play (or a phone without Play) cannot ask, and goes on.
- Everywhere, the age range stays on the phone: it is not sent to HeyNaija, so it
  is not "collected" in either store's privacy forms.
- The answer that lets someone in is kept while the app is open. Members already
  signed in are not asked again until they next sign in.

Apple's and Google's own words leave whether this is enough to counsel; it is
what both stores' age APIs offer an 18+ app.

## Not needed for the first release

- Gists with the phone locked.
