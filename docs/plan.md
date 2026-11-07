# Mobile plan

What the apps do now, what comes next, and what the backend
([HeyNaija/heynaija](https://github.com/HeyNaija/heynaija)) has to add first.

## Now

- Android and iOS apps from one Kotlin Multiplatform project ([ADR 0001](adr/0001-kotlin-multiplatform.md)).
- HeyNaija's look: the web app's colour tokens in light and dark, Plus Jakarta
  Sans, the wordmark, and the HN mark as the app icon. Text follows the phone's
  own size setting, one step smaller on small screens.
- **Signing in and joining** with an emailed code, through Kratos's native
  flows (ADR 0022 in the web repository). Joining asks for the invite, the date
  of birth and the handle, as the web does. The session token is kept in the
  Android Keystore or the iOS Keychain. Members who joined before the age
  question answer it first, and newcomers get the welcome steps.
- **Feeds:** Discover, Latest and Following, as on the web; profiles (posts,
  replies, media, pinned post), communities (join, leave, rules, pinned post),
  hashtags and bookmarks.
- **Posts:** threads with the conversation above and replies below; posting
  with up to four pictures, a poll, a community and who can reply; replies,
  likes, reposts, quotes, bookmarks, sharing a link, pinning to your profile,
  deleting; mentions, hashtags and links in the text; link cards; pictures full
  screen with zoom; videos in the platform's own player.
- **Safety:** reporting posts and members, muting and blocking.
- **Notifications** in the app, with the count on the tab, and which kinds you
  get (and email) in Settings.
- **Search** for people, communities and posts, with trending topics.
- **Settings:** profile name, bio and photo; deleting the account (with the
  sign-in again the API asks for) or calling it off; links to the rules and
  privacy notice. Changing the email or handle is on the web for now.

## What the backend still needs

1. **Session tokens on staging.** Done in HeyNaija/heynaija#107 (API reads
   `Authorization: Bearer`, signup reads `transient_payload`), waiting to merge.
2. **Google sign-in, and Sign in with Apple.** On a phone, Google sign-in
   gives an ID token, which Kratos accepts in its native flows. **App Store
   Review Guideline 4.8:** an iOS app that offers Google sign-in must also offer
   Sign in with Apple (or a sign-in that meets the same privacy rules). Signing
   in by emailed code alone needs neither, so the apps offer only that for now.
3. **Push to phones.** ADR 0011 covers web push only. The apps need endpoints
   to register an FCM token (Android) or an APNs token (iOS), and the API needs
   to send through FCM and APNs. That needs a Firebase project and an Apple
   push key first.
4. **Links that open the app.** For `heynaija.ng/p/...` links to open in the
   app, the web app has to serve `/.well-known/assetlinks.json` (Android App
   Links) and `/.well-known/apple-app-site-association` (iOS Universal Links).

## Next in the app

1. Push notifications (after 3 above).
2. Posting a video: picked, checked against `videoLimits` from `/v1/me`, and
   uploaded with the same signed-upload API as the web (ADR 0021).
3. Gists, with LiveKit's Android and Swift SDKs.
4. @mention suggestions while writing, and the ideas board.
5. Opening shared links in the app (after 4 above).
6. Store listings: Play Console and App Store Connect accounts, privacy labels,
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
