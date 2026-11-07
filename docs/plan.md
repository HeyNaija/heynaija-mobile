# Mobile plan

What the apps do now, what comes next, and what the backend
([HeyNaija/heynaija](https://github.com/HeyNaija/heynaija)) has to add first.

## Now

- Android and iOS apps from one Kotlin Multiplatform project ([ADR 0001](adr/0001-kotlin-multiplatform.md)).
- HeyNaija's look: the web app's colour tokens in light and dark, Plus Jakarta
  Sans, the wordmark, and the HN mark as the app icon. Text follows the phone's
  own size setting, one step smaller on small screens.
- **Signing in and joining** with an emailed code, through Kratos's native
  flows (ADR 0022 in the web repository), or on Android with Google:
  Credential Manager's "Sign in with Google" gives an ID token for Kratos's
  Google client, sent with its nonce to the native flow. A Google address that
  already has an account confirms an emailed code once and Google is connected
  (the web repository's Kratos policy); one nobody has joined with is told that
  joining takes an invite, and joining with Google takes the handle from the
  form and the email from Google. Google needs an Android OAuth client in the
  same Google Cloud project as the web client, for the package `ng.heynaija.app`
  and each signing certificate's SHA-1 (see "Google sign-in" below). Joining asks for the invite, the date
  of birth and the handle, as the web does. The session token is kept in the
  Android Keystore or the iOS Keychain. Members who joined before the age
  question answer it first, and newcomers get the welcome steps.
- **Feeds:** Discover, Latest and Following, as on the web; profiles (posts,
  replies, media, pinned post), communities (join, leave, rules, pinned post),
  hashtags and bookmarks.
- **Posts:** threads with the conversation above and replies below; posting
  with up to four pictures, a video (sent in parts, then waited on until the
  worker has it ready), a poll, a community and who can reply; @mention
  suggestions while writing; replies,
  likes, reposts, quotes, bookmarks, sharing a link, pinning to your profile,
  deleting; mentions, hashtags and links in the text; link cards; pictures full
  screen with zoom; videos in the platform's own player.
- **Safety:** reporting posts and members, muting and blocking.
- **Gists**, the live audio rooms (ADR 0017 in the web repository), for members
  the `gists` flag lets in: Live now on Home, starting one from the bar, and the
  room as on the web: host, stage and listeners, joining to listen, asking to
  speak, the microphone (asked for only then), and the host's and co-hosts'
  controls (let speak, mute, make co-host, back to the listeners, remove, lock,
  requests on or off, end), and reporting the Gist. The room follows the web's join, reconnect and leave
  rules (`gist-session.ts`) and polls as it does. Sound goes through LiveKit:
  its Android SDK, and on iOS its Swift SDK behind a small Swift wrapper in
  `iosApp` (`GistAudio.swift`) that implements the shared `GistAudio` interface.
  The screen stays awake while in a room; the sound keeps playing on other
  screens of the app and stops when the member goes back out of the room.
  Replies to a Gist use the post card and composer (text only, liked through the
  Gist, hidden by its host), and a reply's notification opens it in its Gist.
- **Notifications** in the app, with the count on the tab, and which kinds you
  get (and email) in Settings.
- **Push notifications on Android** (ADR 0023 in the web repository), through
  Firebase Cloud Messaging. Offered once at the top of Notifications and in
  Settings, never asked for at start; the system's permission is asked for only
  then (Android 13 and later). The server seals each message with a key the app
  made, so Firebase carries it without reading it; the app opens it, drops one
  that is not for the member signed in, shows it (a newer one about the same post
  replaces it) and opens its link in the app when tapped. Signing out takes the
  phone off the server and gives up the token. The build reads
  `androidApp/google-services.json`, which is not in git: CI writes it from the
  `GOOGLE_SERVICES_JSON` secret, and a build without it has no push.
- **Posts marked sensitive** (ADR 0020 in the web repository, the 18+ label),
  the way the web shows them: a picture or video in a marked post waits behind
  "Sensitive content" and a Show button, unless the member chose otherwise in
  Settings; the text is never covered. The composer offers "Mark as sensitive"
  once pictures or a video are attached, the post menu adds or takes off the
  author's own mark, Settings can mark everything a member posts, and a post can
  be reported as not marked. Only adults are sent marked posts; all of it is
  behind the API's `adultLabels` flag. Not drawn blurred: Android before 12
  cannot blur, so nothing of the media shows until Show.
- **Stories** (ADR 0024 in the web repository): a post plus the updates its
  author adds to it. Feeds show a story once, as its newest update with the
  first post on one line above it; the first post's screen has Follow story and
  the updates oldest first, joined by a curved line, the earlier ones folded;
  an update's own screen names its place. The author writes the next update
  from the box at the end of the story ("Add update 3…") or the post's menu,
  in the sheet replies use, over the story. Needs the web's `feat/stories` API.
- **Search** for people, communities and posts, with trending topics.
- **Settings:** profile name, bio and photo; deleting the account (with the
  sign-in again the API asks for) or calling it off; links to the rules and
  privacy notice. Changing the email or handle is on the web for now.

## What the backend still needs

1. **Session tokens on staging.** Done in HeyNaija/heynaija#107 (API reads
   `Authorization: Bearer`, signup reads `transient_payload`), waiting to merge.
2. **Sign in with Apple, then Google on iOS.** Android has Google. **App Store
   Review Guideline 4.8:** an iOS app that offers Google sign-in must also offer
   Sign in with Apple, so iOS offers the emailed code only until the Apple
   developer account exists. ADR 0022's "Not yet" in the web repository still
   says Google is not in the apps.
3. **Push to the iOS app.** Android's is ADR 0023 (FCM). iOS needs the Apple
   developer account, an APNs key, and a second push service (`apns`) in the
   API's `push_devices`.
4. **Links that open the app.** For `heynaija.ng/p/...` links to open in the
   app, the web app has to serve `/.well-known/assetlinks.json` (Android App
   Links) and `/.well-known/apple-app-site-association` (iOS Universal Links).

## Next in the app

1. Push notifications on iOS (after 3 above).
2. Gists with the phone locked or another app open. Today the sound can stop,
   and the room says to keep the screen open, as the web does. Listening on
   needs iOS's audio background mode, and on Android a foreground service with a
   notification (of the microphone type while speaking, from Android 14).
   The web's Connection sheet (route, loss and jitter) and microphone settings
   are not in the room yet.
3. The ideas board, and continuing a video upload after the app was closed.
4. Opening shared links in the app (after 4 above).
5. Store listings: Play Console and App Store Connect accounts, privacy labels,
   age rating (18 and over during the beta), and signing keys kept out of the repo.
   LiveKit's WebRTC library makes the Android debug APK about 75 MB with every
   processor type in it; the Play Store's app bundles ship only the one a phone needs.

## Decisions still open

- **App IDs.** Android `applicationId` and iOS bundle ID are both
  `ng.heynaija.app`. Once a store listing exists they cannot change.
- **Licence on the App Store.** This repo is AGPL-3.0-or-later, like the main
  one. Apple's terms add restrictions the GPL family forbids, so only the
  copyright holders can ship it there (Signal ships its AGPL iOS app the same
  way). Outside contributions then need a contributor agreement, or a licence
  exception that lets HeyNaija distribute through the App Store. Get advice on
  this before taking outside pull requests.

## Google sign-in

The app asks Google for an ID token whose audience is the web OAuth client
Kratos uses (`567185521629-rk76…`, the same for staging, production and the dev
stack; `Server.kt`). Google issues it only to an app it knows: in the Google
Cloud project that owns that client, **Clients → Create client → Android**, with
package `ng.heynaija.app` and the SHA-1 of the certificate the app is signed
with. One client per certificate:

- each computer's debug certificate (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`);
- the release certificate, and once the app is on Play, Play App Signing's
  certificate (Play Console → Test and release → App integrity).

Without one, Google's sheet opens and then answers with an error, and the app
says Google sign-in did not work. No secret is involved; the Android client has
none.
