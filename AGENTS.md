# Notes for coding agents

- Shared code goes in `shared/src/commonMain`. Use `androidMain` or `iosMain`
  only for a platform's own API, behind an interface the shared code defines.
- Colours, type and radii come from `theme/`, which follows the web app's
  tokens (`apps/web/app/globals.css` in HeyNaija/heynaija). Do not hard-code
  colours in screens; change the web's tokens first, then follow them here.
- `api/Models.kt` follows the API's JSON (`apps/web/lib/api.ts`). Add a field
  with a default, so an older server's answer still decodes.
- Wording follows the web app: plain, short sentences in British English.
- Check a change with `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`,
  and for iOS `./gradlew :shared:iosSimulatorArm64Test` or an Xcode build.
