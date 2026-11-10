# Mobile plan

What the apps do now, what comes next, and what the backend
([HeyNaija/heynaija](https://github.com/HeyNaija/heynaija)) has to add first.

## Now

- Android and iOS apps from one Kotlin Multiplatform project ([ADR 0001](adr/0001-kotlin-multiplatform.md)).
- HeyNaija's look: the web app's colour tokens in light and dark, Plus Jakarta
  Sans, the wordmark, and the HN mark as the app icon. Text follows the phone's
  own size setting, one step smaller on small screens.
- **Signing in and joining** with an emailed code, through Kratos's native
  flows (ADR 0022 in the web repository), with Apple on iOS (below), or with
  Google. On iOS, Google's page opens in iOS's sign-in sheet as the app's own
  iOS OAuth client (PKCE and a nonce, no Google SDK), whose ID tokens Kratos
  takes as an extra audience. On Android:
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
- **Sign in with Apple on iOS** (ADR 0026 in the web repository): Apple's own
  button and sheet give an ID token for the app, sent with its nonce to Kratos's
  native flow as Google's is on Android, under the same account policy (an
  address that already has an account confirms an emailed code first; joining
  takes an invite). Apple's one-time code then goes to `POST /v1/apple/token`, so
  the server can revoke Apple's token when the account is erased, as Apple
  requires. The web offers no Apple button; Settings there can disconnect it.
- **Push notifications on iOS** (ADR 0026 in the web repository), through Apple's
  push service directly, with no Firebase. Offered and asked for as on Android.
  The server seals each message as for Android; Apple carries only "You have a
  new notification" and `sealed`, and the notification service extension
  (`iosApp/NotificationService`) opens it with the key the app keeps in the
  Keychain of the app group `group.ng.heynaija.app`, and shows its words, or
  keeps the stand-in when it is another member's (the member signed in is in the
  group's defaults). Tapping it opens its link in the app. A build run from Xcode
  registers as `apns_sandbox`, an App Store or TestFlight build as `apns`.
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
- **Age checks with the app store** where a law requires them (Texas's so far):
  before signing in or joining, Apple's Declared Age Range on iOS 26.2 and later,
  through a Swift wrapper (`AgeRange.swift`, as the API is Swift-only), and Google
  Play's Age Signals on Android. Under 18, or not confirmed where a law asks:
  they cannot go on. Elsewhere the app's own "I am 18 or older" stands
  ([release.md](release.md#age-checks)).
- **Search** for people, communities and posts, with trending topics.
- **Settings:** profile name, bio and photo; deleting the account (with the
  sign-in again the API asks for) or calling it off; links to the rules and
  privacy notice. Changing the email or handle is on the web for now.

## What the backend still needs

1. **Session tokens on staging.** Done in HeyNaija/heynaija#107 (API reads
   `Authorization: Bearer`, signup reads `transient_payload`), waiting to merge.
2. **Links that open the app.** HeyNaija/heynaija#140: the site serves
   `/.well-known/apple-app-site-association` (iOS Universal Links) and
   `/.well-known/assetlinks.json` (Android App Links) for the pages the app has
   a screen for (`apps/web/lib/app-links.ts` there). Android's also needs
   production's `ANDROID_CERT_SHA256` once Play has signed the app
   ([release.md](release.md#5-google-play-once)).

## Next in the app

1. Gists with the phone locked or another app open. Today the sound can stop,
   and the room says to keep the screen open, as the web does. Listening on
   needs iOS's audio background mode, and on Android a foreground service with a
   notification (of the microphone type while speaking, from Android 14).
   The web's Connection sheet (route, loss and jitter) and microphone settings
   are not in the room yet.
2. The ideas board, and continuing a video upload after the app was closed.
3. The stores: [release.md](release.md) has every step, what is in the
   repository (release signing, the Release workflow, privacy manifests) and what
   only the account holders can do. LiveKit's WebRTC library makes the Android
   debug APK about 75 MB with every processor type in it; the Play Store's app
   bundles ship only the one a phone needs (the release bundle is about 44 MB).
   LiveKit's camera and screen-sharing permissions are taken out of the manifest,
   as Gists use neither.

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

iOS signs in as its own client, type iOS, bundle ID `ng.heynaija.app`
(`567185521629-68a94t7k…`, `Server.kt`): Google's page in iOS's sign-in sheet,
coming back to the client's reversed ID. Its tokens are for that client, so
Kratos lists it in the Google provider's `additional_id_token_audiences` (the
web repository's compose files). One client serves every build.
