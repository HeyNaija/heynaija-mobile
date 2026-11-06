# HeyNaija for Android and iOS

The native apps for [HeyNaija](https://heynaija.ng), the Nigeria-first social
network built toward community ownership. The web app, API and everything
behind them are in [HeyNaija/heynaija](https://github.com/HeyNaija/heynaija).

**Status:** early. The apps show the Latest feed from staging, read-only and
signed out. [docs/plan.md](docs/plan.md) has what comes next, and what the
backend has to add before members can sign in.

## How it fits together

One Kotlin Multiplatform project, with the UI written once in Compose
Multiplatform ([ADR 0001](docs/adr/0001-kotlin-multiplatform.md)).

| Path | What |
|---|---|
| `shared/src/commonMain` | Everything both apps share: screens, state, the API client, the theme |
| `shared/src/androidMain`, `iosMain` | The few things that differ per platform |
| `shared/src/commonTest` | Tests, run on the JVM and on the iOS simulator |
| `androidApp` | The Android app: one Activity that shows the shared UI |
| `iosApp` | The Xcode project: a SwiftUI app that shows the shared UI |

Inside `shared/src/commonMain/kotlin/ng/heynaija/app`:

- `api/`: the HeyNaija API over Ktor, and the JSON it sends (`Models.kt`, after
  `apps/web/lib/api.ts`). `Server.kt` says which HeyNaija the app talks to.
- `theme/`: the web app's colour tokens, type scale and radii
  (`apps/web/app/globals.css`, `apps/web/DESIGN.md`). Change them on the web first.
- `ui/`: shared pieces: post card, avatar, icons, times.
- `feed/`: the Latest feed: a ViewModel and its screen.

Libraries: Ktor (HTTP), kotlinx.serialization (JSON), Coil (images),
kotlinx-datetime, and AndroidX Lifecycle (ViewModels).

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

The app talks to staging (`https://staging.heynaija.ng`). Staging's Latest feed
is public, so nothing needs to run locally. To point it at another server,
change `ORIGIN` in `shared/src/commonMain/kotlin/ng/heynaija/app/api/Server.kt`.

## Licence

AGPL-3.0-or-later, as the main repository: [LICENSE](LICENSE). Plus Jakarta Sans
is under the SIL Open Font License and the icons are Lucide's (ISC), see
[licenses/](licenses/README.md). Shipping to the App Store raises a licence
question that is still open: [docs/plan.md](docs/plan.md#decisions-still-open).
