# Vcman — AGENTS.md

Guide for AI agents and developers working in this repo. Read **Boundaries** before changing anything.

**Precedence:** this file (plus `CLAUDE.md` / `docs/adr/` if added later) > the official Kotlin Multiplatform guidance linked in **References** > general community practice. When code and this file disagree, fix the file — and for versions, `gradle/libs.versions.toml` always wins.

## Contents

1. [Quick reference](#1-quick-reference)
2. [Boundaries](#2-boundaries)
3. [Project overview](#3-project-overview)
4. [Architecture](#4-architecture)
5. [Coding conventions](#5-coding-conventions)
6. [Testing](#6-testing)
7. [Feature: LLM settings and API key](#7-feature-llm-settings-and-api-key)
8. [Feature: localization (i18n)](#8-feature-localization-i18n)
9. [Tech stack](#9-tech-stack)
10. [Roadmap: limitations and modernization backlog](#10-roadmap-limitations-and-modernization-backlog)
11. [References](#11-references)

## 1. Quick reference

| Task | Command |
|---|---|
| Check toolchain | `./gradlew --version` (no local Gradle/JDK install needed) |
| Build Android | `./gradlew :androidApp:assembleDebug` |
| Run web | `npm install && npm run start` (builds `sharedLogic` JS, then Vite dev server) |
| Run iOS | Open `iosApp/` in Xcode and run (macOS + Xcode; simulator needs Apple Silicon) |
| Test Android/JVM | `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest` |
| Test web | `./gradlew :sharedLogic:jsTest` |
| Test iOS | `./gradlew :sharedUI:iosSimulatorArm64Test :sharedLogic:iosSimulatorArm64Test` |
| Test everything (macOS) | `./gradlew allTests` |
| Resolved version of a dependency | `./gradlew :sharedLogic:dependencyInsight --configuration androidRuntimeClasspath --dependency <name>` |

- Node.js (includes npm) is required for the web app: https://nodejs.org/en/download
- The Android SDK path goes in `local.properties` (`sdk.dir=...`), which is never committed.
- CI (`.github/workflows/ci.yml`) runs the three test rows above as independent jobs `android-jvm`, `js` and `ios` on every PR to `main` and every push to `main`. `android-jvm` and `ios` pass `--continue`. A job's `name:` is its PR status-check name, so check branch protection before renaming one.

## 2. Boundaries

**Always**
- Use the `./gradlew` wrapper.
- Declare every JVM/Kotlin dependency in `gradle/libs.versions.toml` and reference it as `libs.*`.
- Keep `sharedLogic` compiling for all three targets (`android`, `ios*`, `js`).
- Run the tests for every target you touched before reporting work as done.
- Update **Tech stack** when you change a version in the catalog, and **Roadmap** when you install or drop something listed there.

**Ask first**
- Adding any library, plugin or dependency, including anything in **Roadmap**.
- Adding a Gradle module, a target (`wasmJs`, `jvm`/desktop, …) or a CI workflow.
- Restructuring packages or moving files between modules.

**Never**
- Change `kotlin`, `agp`, `composeMultiplatform`, `material3` or SDK versions on your own.
- Hard-code a version in a `build.gradle.kts`.
- Edit or delete `README.md` (its Desktop/`jvmMain` mention is wizard leftover — there is no `jvm` target).
- Commit `local.properties`, `.idea/`, `node_modules/` or any `build/` directory.
- Put platform APIs (`android.*`, `platform.*`, `java.*` outside the stdlib) in `commonMain`.
- Hard-code an API key anywhere (source, resources, `BuildConfig`, `Info.plist`).
- Assume a **Roadmap** item exists in the code.

## 3. Project overview

Kotlin Multiplatform app (root project `Vcman`, package root `com.tekome.vcman`) for Android, iOS and Web.

**Status: POC.** One feature exists, on Android and iOS only: LLM score analysis — enter a rubric and a subject in `SetupScreen`, get a `ScoreReportScreen` — plus a Settings screen for the LLM provider, model and API key (issues #4, #6, #7, #11, #40). `webApp` is still the wizard `Greeting` sample. Nothing else exists; see **Roadmap**.

| Path | Kind | Role | Targets | Depends on |
|---|---|---|---|---|
| `sharedLogic` | Gradle module | Pure-Kotlin business logic, ViewModels included | `android`, `iosArm64`, `iosSimulatorArm64`, `js` (browser library + TypeScript definitions) | — |
| `sharedUI` | Gradle module | Compose Multiplatform UI for Android and iOS | `android`, `iosArm64`, `iosSimulatorArm64` (static framework `SharedUI`); **no `js`** | `sharedLogic` (`api`) |
| `androidApp` | Gradle module | Android entry point (`MainActivity`) | — | `sharedUI` |
| `iosApp` | Xcode project, not Gradle | SwiftUI entry point; hosts `MainViewController()` from `sharedUI` `iosMain` | — | `SharedUI` framework |
| `webApp` | npm workspace, not Gradle | React + TypeScript + Vite (not Compose for Web) | — | `sharedLogic` Kotlin/JS output |

`settings.gradle.kts` includes only `:androidApp`, `:sharedLogic` and `:sharedUI`. Build `iosApp` with Xcode and `webApp` with npm.

**Source sets.** `sharedLogic`: `commonMain`, `androidMain`, `iosMain`, `jsMain`, `commonTest`, `androidHostTest`, `iosTest`, `webTest`. `sharedUI`: `commonMain`, `iosMain`, `commonTest`, `androidHostTest`, `iosTest`. iOS code goes in the intermediate `iosMain`/`iosTest` created by the default hierarchy template — never in `iosArm64Main`/`iosSimulatorArm64Main`.

## 4. Architecture

**Layers.** Dependencies point one way: `androidApp`/`iosApp` → `sharedUI` → `sharedLogic`; `webApp` → `sharedLogic`. Inside `sharedLogic/commonMain`, layer by **package**, not by Gradle module (the codebase is too small for more modules):

| Package | Holds | Examples |
|---|---|---|
| `domain` | Pure models, no I/O | `ScoreModels.kt`, `LanguageTag` |
| `data` | I/O, LLM/search clients, storage, prompt building | `ScoreAnalysisService`, `SettingsRepository`, `KoogLlmChats.kt`, `FirecrawlSearchTool` |
| `presentation` | One `ViewModel` per screen + typed UI state | `ScoreAnalysisViewModel`, `SettingsViewModel`, `AnalysisUiState` |

`sharedUI` (`com.tekome.vcman.ui`) holds stateless Composables only.

**MVVM / unidirectional data flow.** ViewModels live in `sharedLogic` `presentation` (AndroidX Lifecycle KMP `ViewModel`) and expose `StateFlow`; Composables collect it and forward events. Composables own no business logic. This follows Google's architecture guidance, with Hilt replaced by manual wiring until a KMP DI library is added.

**Navigation.** Navigation Compose with type-safe routes `HomeRoute` and `SettingsRoute` in `App.kt`. `SetupInput` is hoisted above the `NavHost` so it survives a trip to Settings. `ScoreAnalysisViewModel` is created in `App`, outside the `NavHost`, so an in-flight analysis is kept. `SettingsViewModel` is scoped to its back-stack entry, so unsaved edits are dropped on Back.

**Window insets.** Each destination owns its insets: Home applies `safeContentPadding()` in `AppContent`, Settings lets its `Scaffold`/`TopAppBar` handle them. Do not put `safeContentPadding()` back on the root `App()`, or app bars cannot draw edge-to-edge.

**Wiring (no DI yet).** Dependencies are created at the entry point and passed down — e.g. `createSettingsRepository(context)` in `MainActivity`, `createSettingsRepository()` in `MainViewController`. Entry points stay thin: wiring and bootstrap only.

**`expect`/`actual` vs interface.**
- `expect`/`actual` for small, stateless platform facts or platform singletons, where the compiler must force every target to implement it (e.g. `Platform.kt`). Factories whose signatures differ per platform are plain per-platform functions instead (e.g. `createSettingsRepository(context)` in `androidMain`, `createSettingsRepository()` in `iosMain`).
- A `commonMain` interface with per-platform implementations for anything with state, logic or a need to be faked in tests (e.g. `SettingsRepository`, `WebSearchTool`, `LlmChat`; `SecretKeyProvider` is the same idea inside `androidMain`). Do not mix both mechanisms for the same concern.

**Coroutines.** Inject `CoroutineDispatcher` through the constructor with a default (see `StoredSettingsRepository`); never hard-code `Dispatchers.*` inside a function body. Rethrow `CancellationException` — never swallow it in a `catch (e: Exception)`. Work runs in `viewModelScope`; no `GlobalScope`.

## 5. Coding conventions

- `kotlin.code.style=official` (4-space indent), set in `gradle.properties`.
- Kotlin packages: `com.tekome.vcman` + `.domain` / `.data` / `.presentation` / `.ui`. The Android namespaces `com.tekome.vcman.sharedLogic` / `.sharedUI` are only for generated `R`/resources — not Kotlin packages.
- `expect`/`actual` file names: `Xxx.kt` + `Xxx.android.kt` / `Xxx.ios.kt` / `Xxx.js.kt`.
- Composables: PascalCase; `modifier: Modifier = Modifier` is the first optional parameter.
- JVM target is 11 in every Kotlin/Android module — no Java APIs newer than 11.
- **Koog + JVM 11:** Koog's Android artifacts are JVM 17 bytecode. Calling a Koog `inline reified` function (e.g. `typeToken<T>()`) inlines that bytecode into our JVM 11 code and fails to compile; use the non-inline overload (`typeToken(typeOf<T>())`), as in `KoogLlmChats.kt`.
- Secrets use the `ApiKey` type everywhere so `toString()` never prints them; never put them in `rememberSaveable` or logs.
- `webApp`: function components, `.tsx` files, CSS next to its component.

## 6. Testing

| Source set | Runs on | Gradle task | Use for |
|---|---|---|---|
| `commonTest` | every target | (part of each task below) | Shared logic and locale-independent UI tests (`kotlin.test`, `runTest`, `runComposeUiTest`) |
| `androidHostTest` | host JVM (Robolectric for UI) | `testAndroidHostTest` | Android-only code, exact-text locale tests, crypto with a software key |
| `iosTest` | iOS simulator | `iosSimulatorArm64Test` | iOS-only code |
| `webTest` | browser JS | `jsTest` | JS-only code |

- **Fakes, not mocks.** Hand-written fakes of the `commonMain` interfaces (plus `MapSettings` and Ktor `MockEngine`); JVM-only mocking libraries do not run on Native/JS and must not be added.
- Coroutine tests use `runTest` and inject a `TestDispatcher`.
- Things that cannot run in tests need a manual check on a real device: Android Keystore and iOS Keychain persistence (kill the app, reopen, settings are still there).

## 7. Feature: LLM settings and API key

- **Where:** `sharedLogic` `data` (`LlmProviderType`, `LlmSettings`, `SettingsRepository`, `ConnectionTester`, `KoogLlmChats.kt`) and `presentation` (`SettingsViewModel`); UI in `sharedUI/ui/SettingsScreen.kt` + `SettingsText.kt`. `LlmProviderType` lives in `data` because it carries the Koog client builder.
- **Add a provider = one enum entry** in `LlmProviderType` (`id`, `brand`, `defaultBaseUrl`, `defaultModel`, `createChat`) plus one `xxxCompatibleChat(settings, httpClientFactory)` builder in `KoogLlmChats.kt`. Everything else (Settings list, defaults, validation, storage, connection test, analysis) iterates `LlmProviderType.entries`. Never branch on a specific provider elsewhere. A persisted `id` never changes.
- **Base URL / path:** stored as typed (trimmed, trailing `/` removed). Koog appends the request path after the URL's own path: `openAiChatPath` sends `v1/chat/completions` when the URL has no path (`http://localhost:11434`) and `chat/completions` when it has one (`https://api.openai.com/v1`); `anthropicMessagesPath` sends `messages` when the path ends in `/v1`, else `v1/messages`. The model is a hand-built `LLModel` with minimal capabilities (OpenAI also needs `OpenAIEndpoint.Completions`; Anthropic needs the model in `modelVersionsMap`).
- **Key storage:** never stored with the other settings.
  - Android: AES-256-GCM with a key in the Android Keystore (`AesGcmSecretCipher`, `KeystoreSecretStore`), ciphertext in a separate preferences file. If the Keystore key is lost or invalidated (lock-screen change, restore to another device) the ciphertext is discarded and the user re-enters the key; provider/base URL/model are kept.
  - iOS: Keychain via `KeychainSettings`. Items survive an app uninstall; the next save overwrites them.
  - Web: no Settings screen, no key stored.
- **Plain `http://`** to a non-local host (anything but `localhost`, `127.0.0.1`, `[::1]`, `10.0.2.2`) shows a warning but is allowed.
- **Security model — bring your own key, personal/dev use only.** The app calls the provider directly from the device with the user's own key; there is no backend. That is acceptable for a personal/POC build. It is **not** acceptable for a public App Store / Play Store release that ships or provisions a shared key: anything in the binary or on the device can be extracted, so a shared key must sit behind a backend proxy that authenticates users and enforces quotas.

## 8. Feature: localization (i18n)

Languages: `en` (default and fallback) and `vi`. The UI follows the device language only (no in-app switcher); any other language falls back to `en`. Uses Compose Resources, no extra library.

- **Strings:** `sharedUI/src/commonMain/composeResources/values/strings.xml` (en) and `values-vi/strings.xml`, one file per locale. Read with `stringResource(Res.string.<key>)` (`vcman.sharedui.generated.resources.Res`).
- **No hard-coded user-facing text** in Composables; only `@Preview` and test data may use literals. Dynamic values use positional placeholders (`%1$s`, `%1$d`), never concatenation.
- **ViewModels never return display strings.** They emit typed data (`AnalysisFailure`, `RequiredFieldId`, `SettingsFieldError`, `ConnectionTestResult`); `sharedUI/ui/AnalysisFailureText.kt` and `SettingsText.kt` map it to resources with an exhaustive `when` (no `else`), so a new case will not build without a message. Provider labels use one format string (`settings_provider_option`) plus `brand`.
- **LLM answer language** = the `content_language_tag` resource, exposed by `rememberContentLanguage()` and passed as `LanguageTag` through `ScoreAnalysisViewModel.analyze` → `ScoreAnalysisService.analyze` → `PromptBuilder.buildUserPrompt`. Reports already received are not re-translated when the locale changes.
- **Score decimal separator** = the `decimal_separator` resource (`formatScore(value, separator)`); minimal formatting, not CLDR.
- **Vietnamese glossary:** rubric = "rubric", score = "điểm", weight = "trọng số". Always write Vietnamese with diacritics.
- **Add a key:** add it to `values` and every `values-xx`. **Add a language:** create `values-xx/strings.xml` with every key (including `decimal_separator` and `content_language_tag` = `xx`), then add `xx` to `CFBundleLocalizations` in `iosApp/iosApp/Info.plist` and `knownRegions` in `iosApp/iosApp.xcodeproj/project.pbxproj`. No Kotlin change needed.
- **Guard:** `StringResourcesCompletenessTest` (`sharedUI` `androidHostTest`) fails on missing/unknown keys, differing placeholders, blank strings or a wrong `content_language_tag`. Compose Resources silently falls back to `values`, so this test is the only check.
- **Locale tests:** exact-text assertions only in `androidHostTest` (`LocalizedUiTest`, Robolectric `@Config(qualifiers = ...)`); `commonTest` asserts locale-independent facts only.
- **Android `app_name`** is a brand name (`translatable="false"` in `androidApp/src/main/res/values/strings.xml`).

## 9. Tech stack

Exact versions come from `gradle/libs.versions.toml` (Kotlin/JVM) and `webApp/package.json` (web, `^` = npm minimum). If this table disagrees, those files win.

**Toolchain and platform**

| Component | Version |
|---|---|
| Kotlin | 2.4.20 |
| Android Gradle Plugin (KMP library plugin `com.android.kotlin.multiplatform.library`) | 9.1.1 |
| Gradle wrapper (configuration cache + build cache on in `gradle.properties`) | 9.5.1 |
| Android compileSdk / targetSdk / minSdk | 37 / 37 / 27 |
| JVM target (all modules) | 11 |

**Shared libraries**

| Library | Where | Version |
|---|---|---|
| Compose Multiplatform | `sharedUI` | 1.12.1 |
| Material3 (Compose) | `sharedUI` | 1.12.0-alpha03 |
| Navigation Compose Multiplatform | `sharedUI` | 2.9.2 |
| AndroidX Lifecycle (KMP: `viewmodel`, `-viewmodel-compose`, `-runtime-compose`) | `sharedLogic`, `sharedUI` | 2.11.0 |
| AndroidX Activity (Compose) | `androidApp` | 1.13.0 |
| Koog (`koog-agents`, `prompt-executor-{anthropic,openai}-client`) | `sharedLogic` | 1.3.0 |
| Ktor client (core, content-negotiation; engines okhttp / darwin / js) | `sharedLogic` | 3.3.3 |
| kotlinx.serialization (json) | `sharedLogic`, `sharedUI` | 1.11.0 |
| kotlinx.coroutines core | `sharedLogic` | **transitive, not declared** — 1.10.2 on `android`/`ios*`, 1.11.0 on `js` |
| multiplatform-settings (+ `-coroutines`; `-datastore` in `androidMain`) | `sharedLogic` | 1.3.0 |
| AndroidX DataStore Preferences | `sharedLogic` `androidMain` | 1.2.1 |
| kotlin-wrappers (`kotlin-browser`) | `sharedLogic` `jsMain` | 2026.9.2 |

**Testing**

| Library | Where | Version |
|---|---|---|
| kotlin-test | all `*Test` | = Kotlin |
| kotlinx-coroutines-test | `sharedLogic` `commonTest` | 1.11.0 |
| Ktor client mock | `sharedLogic` `commonTest` | 3.3.3 |
| multiplatform-settings-test (`MapSettings`) | `sharedLogic` `commonTest` | 1.3.0 |
| Compose UI test (`runComposeUiTest`) | `sharedUI` `commonTest` | 1.12.1 |
| Robolectric | `sharedUI` `androidHostTest` | 4.15.1 |

**Web (`webApp/package.json`)**: React ^18.2.0, Vite ^7.1.6, TypeScript ^5.0.2.

## 10. Roadmap: limitations and modernization backlog

Nothing in this section exists in the code. Each item needs an issue, and anything that adds a dependency or plugin needs approval first (**Boundaries**). Pick the latest stable version and check it against Kotlin / Compose Multiplatform / AGP above before adding it to the catalog.

**Product gaps (POC scope)**

| Gap | Today | Next step |
|---|---|---|
| Web feature | `webApp` shows `Greeting`; no `js` `createSettingsRepository`, no web UI | Decide React UI over `sharedLogic` JS vs. a Compose `wasmJs` target |
| Saved rubrics / analysis history | Rubric, subject and report are in memory only (`rememberSaveable` survives process death, not an app restart); only LLM settings persist | Add a KMP database (see below) |
| Web search during analysis | `FirecrawlSearchTool` is implemented and tested, but `ScoreAnalysisViewModel` passes `searchTool = null` and no UI takes a Firecrawl key | Add the key to Settings and pass the config |
| Public release | Bring-your-own-key only (section 7) | Backend proxy holding the key |

**Engineering backlog (ordered by value / effort)**

| # | Item | Why (industry standard) | Applies to |
|---|---|---|---|
| 1 | Declare `kotlinx-coroutines-core` in the catalog | Main code imports it directly; today its version drifts per target via Koog/Ktor/Lifecycle | `sharedLogic` |
| 2 | ktlint + Compose Rules (Gradle plugin, run in CI); detekt optional | Enforced formatting and Compose pitfalls; today only an IDE-level ktlint setting in `.idea/` | all Kotlin |
| 3 | Convention plugins in `build-logic` (included build) | Removes duplicated target/compiler/Android config across `sharedLogic` and `sharedUI` | build |
| 4 | DI: Koin (Koin Annotations / KSP for compile-time checks) | Replaces manual wiring as the graph grows; one injection mechanism | `sharedLogic`, entry points |
| 5 | Logging: Kermit | Multiplatform logging with crash-reporting hooks | `sharedLogic` |
| 6 | Turbine | Deterministic `Flow`/`StateFlow` assertions in `commonTest` | tests |
| 7 | Persistence: Room KMP or SQLDelight (both support JS/WasmJS now) | Needed for rubric/analysis history | `sharedLogic` |
| 8 | Raise JVM target 11 → 17 | Matches Koog's bytecode and current AGP defaults; removes the inline-function workaround | all modules (ask first) |
| 9 | Swift-friendly API: SKIE or KMP-NativeCoroutines | Only if Swift code starts consuming `Flow`/`suspend` directly; today Swift only hosts Compose | `sharedUI`/iOS |

Already done and no longer on the backlog: Ktor + kotlinx.serialization (issue #7), multiplatform-settings (issue #40), Navigation Compose (issue #40), GitHub Actions CI with separate Linux/macOS runners.

## 11. References

- Kotlin Multiplatform project structure / hierarchy: https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
- `expect`/`actual` and platform APIs: https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html, https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html
- Gradle best practices: https://kotlinlang.org/docs/gradle-best-practices.html
- Coroutines in KMP: https://kotlinlang.org/docs/multiplatform-mobile-concurrency-and-coroutines.html
- Testing KMP: https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html
- Android app architecture: https://developer.android.com/topic/architecture
- Library search: https://klibs.io

---
Last verified against the repo on 2026-10-08 (Kotlin 2.4.20, AGP 9.1.1, Compose Multiplatform 1.12.1, Koog 1.3.0, Ktor 3.3.3, kotlinx.serialization 1.11.0).
