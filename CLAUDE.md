# Playlog

Personal, non-commercial Android game tracker. It aggregates playtime, titles, covers and screenshots
from Steam (official Web API), PlayStation and Nintendo (reverse-engineered APIs of their own apps)
for the user's own accounts. Not published to any store.

Stack: Kotlin 2.3, Jetpack Compose + Material 3, Hilt, Room, DataStore, WorkManager, Ktor, Coil,
kotlinx.serialization. minSdk 26, compile/target 36, JVM 17. Versions live in `gradle/libs.versions.toml`.

## Layout

- `:core:model`, `:core:common`, `:core:network`, `:core:data`, `:core:testing`, `:platform:*` are
  **pure Kotlin/JVM**: no Android imports there, ever. They build without the Android SDK.
- `:app`, `:core:database`, `:core:designsystem`, `:core:ui`, `:feature:*` are Android modules.
- `:platform:api` is the contract every platform implements: `GamePlatformSource` (declares
  `capabilities`, fetches library / screenshots / daily playtime) and `AccountLinker` (link flow of
  `LinkStep`s and `LinkFieldType` inputs; WebView capture via `captureRedirectPrefix`). The rest of the
  app talks only to these interfaces; sources are registered in `app/.../di/AppModule.kt`.
- `:core:data` owns `SyncEngine`, repositories, store interfaces (`Stores.kt`) and their in-memory
  implementations; `:core:database` provides the Room/DataStore/Keystore implementations.
- Errors from platforms are `PlatformException` subtypes (`NotLinked`, `AuthExpired`, `InvalidInput`,
  `PrivateProfile`, `RateLimited`, `Network`, `Protocol`). Auth expiry must surface as a re-link
  prompt, not a crash or endless spinner.
- Dependencies between modules use type-safe accessors: `implementation(projects.core.data)`.

## Commands

```bash
./gradlew test                              # all unit tests (Android modules via Robolectric)
./gradlew test -Pplaylog.jvmOnly=true       # JVM modules only, no Android SDK needed
./gradlew :platform:steam:test              # one module
./gradlew liveTest                          # *LiveTest against real servers; skipped without env vars
./gradlew detekt                            # static analysis, baseline in config/detekt/baseline/
./gradlew koverHtmlReport                   # merged coverage
./gradlew :app:assembleDebug                # debug APK (app.playlog.debug)
```

In a cloud container without the Android SDK, use `-Pplaylog.jvmOnly=true`. There is no CI: run
`test` and `detekt` locally for the modules you touched before committing.

## Conventions

- Code, comments, identifiers and commit messages in English. User-facing strings go in
  `res/values/strings.xml` (Russian UI), never hard-coded in composables.
- Follow `.editorconfig` and Kotlin official style. Keep comments sparse: explain *why*, not *what*.
- Don't add detekt suppressions or grow the baseline to get green; fix the finding.
- UI follows the design spec (tokens in `core/designsystem`: `PlaylogTheme`, `PlatformPalette`,
  spacing). Dark theme first, lime accent, Manrope for headings, Inter for body text.
- Mockups: https://claude.ai/artifact/AZEG69DvDHTHHofjVL5wEL
- ViewModels expose `StateFlow<UiState>`; collect with `collectAsStateWithLifecycle`. Inject
  dispatchers from `:core:common`, don't hard-code `Dispatchers.IO`.
- Network clients are tested with Ktor `MockEngine` and JSON fixtures under
  `src/test/resources/fixtures/<platform>/`. Fixtures must be anonymised: no real IDs, tokens, names.
- Secrets (Steam API key, PSN refresh token, Nintendo session token) only go through
  `CredentialStore`. Never log them, never put them in fixtures or commits. Live tests read
  `STEAM_API_KEY`, `STEAM_ID`, `PSN_NPSSO`, `NSO_SESSION_TOKEN` via `requireEnv`.
- Reverse-engineered endpoints, client IDs and headers are documented in the project's
  `data-sources/SPEC.md`; keep code and spec in sync when an endpoint changes.

## Subagents

`.claude/agents/` defines roles: `android-developer`, `ui-ux-designer`,
`platform-reverse-engineer`, `test-engineer`. Use the one matching the task.
