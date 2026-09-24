# Vcman — AGENTS.md

Reference for AI agents and developers working in this repo. Read `## Boundaries` before making changes.

## Project overview

- Kotlin Multiplatform project, root project name `Vcman`, namespace `com.tekome.vcman`.
- Targets: Android, iOS, Web.
- **Status: wizard boilerplate.** No real business logic yet — only sample code (`Greeting.kt`, `Platform.kt`, `App.kt`). Do not assume any feature exists beyond this sample code.
- Web UI is a plain **React** app (TypeScript + Vite), not Compose for Web/Wasm. It consumes the Kotlin/JS output of `sharedLogic`.

## Tech stack (current)

| Component | Version |
|---|---|
| Kotlin | 2.4.20 |
| AGP (Android Gradle Plugin) | 9.1.1 |
| Compose Multiplatform | 1.12.0 |
| Material3 (Compose) | 1.12.0-alpha03 |
| AndroidX Lifecycle (KMP artifacts) | 2.11.0 |
| kotlin-wrappers (JS/React interop) | 2026.9.1 |
| AndroidX Activity | 1.13.0 |
| Android compileSdk / targetSdk / minSdk | 37 / 37 / 27 |
| Gradle wrapper | 9.5.1 |
| JVM target (all Kotlin/Android modules) | 11 |
| React (webApp) | ^18.2.0 |
| Vite (webApp) | ^7.1.6 |
| TypeScript (webApp) | ^5.0.2 |
| Koog (`ai.koog:koog-agents` + `prompt-executor-{anthropic,openai}-client`) | 1.2.0 |
| Ktor client | 3.3.3 |
| kotlinx.serialization | 1.10.0 |
| kotlinx.coroutines (`kotlinx-coroutines-test`, `commonTest` only) | 1.10.2 |

`gradle/libs.versions.toml` is the single source of truth for JVM/Kotlin dependency versions; if this table disagrees with it, **the catalog wins**. `webApp/package.json` is the source of truth for web dependency versions. `^x.y.z` values are npm semver ranges copied from `webApp/package.json` (minimum version, not an exact pin) — all other rows are exact pinned versions from `gradle/libs.versions.toml`.

Koog's `android`-targeted artifacts are compiled against JVM 17 bytecode; calling one of Koog's own `inline` reified functions (e.g. `ai.koog.serialization.typeToken<T>()`) from `sharedLogic` would embed that JVM 17 bytecode into a JVM 11 compilation unit and fail to compile. Prefer the non-inline overload (e.g. `typeToken(kotlin.reflect.typeOf<T>())`) when Koog offers one; see `KoogLlmChats.kt` for a worked example.

Not present yet: DI, persistence, navigation, and logging libraries. Networking (Ktor + kotlinx.serialization) and an LLM agent framework (Koog) were added in `sharedLogic` for issue #7 (`data.ScoreAnalysisService`) — see `## Recommended additions` below for what is still missing. No Gradle lint plugin (only an IDE-level ktlint setting in `.idea/ktlint-plugin.xml`). No CI configuration (no `.github` directory).

## Project structure

| Path | Type | Role | Depends on |
|---|---|---|---|
| `sharedLogic` | Gradle module `:sharedLogic` | Pure-Kotlin shared business logic. Targets: `android`, `iosArm64`, `iosSimulatorArm64`, `js` (browser library, generates TypeScript definitions). | — |
| `sharedUI` | Gradle module `:sharedUI` | Compose Multiplatform UI shared by Android and iOS. Targets: `android`, `iosArm64`, `iosSimulatorArm64` (static framework `SharedUI`). **No `js` target.** | `sharedLogic` |
| `androidApp` | Gradle module `:androidApp` | Android app entry point (`com.android.application`). | `sharedUI` |
| `iosApp` | Xcode project — **not a Gradle module** | SwiftUI entry point, consumes the `SharedUI` framework. | `sharedUI` (compiled framework) |
| `webApp` | npm workspace — **not a Gradle module** | React + TypeScript + Vite app, consumes the Kotlin/JS output of `sharedLogic`. | `sharedLogic` (npm package) |

`settings.gradle.kts` only `include`s `:androidApp`, `:sharedLogic`, `:sharedUI`. `iosApp` and `webApp` are top-level directories but have no Gradle build of their own — build/run them with Xcode and npm respectively.

