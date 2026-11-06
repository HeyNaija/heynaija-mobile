# ADR 0001 — Kotlin Multiplatform with one Compose UI

**Date:** 6 November 2026 · **Status:** Accepted

## Decision

The Android and iOS apps are one Kotlin Multiplatform project. The screens are
written once in Compose Multiplatform (`shared/src/commonMain`), and so are the
API client, state and design tokens. Each platform has a thin shell: an Activity
(`androidApp`) and a SwiftUI app hosting the Compose view controller (`iosApp`).
Anything that needs a platform's own SDK sits behind a small Kotlin interface
with an Android and an iOS implementation.

## Why

- **Low-end Android first.** Members are mostly on low-end Android phones with
  patchy data (`apps/web/DESIGN.md` in the main repo). On Android, Compose is the
  platform's own UI toolkit, compiled ahead of time, with no JavaScript runtime
  or bridge between the UI and the code behind it.
- **The heavy parts are native SDKs.** Gists use LiveKit, which ships Android
  (Kotlin) and iOS (Swift) SDKs. ADR 0021 in the main repo says the apps play HLS
  with the platforms' own players (Media3 ExoPlayer, AVPlayer). Push needs FCM
  and APNs. Kotlin calls all of these directly: Android libraries as they are,
  and Apple's frameworks through Kotlin/Native's Objective-C interop.
- **One UI for a small team.** HeyNaija has its own look (white cards on a sage
  ground, one green), not either platform's stock look, so drawing the same UI
  on both costs little. Writing every screen twice would cost a lot.
- **Compose on iOS is stable** (since Compose Multiplatform 1.8, May 2025),
  with VoiceOver support and native text input. A screen that needs to be
  UIKit or SwiftUI can still be written that way and embedded.

## What else was considered

| Option | For | Against |
|---|---|---|
| **React Native (Expo)** | TypeScript, like the web and API; API types could be shared; Expo handles builds and push; LiveKit has an RN SDK | A JavaScript runtime on low-end phones; native media and audio go through bridges; little of the web app's code would carry over (it is Next.js server components, not React Native) |
| **Flutter** | One drawn UI, mature | Dart, a third language, shared with nothing; the native SDKs still need platform channels |
| **Two native apps** (Kotlin and Swift) | Each platform at its best | Every screen and every bug fix twice |
| **KMP, SwiftUI on iOS** | Shared logic, each platform's own UI | Two UIs again. Kept as the fallback if Compose on iOS falls short: state lives in ViewModels, apart from the screens, so iOS screens could move to SwiftUI one at a time |

## Consequences

- Kotlin joins TypeScript as a language the project is written in.
- iOS builds and App Store signing still need a Mac with Xcode.
- The API's JSON is described twice: in TypeScript (`apps/web/lib/api.ts`) and
  in Kotlin (`shared/.../api/Models.kt`). Decoding ignores fields the app does
  not know, so the API can add fields freely. Removing or renaming one breaks
  older app versions still installed on phones, which the web never had to
  consider. If that starts to hurt, an OpenAPI schema can generate both.
- On iOS the UI is drawn by Compose (Skia), not UIKit. Scrolling, text
  selection and keyboard handling are close to native but not identical. Test
  them on a real iPhone, not only in the simulator.
