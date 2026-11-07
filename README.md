# HeyNaija for Android and iOS

The native apps for [HeyNaija](https://heynaija.ng), the Nigeria-first social
network built toward community ownership. The web app, API and everything
behind them are in [HeyNaija/heynaija](https://github.com/HeyNaija/heynaija).

**Status:** in development, not in the stores yet. Members can sign in and
join with an invite, read the feeds, threads, profiles and communities, post
with pictures, polls and video, reply, like, repost, quote, bookmark, search,
get notifications in the app and, on Android, as push notifications, and listen
and speak in Gists, the live audio rooms. [docs/plan.md](docs/plan.md) has what
is done, what is left (push on iOS, Gists in the background), and what the
backend still needs.

## How it fits together

One Kotlin Multiplatform project, with the UI written once in Compose
Multiplatform ([ADR 0001](docs/adr/0001-kotlin-multiplatform.md)).

| Path | What |
|---|---|
| `shared/src/commonMain` | Everything both apps share: screens, state, the API client, the theme |
| `shared/src/androidMain`, `iosMain` | The few things that differ per platform |
| `shared/src/commonTest` | Tests, run on the JVM and on the iOS simulator |
| `androidApp` | The Android app: one Activity that shows the shared UI |
| `iosApp` | The Xcode project: a SwiftUI app that shows the shared UI, and the Swift side of Gists (`GistAudio.swift`) |

Inside `shared/src/commonMain/kotlin/ng/heynaija/app`:

- `api/`: the HeyNaija API over Ktor (`HeyNaijaApi.kt`), Kratos's native
  sign-in flows (`Kratos.kt`), and the JSON they send (`Models.kt`, after
  `apps/web/lib/api.ts`). `Server.kt` says which HeyNaija the app talks to.
- `AppState.kt`: the session, the API, navigation, and the changes every screen
  takes up (a like, a delete, a new post). Screens reach it through `LocalApp`.
- `nav/`: a back stack per tab, with each screen's ViewModels kept until it is popped.
- `platform/`: what each platform provides: the token store (Android Keystore,
  iOS Keychain), the photo picker, the share sheet, the video player, and a
  Gist's sound (`GistAudio`: LiveKit's Android SDK in `androidMain`; on iOS the
  Swift wrapper in `iosApp`, because LiveKit's Swift SDK has no Objective-C
  interface Kotlin could call, passed in through `MainViewController`).
- `theme/`: the web app's colour tokens and radii (`apps/web/app/globals.css`),
  and the type scale for phones (`Type.kt` says how it differs from the web's).
- `ui/`: shared pieces: post card, post text with links, poll, avatar, icons, times.
- One folder per area: `feed/`, `thread/`, `profile/`, `communities/`,
  `search/`, `notifications/`, `compose/`, `auth/` (sign in, join, the age
  question, the welcome steps), `settings/`, `media/`, `report/`, `gists/` (Live
  now, the room, and its rules after the web's `lib/gists.ts`).

Libraries: Ktor (HTTP), kotlinx.serialization (JSON), Coil (images),
kotlinx-datetime, AndroidX Lifecycle (ViewModels), Media3 for video on
Android (AVKit on iOS), and LiveKit for Gists (its Android SDK through Gradle,
its Swift SDK as a Swift package in the Xcode project; Xcode fetches it on the
first build).

## Run it

You need JDK 17 or later, Android Studio with the Android SDK, and, for iOS,
Xcode on a Mac. Gradle downloads everything else, including the JDK it runs on.

Android, on a running emulator or a connected phone:

```bash
./gradlew :androidApp:installDebug
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator and press Run.
The Xcode build calls Gradle to build the shared framework. Or open the whole
folder in Android Studio with the Kotlin Multiplatform plugin and use its run
configurations.

Tests:

```bash
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
```

The app talks to staging (`https://staging.heynaija.ng`). Signing in needs the
API to accept session tokens (HeyNaija/heynaija#107); until that is on staging,
use the dev stack on your computer.

### Against the dev stack

A debug build has a **Server** choice in Settings (the gear on Home): Staging,
or This computer. "This computer" expects the web repository's dev stack
(`pnpm infra:up` and `pnpm dev:api`): the API on port 4000, Kratos on 4533 and
the media bucket on 9100. The iOS simulator reaches them as they are. The
Android emulator needs the ports forwarded first:

```bash
for port in 4000 4533 9100 7880 7881; do adb reverse tcp:$port tcp:$port; done
```

7880 and 7881 are LiveKit, for Gists (`pnpm infra:livekit` starts it, and the
API must run with `APP_ENV=development`, as `pnpm dev:api` does, or Gists are
off). `adb reverse` carries TCP only, so the emulator's sound goes over
LiveKit's TCP port rather than UDP; that is fine on one computer. The
emulator's microphone sends silence unless it is given the computer's
microphone, and the iOS simulator can only speak if macOS lets Simulator use the
microphone (System Settings, Privacy & Security, Microphone).

Sign-in codes land in Mailpit, at http://127.0.0.1:8026. Make an invite with
`pnpm invite:create` in the web repository.

Push on the emulator needs a Google Play system image, `google-services.json`
at `androidApp/google-services.json` (from the Firebase console; git ignores it),
and the dev API started with `FCM_SERVICE_ACCOUNT` set to the project's service
account key in base64 (`docs/deployment/app-push.md` in the web repository).

## Licence

AGPL-3.0-or-later, as the main repository: [LICENSE](LICENSE). Plus Jakarta Sans
is under the SIL Open Font License and the icons are Lucide's (ISC), see
[licenses/](licenses/README.md). Shipping to the App Store raises a licence
question that is still open: [docs/plan.md](docs/plan.md#decisions-still-open).