Dependency flow: `androidApp -> sharedUI -> sharedLogic`; `iosApp -> SharedUI framework -> sharedLogic`; `webApp -> sharedLogic (Kotlin/JS)`.

`sharedLogic` source sets: `commonMain`, `androidMain`, `iosMain`, `jsMain`, `commonTest`, `androidHostTest`, `iosTest`, `webTest`. `expect`/`actual` files follow the pattern `Xxx.kt` (expect) + `Xxx.android.kt` / `Xxx.ios.kt` / `Xxx.js.kt` (actual), e.g. `Platform.kt`.

Note: `README.md` mentions a Desktop/`jvmMain` folder under `sharedUI`. No `jvm`/desktop target is actually configured in `sharedUI/build.gradle.kts` — treat that README text as leftover wizard boilerplate, not a real target.

## Architecture

- `sharedLogic` is pure Kotlin (no Compose) and must stay compilable for **all three targets** (`android`, `ios*`, `js`). Layer new code by **package** inside `commonMain` (`domain` / `data` / `presentation`) — do not create new Gradle modules for this; the codebase is too small to justify it today.
- `sharedUI` holds Compose Multiplatform UI shared by Android and iOS only (no `js` target — do not add Compose-only code expecting it to run on web).
- **Presentation layer: MVVM.** One `ViewModel` per screen, living in `sharedLogic`'s `presentation` package (business/UI-state logic stays platform-agnostic there), exposing state to Composables in `sharedUI` as `StateFlow` via `androidx.lifecycle-viewmodel-compose` / `androidx.lifecycle-runtime-compose` (already in `libs.versions.toml` and wired into `sharedUI/build.gradle.kts`). Composables should stay stateless — read state and forward events to the ViewModel, not own business logic.
  - Source: Google architecture guidance adapted for Kotlin Multiplatform (same `ViewModel`/unidirectional state pattern, DI swapped for Koin) — https://developer.android.com/topic/architecture
- Entry-point modules (`androidApp`, `iosApp`, `webApp`) must stay thin: wiring/bootstrap only, no business logic.
- **Rule — `expect`/`actual` vs interface + DI:**
  - Use `expect`/`actual` when the compiler must enforce that every platform provides an implementation, when the underlying platform types genuinely differ, or when wrapping a platform global/singleton API (e.g. the existing `Platform.kt`).
  - Use a `commonMain` interface with per-platform implementations wired through DI for everything else — anything that needs to be mockable, have multiple implementations, or be swapped in tests.
  - Source: https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html

## Setup

```bash
# Android / Kotlin: no local JDK/Gradle install required, use the wrapper
./gradlew --version

# Web
# Install Node.js (includes npm): https://nodejs.org/en/download

# iOS
# Requires macOS + Xcode. iosSimulatorArm64 requires an Apple Silicon Mac.
```

- Android SDK location goes in `local.properties` (`sdk.dir=...`) — do not commit this file.

## Build & run

```bash
# Android app
./gradlew :androidApp:assembleDebug

# Web app
npm run build:shared   # runs: ./gradlew :sharedLogic:jsBrowserDevelopmentLibraryDistribution
npm install
npm run start           # build:shared + vite dev server for webApp

# iOS app
# Open /iosApp in Xcode and run from there.
```

## Test

```bash
# Android tests
./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest

# Web tests
./gradlew :sharedLogic:jsTest

# iOS tests
./gradlew :sharedUI:iosSimulatorArm64Test :sharedLogic:iosSimulatorArm64Test
```

| Source set | Gradle task |
|---|---|
| `androidHostTest` | `testAndroidHostTest` |
| `webTest` | `jsTest` |
| `iosTest` | `iosSimulatorArm64Test` |

## Code style

- `kotlin.code.style=official` (4-space indent), set in `gradle.properties`.
- Package root: `com.tekome.vcman` (`.sharedLogic`, `.sharedUI` sub-namespaces per module).
- `expect`/`actual` file naming: `Xxx.kt` + `Xxx.android.kt` / `Xxx.ios.kt` / `Xxx.js.kt`, matching existing `Platform.kt`.
- Composables: PascalCase function names; `Modifier` is the first parameter with a default value.
- JVM target is 11 across all Kotlin/Android modules — do not use APIs newer than Java 11.
- `webApp`: standard React/TypeScript conventions — function components, `.tsx` files, CSS next to the component that uses it.
- Every JVM/Kotlin dependency must be declared via an alias in `gradle/libs.versions.toml` (`libs.*`) — no inline version strings in `build.gradle.kts`.

## Recommended additions (not installed)

**Everything in this section is NOT installed except the Networking row below (installed for issue #7). Do not assume any other library listed here is available in the code.**

| Area | Suggested | Why | Applies to | Status |
|---|---|---|---|---|
| Networking | Ktor client + kotlinx.serialization | Official multiplatform HTTP client, auto engine selection per target | `sharedLogic`, all targets | INSTALLED (issue #7: `data.BraveSearchTool`/`data.FirecrawlSearchTool`, Koog LLM clients) |
| DI | Koin, prefer Koin Annotations (KSP) | Compile-time safe bindings; catches missing bindings at build time, useful for AI-agent-driven edits | `sharedLogic` | NOT INSTALLED |
| Persistence | SQLDelight or Room (KMP) | SQL-first vs annotation-based; Room only gained JS/WasmJS support in Room 3.0 (03/2026) — previously SQLDelight was the only option for this repo's `js` target. Both support JS/WasmJS now — pick per team preference | `sharedLogic` | NOT INSTALLED |
| Preferences | multiplatform-settings | Simple key-value store, supports all targets including JS | `sharedLogic` | NOT INSTALLED |
| Navigation | AndroidX Navigation Compose Multiplatform (Decompose as advanced alternative) | Official, same API as Jetpack Compose Navigation | `sharedUI` only, not `webApp` | NOT INSTALLED |
| Logging | Kermit or Napier | Multiplatform logging; Kermit adds crash-reporting integrations | `sharedLogic` | NOT INSTALLED |
| Lint/format | ktlint + Compose Rules ruleset | Catches Compose-specific pitfalls; detekt/Spotless optional | `sharedUI` (Compose rules), all Kotlin code (ktlint) | NOT INSTALLED |
| Testing | Turbine (on top of existing kotlin-test) | Deterministic `Flow`/`StateFlow` testing, add when Flow-based logic exists | `sharedLogic` | NOT INSTALLED |
| CI/CD | GitHub Actions: Linux runner for Android/JVM/JS tests, separate macOS runner for iOS | Standard split-runner setup for KMP; Fastlane/code signing/SBOM only once real releases exist | repo-wide | NOT INSTALLED |

Versions are intentionally not pinned here. Use the latest stable release and verify compatibility with Kotlin 2.4.20 / Compose Multiplatform 1.12.0 / AGP 9.1.1 before adding anything to `gradle/libs.versions.toml`.

## Boundaries

**Always**
- Use the `./gradlew` wrapper, never a separately installed Gradle.
- Declare dependencies through `gradle/libs.versions.toml` and reference them via `libs.*` aliases.
- Keep `sharedLogic` compiling for all three targets (`android`, `ios*`, `js`).
- Run the tests for any target you touched before reporting work as done.
- Update the `## Tech stack (current)` table in this file if you bump a version in the catalog.

**Ask first**
- Adding any new library, plugin, or dependency — including anything listed in `## Recommended additions`.
- Adding a new Gradle module or target (e.g. `wasmJs`, `jvm`/desktop).
- Restructuring packages or moving files between modules.
- Adding a CI workflow (`.github/workflows/...`).

**Never**
- Change `kotlin`, `agp`, `composeMultiplatform`, `material3`, or SDK versions in `gradle/libs.versions.toml` on your own.
- Hard-code a dependency version directly in a `build.gradle.kts` instead of the version catalog.
- Edit or delete `README.md`.
- Commit `local.properties`, `.idea/`, `node_modules/`, or any `build/` directory.
- Add platform-specific APIs into `commonMain`.
- Assume a library from `## Recommended additions` is already installed.

---
Last verified against the repo on 2026-09-23 (Kotlin 2.4.20, AGP 9.1.1, Compose Multiplatform 1.12.0, Koog 1.2.0, Ktor 3.3.3).
