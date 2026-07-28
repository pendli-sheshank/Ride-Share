---
name: release-pipeline
description: Build and publish SawaariShare to Google Play internal testing and Apple TestFlight from GitHub Actions. Use when working on release workflows, app signing, versionCode or build numbers, the Kotlin Multiplatform shared framework, the iOS Xcode project, or when a CI or release run fails.
triggers:
  - "release"
  - "publish"
  - "deploy"
  - "play store"
  - "internal testing"
  - "testflight"
  - "app store"
  - "ipa"
  - "aab"
  - "signing"
  - "keystore"
  - "fastlane"
  - "github actions"
  - "ci failed"
  - "workflow"
---

# SawaariShare Release Pipeline

How this app gets from a push on `main` to testers on both stores, what has to be true for
that to work, and every failure mode discovered so far.

> **Append to "Known issues & fixes" whenever a run goes red.** That section is the point of
> this skill: each entry saves the next person the debugging round trip. Record the symptom
> exactly as it appears in the log, because that is what future-you will search for.

---

## 1. Architecture

| Module | What it is | Builds to |
|---|---|---|
| `:app` | Android application. All UI (`SawaariApp.kt`, ~8,000 lines of Jetpack Compose), `MainViewModel`, `SawaariRepository` (Firebase). | APK / AAB |
| `:shared` | Kotlin Multiplatform library — `androidTarget` + `iosX64` / `iosArm64` / `iosSimulatorArm64`. | JVM klib + (planned) `Shared.framework` |
| `iosApp/` | Xcode project. **Not yet created.** | IPA |

**Current state:** Android ships. iOS does not exist yet — there is no Xcode project, no
Compose Multiplatform, and `:shared` holds only duplicated models plus dead use-case classes
that `:app` does not depend on. The iOS half of this document describes the intended design,
marked *(planned)*, and is not yet load-bearing.

---

## 2. Workflows

| File | Trigger | Runner | Does |
|---|---|---|---|
| `.github/workflows/ci.yml` | PRs, non-`main` pushes | `ubuntu-latest` | assembleDebug, artifact class check, unit tests, `:shared:compileCommonMainKotlinMetadata` |
| `.github/workflows/release-android.yml` | push to `main`, manual | `ubuntu-latest` | signed AAB → Play internal testing |
| `.github/workflows/release-ios.yml` *(planned)* | push to `main`, manual | `macos-15` | archive → IPA → TestFlight |

Android and iOS are **separate workflow files on purpose**: independent re-runs, independent
concurrency, and the iOS workflow can be disabled in the Actions UI without touching Android.
A red iOS build must never hold up a Play release.

`concurrency` uses `cancel-in-progress: false` on release workflows — never cancel a run
partway through a store upload.

### The artifact class check — do not remove it

`.github/scripts/verify-app-classes.sh` fails the build if the APK/AAB does not contain
`com/example/ui/SawaariAppKt` in any dex. This exists because of the single worst bug found in
this repo: with no Kotlin plugin applied, Gradle skipped every `.kt` file, reported
**BUILD SUCCESSFUL**, and produced an installable artifact containing resources, a manifest,
and none of the app. That ships green and crashes on launch. The check costs two seconds.

It scans *every* dex, not `classes.dex` — app code landed in `classes6.dex` (APK) and
`base/dex/classes2.dex` (AAB), and which dex holds a class is not stable across builds.

---

## 3. Required GitHub secrets

Set at **Settings → Secrets and variables → Actions**. Publishing steps are gated on these
being non-empty, so the pipeline builds and validates without them and only starts uploading
once they exist.

### Android

| Secret | How to produce it |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 my-upload-key.jks` — **must** be the original upload key (see §4) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore store password |
| `ANDROID_KEY_PASSWORD` | key password |
| `ANDROID_KEY_ALIAS` | key alias; defaults to `upload` if unset |
| `PLAY_SERVICE_ACCOUNT_JSON` | full service-account JSON, raw (not base64) |
| `GOOGLE_SERVICES_JSON` | contents of `google-services.json` (optional but recommended) |
| `GEMINI_API_KEY` | → `.env` → `BuildConfig.GEMINI_API_KEY` |
| `FIREBASE_API_KEY` | → `.env` |
| `FIREBASE_APP_ID` | → `.env` |
| `FIREBASE_PROJECT_ID` | → `.env` |
| `FIREBASE_STORAGE_BUCKET` | → `.env` |

⚠ **Use `base64 -w0`.** Line-wrapped base64 is the single most common first-run failure; it
decodes to a corrupt keystore and the error message does not mention wrapping.

⚠ **A wrong Firebase secret fails quietly.** `SawaariRepository` skips Firebase init when a
value contains `PLACEHOLDER`, so a typo'd secret yields a working-looking build with no
backend rather than a crash. If testers report "nothing loads", check these first.

### iOS *(planned)*

| Secret | How to produce it |
|---|---|
| `ASC_KEY_ID` | App Store Connect API key ID |
| `ASC_ISSUER_ID` | App Store Connect issuer ID |
| `ASC_PRIVATE_KEY_BASE64` | `base64 -w0 AuthKey_XXXX.p8` — downloadable exactly once |
| `APPLE_TEAM_ID` | 10-character team ID |
| `MATCH_PASSWORD` | passphrase encrypting the certificates repo |
| `MATCH_GIT_URL` | private certs repo, e.g. `https://github.com/<you>/ios-certificates.git` |
| `MATCH_GIT_BASIC_AUTHORIZATION` | `base64 -w0 <<< "<user>:<PAT>"` |

---

## 4. Manual setup runbooks

### Google Play — one-time

1. **Find the original upload keystore.** The app is already live on internal testing, so it
   was signed with a specific upload key. Play rejects any AAB signed with a different one
   (`403: Your Android App Bundle is signed with the wrong key`). If it is genuinely lost:
   Play Console → Setup → App integrity → App signing → **request an upload key reset**. Do
   not just generate a new keystore and hope.
2. Play Console → **Setup → API access** → link or create a Google Cloud project.
3. In that GCP project: enable the **Google Play Android Developer API**; IAM → Service
   Accounts → create one → Keys → Add key → **JSON** → download.
4. Play Console → **Users and permissions → Invite new user** → the service account's email.
   Grant, for this app: *Release to testing tracks* and *View app information*.
5. Paste the JSON into `PLAY_SERVICE_ACCOUNT_JSON`.
6. **Permission propagation takes up to 24 hours.** A 403 on the first attempt is expected;
   wait before debugging anything else.
7. Play requires **at least one manual upload per track** before the API will accept one. If
   the internal track has never received an AAB, upload one by hand first.

### Apple — one-time *(planned)*

1. Active **paid** Apple Developer Program membership. TestFlight is impossible without it.
2. App Store Connect → Users and Access → Integrations → **App Store Connect API** →
   generate a team key with the **Admin** role. App Manager is *not* enough — it cannot create
   the signing certificate that `fastlane match` needs on its first run. Download the `.p8`
   (one chance only) and record the Key ID and Issuer ID.
3. developer.apple.com → Certificates, Identifiers & Profiles → **Identifiers** → register the
   bundle ID. Android's `applicationId` is frozen by the existing Play listing, but iOS is
   greenfield — prefer a clean `com.sawaarishare.app` over the AI Studio-generated
   `com.aistudio.sawaarishare.krqmzb`.
4. App Store Connect → **Apps → +** → create the app record. Uploads to a nonexistent record
   fail.
5. TestFlight → **Internal Testing** → create a group and add testers. Internal testing needs
   no App Review.
6. Create a **private** repo for `fastlane match` to store encrypted certificates, plus a
   fine-grained PAT with `contents:write` on it.
7. Set `ITSAppUsesNonExemptEncryption = false` in `Info.plist`, or every single build stalls
   waiting for a manual export-compliance answer.

### Why `fastlane match` and not Xcode automatic signing

Automatic signing with `-allowProvisioningUpdates` needs no Mac, which is appealing here — but
each ephemeral runner mints a **new** Apple Distribution certificate and discards the private
key. Apple caps distribution certificates at 2 per account, so the third run fails and needs
manual cleanup in the portal. `match` generates the key and CSR locally via OpenSSL and
registers it through the ASC API — also no Mac required — then reuses one stable certificate
forever. Keep automatic signing as an emergency fallback only.

---

## 5. Versioning

| | Android | iOS *(planned)* |
|---|---|---|
| Build number | `versionCode = 1000 + github.run_number` | `CURRENT_PROJECT_VERSION = github.run_number` |
| Display | `versionName = 1.0.<run_number>` | `MARKETING_VERSION` |

Both read from environment variables in `app/build.gradle.kts` via
`providers.environmentVariable(...)` — not `System.getenv(...)`, so the reads are declared
build inputs and the configuration cache stays valid.

- The published `versionCode` was **2**; the `1000` offset guarantees the value only ever
  increases and leaves headroom.
- ⚠ **`github.run_number` resets to 1 if the workflow file is renamed.** Renaming
  `release-android.yml` silently reintroduces version collisions. Bump the offset if you ever
  rename it.
- Store build numbers can never be reused, even after deleting a release.

---

## 6. Local verification (Linux, no Mac)

```bash
./gradlew :app:assembleDebug                          # compiles Kotlin — see Known issue #1
.github/scripts/verify-app-classes.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
./gradlew :shared:compileCommonMainKotlinMetadata     # best proxy for "will iOS compile?"
```

**Both optional-input states must be tested, not just one:**
```bash
env KEYSTORE_PATH= ./gradlew :app:bundleRelease    # empty  -> unsigned AAB, must SUCCEED
./gradlew :app:bundleRelease                       # absent -> unsigned AAB, must SUCCEED
```

Release path with a throwaway key (never use this artifact for anything):
```bash
keytool -genkeypair -v -keystore /tmp/test.jks -alias upload -keyalg RSA -keysize 2048 \
  -validity 100 -storepass testpass -keypass testpass -dname "CN=Test,O=Test,C=US"
KEYSTORE_PATH=/tmp/test.jks STORE_PASSWORD=testpass KEY_PASSWORD=testpass \
KEY_ALIAS=upload VERSION_CODE=1001 VERSION_NAME=1.0.99 ./gradlew :app:bundleRelease
```

**Android SDK is not preinstalled in the Claude Code container.** Install it once:
```bash
curl -sSfL -o /tmp/cmdline.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
mkdir -p /opt/android-sdk/cmdline-tools && unzip -q /tmp/cmdline.zip -d /opt/android-sdk/cmdline-tools
mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
export ANDROID_HOME=/opt/android-sdk PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
yes | sdkmanager --licenses > /dev/null
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=/opt/android-sdk" > local.properties     # gitignored
```

**Apple targets cannot be compiled on Linux at all** — not a configuration problem, a
Kotlin/Native platform restriction. Anything Xcode-related must be validated by pushing and
watching the macOS runner. Budget for slow iteration and prefer the unsigned simulator build
in `ci.yml` while shaking out project-structure problems, because it costs nothing in Apple
account state.

---

## 7. Known issues & fixes

Append-only log. Format: symptom as logged → cause → fix.

### 2026-07-24 — `BUILD SUCCESSFUL` but the app crashes instantly on launch
**Cause:** `:app` never applied `org.jetbrains.kotlin.android`. AGP 9.x compiles Kotlin
natively; when the project was downgraded to AGP 8.8.0 the plugin was never added, so no
`compileDebugKotlin` task existed and Gradle silently skipped all 16,000 lines of Kotlin. The
build produced an artifact with resources and a manifest but zero app classes.
**Fix:** added `kotlin-android` to the version catalog, root build, and `:app`; added
`.github/scripts/verify-app-classes.sh` so this can never recur silently.
**Check for it:** `./gradlew :app:tasks --all | grep compileDebugKotlin` — if that prints
nothing, Kotlin is not being compiled.

### 2026-07-24 — `Dependency 'androidx.core:core-ktx:1.18.0' requires Android Gradle plugin 8.9.1 or higher`
**Cause:** AGP pinned to 8.8.0, allegedly "for Gradle 8.14.3 compatibility" — a misdiagnosis.
AGP 8.13 requires Gradle 8.13+, which 8.14.3 satisfies.
**Fix:** AGP → 8.13.2.

### 2026-07-24 — `Configuration ':app:debugRuntimeClasspath' contains AndroidX dependencies, but the android.useAndroidX property is not enabled`
**Cause:** `android.useAndroidX=true` was never set, despite an entirely AndroidX graph. Hidden
until Kotlin compilation was switched on. Presents confusingly as a *configuration cache
serialization* error, because that is where the failure surfaces.
**Fix:** added `android.useAndroidX=true` to `gradle.properties`.

### 2026-07-24 — 134 Kotlin compile errors on the first real compilation
**Cause:** the code had never been compiled by anything, so errors accumulated freely. Two were
brace-balance bugs in `SawaariApp.kt`: a duplicated `}` inside `TripDetailScreen` and a missing
`}` on a `Card { Column { Row {` block. Together they detached `ChatScreen`, `StudentAvatar`,
`GoogleMapsMatrixCard` and 15 other composables into a nested scope, making them unresolvable
from their own call sites and cascading into ~100 downstream errors.
**Fix:** repaired both braces (134 → 70 → 31 errors), then the genuine API mismatches.
**Technique worth reusing:** compare computed brace depth against indentation to locate the
break — the file is consistently indented 4 spaces per level, so the first line where
`indent/4 != depth` is where the imbalance was introduced. Far faster than reading 8,000 lines.

### 2026-07-24 — `Unresolved reference 'FirestoreSettings'`
**Cause:** the class is `FirebaseFirestoreSettings`, and `setPersistenceEnabled` is superseded.
**Fix:** `FirebaseFirestoreSettings.Builder().setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())`.

### 2026-07-24 — `.MainActivity` would crash after a namespace change
**Cause:** `namespace` was the placeholder `com.splitcruiser.app` while `applicationId` was real.
Changing `namespace` to `com.aistudio.sawaarishare` also changes what the manifest's
`android:name=".MainActivity"` shorthand resolves to — it would have pointed at a class that
does not exist, crashing on launch with `ClassNotFoundException`.
**Fix:** fully qualified it as `com.splitcruiser.app.MainActivity` in `AndroidManifest.xml`.
**Lesson:** the `.Foo` shorthand resolves against `namespace`, *not* `applicationId`. Any
namespace change must be checked against the manifest and against `R` / `BuildConfig` imports.

### 2026-07-24 — `AndroidManifest.xml` had no `INTERNET` permission
**Cause:** never added, despite Firebase, OkHttp and the Gemini API. The app could not have
worked on a real device.
**Fix:** added `INTERNET` and `ACCESS_NETWORK_STATE`.

### 2026-07-24 — artifact class check reported a false failure
**Cause:** `unzip -p … | grep -qa` under `set -o pipefail`. `grep -q` exits on first match,
closing the pipe; `unzip` dies of SIGPIPE (141); pipefail propagates that, so a **successful
match** looked like a failed pipeline.
**Fix:** extract each dex to a temp file and grep the file. Applies to any
`producer | grep -q` under pipefail.

### 2026-07-24 — 157 compile errors in the unit and instrumented test suites
**Cause:** the suites import `com.splitcruiser.app.data.models.*` (no such package) and
`com.splitcruiser.app.data.MainViewModel` (wrong package — it is `com.splitcruiser.app.ui.MainViewModel`), and
call ~50 repository methods that do not exist (`createTripOffer`, `postTripOffer`,
`getTripOffers`, …). They were written against an imagined API and never compiled, so their
green PR descriptions meant nothing.
**Status:** unresolved — see the repo's open follow-ups. They cannot be "fixed"; they need
rewriting against the real API. `ExampleRobolectricTest` was a genuine test and only needed
its `R` import updated for the new namespace.
**Lesson:** a test suite that has never been executed is not evidence of anything. The
`ci.yml` unit-test job exists so this cannot happen again.

### 2026-07-24 — `sdkmanager: command not found` (exit 127) on `ubuntu-latest`
**Cause:** the runner image ships an Android SDK and sets `ANDROID_HOME`, but `sdkmanager`
itself lives in `cmdline-tools` and is **not on `PATH`**.
**Fix:** use `android-actions/setup-android@v3` with
`packages: 'platforms;android-36 build-tools;36.0.0'` instead of calling `sdkmanager` directly.

### 2026-07-24 — a workflow run appears with **zero jobs**, titled by filename instead of workflow name
**Cause:** the workflow file failed validation, so GitHub could not read its `name:`. The
specific error: **the `secrets` context is not permitted in `if:` conditions.** Steps had
`if: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON != '' }}`.
**Fix:** `secrets` *is* allowed in `jobs.<id>.env`. Hoist the presence checks to job-level env
flags and gate steps on those:
```yaml
env:
  HAS_KEYSTORE: ${{ secrets.ANDROID_KEYSTORE_BASE64 != '' }}
# then, on a step:
if: env.HAS_KEYSTORE == 'true'
```
**Recognising it:** a run with 0 jobs and the file path as its title is *always* a workflow
validation error, never a build failure. There are no logs to read — lint the file instead.

### 2026-07-24 — `got unexpected character '+' while lexing expression`
**Cause:** `${{ 1000 + github.run_number }}`. **GitHub Actions expressions have no arithmetic
operators.** There is no `+`, `-`, `*` or `/`.
**Fix:** compute in the shell and export through `$GITHUB_ENV`:
```yaml
- run: echo "VERSION_CODE=$(( 1000 + GITHUB_RUN_NUMBER ))" >> "$GITHUB_ENV"
```

### 2026-07-24 — `java.lang.UnsupportedOperationException at DefaultSdkProvider.java:170` in Robolectric tests
**Symptom:** `ExampleRobolectricTest > classMethod FAILED` and `GreetingScreenshotTest >
classMethod FAILED`, both with a bare `UnsupportedOperationException` and no useful message.
Passed locally, failed on CI — the tell-tale sign of a toolchain difference, not a code bug.
**Cause:** Robolectric requires a minimum JDK *per Android API level*, and throws
`"Android SDK %d requires Java %d (have Java %d)"`. Its table:

| Android API | Minimum JDK |
|---|---|
| 33 and below | 8–9 |
| 34 (Android 14) | 17 |
| 35 (Android 15) | 17 |
| **36 (Android 16)** | **21** |

`compileSdk`/`targetSdk` are 36 and both tests use `@Config(sdk = [36])`, so JDK 21 is
required. CI was pinned to JDK 17; the container happened to have JDK 21, which is exactly why
it reproduced only on CI.
**Fix:** `java-version: '21'` in every workflow. `compileOptions` stays at Java 11 — that is the
bytecode target and is unrelated to the JDK running Gradle.
**Lesson:** when something passes locally and fails on CI, diff the *toolchain* before the code.
Keep the CI JDK and the local JDK equal.

### 2026-07-24 — `Cannot convert '' to File.` on the first real release run against `main`
**Symptom:** `Release Android` failed in ~1 minute at the *Build release bundle* step, before any
task ran. The `--stacktrace` output is all `BuildScriptProcessor` / `LifecycleProjectEvaluator` /
`prepareProjects` frames, which is the signature of a **configuration-time** failure while
evaluating `app/build.gradle.kts` — not a task failure.
**Cause:** the workflow computes the keystore path conditionally:
```yaml
KEYSTORE_PATH: ${{ env.HAS_KEYSTORE == 'true' && format('{0}/upload.jks', runner.temp) || '' }}
```
With no keystore secret this sets the variable to an **empty string**, not to nothing. Gradle's
`providers.environmentVariable(...).orNull` returns `""` for a set-but-empty variable — it only
returns `null` when the variable is genuinely absent. So the `!= null` guard passed and
`file("")` threw.
**Fix:** treat blank as absent in `app/build.gradle.kts`:
```kotlin
providers.environmentVariable("KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }
```
Also gate the publish step on `HAS_KEYSTORE` as well as `HAS_PLAY_SERVICE_ACCOUNT`, so an
unsigned bundle is never offered to Play.
**Lesson — this is the one that got through:** the local verification had only ever exercised
*keystore present* (real path) and *keystore variable absent* (plain `assembleDebug`). CI hit a
third state, *present-but-empty*, that no local run had. **When a build input is optional,
test the empty string, not just set-vs-unset** — a conditional YAML expression produces empty,
never absent.
```bash
# the check that would have caught it
env KEYSTORE_PATH= ./gradlew :app:bundleRelease   # must succeed, producing an unsigned AAB
```

> **Lint workflows before pushing.** All three failures above were caught in seconds by
> [`actionlint`](https://github.com/rhysd/actionlint), versus a ~2 minute CI round trip each:
> ```bash
> curl -sSfL -o - https://github.com/rhysd/actionlint/releases/download/v1.7.7/actionlint_1.7.7_linux_amd64.tar.gz \
>   | tar xz actionlint && ./actionlint -no-color -oneline
> ```
> It understands the context-availability rules and the expression grammar, so it catches
> exactly the class of error that produces an unreadable zero-job run.

### 2026-07-27 — the iOS "test suite" was 39 tests that CI never compiled or ran
**Cause:** three `commonTest` files were added to `:shared`, plus an `androidTest` file and a
Swift `XCTest` file, and the PR described them as a working suite. Nothing ran any of them.
`ci.yml` runs `:app:testDebugUnitTest` (a different module) and
`:shared:compileCommonMainKotlinMetadata` (compiles `commonMain`, **not** `commonTest`). Green
CI therefore proved only that the app still built. Running `:shared:testDebugUnitTest` by hand
found 2 of the 39 asserting things that were simply false: `testModelDefaults` expected
`User().verifiedTier == "guest"` when `Models.kt` defaults it to `"vouched"`, and
`testEmailValidation` asserted `assertFalse` on a condition that is true for its own input.
**Fix:** corrected both assertions; added `:shared:testDebugUnitTest` to the `shared-common`
job so the suite actually gates merges.
**Check for it:** a test file only counts if a CI job names its Gradle task. Grep the workflow
for the task before believing any claim about coverage.

### 2026-07-27 — the Xcode project linked an XCFramework that no Gradle task produced
**Cause:** `iosApp.xcodeproj` referenced `../shared/build/XCFrameworks/Shared.xcframework` and
the docs told people to run `:shared:assembleSharedXCFramework`, but `shared/build.gradle.kts`
never declared an `XCFramework()`, so that task did not exist. The workflows instead ran
`:shared:linkReleaseFrameworkIosFat`, which writes somewhere else entirely — so
`build/XCFrameworks/` was never created, and `build-ios.yml` was uploading an artifact path
that never had anything in it.
**Fix:** declared `val xcf = XCFramework("Shared")` and `xcf.add(this)` per target, which
registers `assembleShared{Debug,Release}XCFramework`; pointed the workflows, `setup.sh` and the
pbxproj at `build/XCFrameworks/release/Shared.xcframework`; added a step that fails loudly if
the framework is not at that path after the Gradle build.
**Check for it:** `./gradlew :shared:<task> --dry-run` fails fast for a task that does not
exist. Do that before writing a workflow around a task name.

### 2026-07-27 — `linkReleaseFrameworkIosFat` cannot produce an App Store-uploadable build
**Cause:** it lipos `iosArm64` (device) together with `iosX64` (simulator) into one binary.
App Store upload rejects any archive whose embedded framework carries simulator slices
(ITMS-90240). It is a convenience task for local simulator work, not a release artifact.
**Fix:** use an XCFramework, which keeps device and simulator slices in separate correctly
tagged directories.

### 2026-07-27 — `pod install` aborted every iOS run: "Unable to find a target named `iosAppTests`"
**Cause:** the `Podfile` declared a nested `target 'iosAppTests'`, but `iosApp.xcodeproj` has
exactly one target (the app). The release workflow masked it with `|| echo "continuing..."`,
which turned a hard failure into a later, far more confusing one.
**Fix:** removed the nested target. No pods are declared at all now, so the release workflow no
longer runs CocoaPods — with nothing to install it only forces `-workspace` over `-project`.
**Also:** `FRAMEWORK_SEARCH_PATHS` was `"$(inherited)"` in both configurations, so `import
Shared` could not have resolved even once the framework existed. Now set per configuration.

### 2026-07-27 — first real TestFlight run: "The project 'iosApp' is damaged and cannot be opened"
**Symptom:** `xcodebuild archive` failed after 4 seconds with
`The project contains no build configurations - it may have been damaged`, naming no file,
line or ID. Everything before it succeeded — XCFramework built and verified, certificate
imported ("1 valid identities found"), profile UUID resolved, build number set.
**Cause:** `generate-project.py` called `generate_id()` inline at each emission site, so an
object's *definition* and every *reference* to it drew different random UUIDs. 12 references
dangled: all five app files (each had three disagreeing ids — the `PBXBuildFile.fileRef`, the
`PBXFileReference` definition, and the `PBXGroup` child) plus both `buildConfigurationList`
pointers. Xcode fails the entire project on a single unresolved reference, and reports only
"damaged".
**Fix:** gave every object a stable id in the `ids` dict keyed by role, and made
`generate_id()` deterministic via `uuid5` off a fixed namespace. Added
`.github/scripts/verify-xcodeproj.py`, which resolves every reference and names the dangling
ones, plus a CI step asserting the committed pbxproj is byte-identical to a fresh generation.
**Check for it:** `python3 .github/scripts/verify-xcodeproj.py iosApp/iosApp.xcodeproj/project.pbxproj`.
Runs on Linux in milliseconds; the macOS archive that catches it otherwise costs ~3 minutes.
**Why it hid so long:** because ids were random, regenerating rewrote the whole file, so a
diff against a fresh generation was pure UUID churn and told you nothing. Determinism is what
makes the drift check possible.

### 2026-07-27 — no shared scheme existed, and `xcodebuild archive` has no `-target` form
**Cause:** the generator emitted `project.pbxproj` and `contents.xcworkspacedata` but no
`xcshareddata/xcschemes/iosApp.xcscheme`. Whether `xcodebuild` autocreates a scheme for a
project with no shared schemes is version- and setting-dependent, so `-scheme iosApp` is a
coin flip on a runner. Archiving cannot fall back to `-target`.
**Fix:** the generator now writes a shared scheme whose `BlueprintIdentifier` is the real
`PBXNativeTarget` id and whose `ArchiveAction` is `Release`.
**Note:** this was found by inspection before it cost a run, unlike the one above.

### 2026-07-27 — archive: "Cannot code sign because the target does not have an Info.plist file"
**Symptom:** with the pbxproj graph repaired, `xcodebuild archive` got as far as computing the
dependency graph and running `actool`/`ibtool`, then failed with
`error: Cannot code sign because the target does not have an Info.plist file and one is not
being generated automatically ... (in target 'iosApp')`.
**Cause:** `generate-project.py` listed `Info.plist` in the resources copy phase but never set
`INFOPLIST_FILE`. Those are not interchangeable: the copy phase just drops the file into the
bundle, whereas `INFOPLIST_FILE` is what tells Xcode which plist *is* the target's — and code
signing requires the latter. The plist itself was complete and correct all along.
**Fix:** set `INFOPLIST_FILE = iosApp/Info.plist` (relative to SRCROOT, which is `iosApp/`) in
both target configurations, and removed `Info.plist` from the resources copy phase. Keeping
both would fail the next build with "Multiple commands produce .../Info.plist", since
INFOPLIST_FILE already installs a processed copy at the bundle root. The PBXFileReference is
retained so the file still appears in the project navigator.
**Check for it:** `grep INFOPLIST_FILE iosApp/iosApp.xcodeproj/project.pbxproj` — two hits
expected, one per configuration. Note that `verify-xcodeproj.py` does *not* catch this: the
reference graph was perfectly intact, the setting was simply absent.

### 2026-07-27 — Swift finally compiled, and failed with `error: no such module 'Shared'`
**Cause:** the pbxproj declared the framework as `lastKnownFileType = wrapper.framework` while
its path pointed at `Shared.xcframework`. An `.xcframework` is a *container* of per-slice
`.framework` bundles (`Shared.xcframework/ios-arm64/Shared.framework`). Typed as a plain
framework wrapper, Xcode never runs the `ProcessXCFramework` step that resolves the right
slice into `BUILT_PRODUCTS_DIR`, so `-F .../XCFrameworks/release` found no `.framework` to
import. The give-away in the log is the *absence* of a `ProcessXCFramework` line — the
`-F` search path was present and correct, which makes the error thoroughly misleading.
**Fix:** `lastKnownFileType = wrapper.xcframework`. Added a check to
`verify-xcodeproj.py`: any `PBXFileReference` whose path ends in `.xcframework` must be
declared `wrapper.xcframework`.
**Note:** this is the third distinct iOS-project bug that the *previous* guard did not catch.
The reference-graph check, the build-settings check and this one are independent failure
modes. Do not assume a green `verify-xcodeproj.py` means the project is sound.

### 2026-07-27 — `ViewModel.swift` was on disk, committed, and never compiled
**Symptom:** `** ARCHIVE FAILED **` on `CompileSwift normal arm64`, after `import Shared`
finally resolved.
**Cause:** `generate-project.py` only ever added `iOSApp.swift` and `ContentView.swift` to the
Sources build phase. `ViewModel.swift` — which defines `AppViewModel`, referenced five times by
`ContentView.swift` — was never in the project at all. The give-away is in the build log
itself: `Compiling iOSApp.swift, ContentView.swift, GeneratedAssetSymbols.swift`, three files
where the directory holds four.
**Fix:** added `ViewModel.swift` to the generator (PBXBuildFile, PBXFileReference, group child,
Sources build phase). Extended `verify-xcodeproj.py` with an optional sources-directory
argument: every `.swift` on disk must appear in a Sources build phase. CI now passes
`iosApp/iosApp`.
**Check for it:** read the `Compiling ...` line in any archive log and count the files against
`ls iosApp/iosApp/*.swift`. A file that is not listed does not exist as far as the compiler is
concerned, and the error surfaces at the *use* site in another file.
**Pattern worth internalising:** four iOS-project bugs, four different guards — dangling
references, a missing build setting, a wrong file type, and an uncompiled source file. Each was
invisible to the guard written for the previous one. When a generated project misbehaves, do
not assume the existing checks narrow it down.

### 2026-07-28 — first real Swift type-check: `SawaariRepository` is not in `Shared`
**Symptom:** `cannot find type 'SawaariRepository' in scope`, plus `missing arguments for
parameters 'invitedBy', 'ratingAvg', ... in call` on a `User(...)` construction.
**Cause (1):** `iosApp/ViewModel.swift` was written against a `SawaariRepository` that exists
only in `:app`. It takes an `android.content.Context` and drives Firebase, so it is
Android-only and is not — and cannot be — part of the `Shared` framework. iOS gets the models
from `Shared` and nothing else. The property was also never used: every method was a TODO
operating on local state.
**Cause (2):** **Kotlin default arguments do not survive into generated Swift initializers.**
`User` has 19 properties, all with Kotlin defaults; the generated Swift memberwise init
requires all 19 explicitly. This applies to every model in `Models.kt` and will bite again
anywhere Swift constructs one.
**Fix:** dropped the repository (dead code referencing a nonexistent type) and supplied all 19
arguments. Also replaced four `defer { isLoading = false }` statements that Swift warns about
as no-ops when they are the last statement in scope.
**Consequence worth knowing:** iOS has no persistence layer at all. Giving it one means
writing a repository in `shared/commonMain` — Firebase and `Context` cannot come along. Until
then the iOS app is UI over local state, which is fine for TestFlight but is an automatic
App Store rejection under Guideline 2.1.

### 2026-07-28 — modern SwiftUI against a 2020 deployment floor
**Symptom:** two errors in `ContentView.swift` — `note: add @available attribute to enclosing
struct` on `LoginView`, and `error: the compiler is unable to type-check this expression in
reasonable time` on a 40-line `body`.
**Cause:** `IPHONEOS_DEPLOYMENT_TARGET` was `14.0` while the SwiftUI is written in iOS 15/16
idiom. The offenders are all *dot-shorthand* forms, which is why a grep for the obvious iOS 15
APIs (`AsyncImage`, `.task`, `NavigationStack`) found nothing:
- `.textFieldStyle(.roundedBorder)` and `.progressViewStyle(.circular)` — iOS 15 (iOS 14 needs
  `RoundedBorderTextFieldStyle()` / `CircularProgressViewStyle()`)
- `Section("title") { }` — iOS 15 (iOS 14 needs `Section(header: Text(...))`)
- `NavigationLink("title") { }` — iOS 16
**The second error was a cascade of the first.** When Swift cannot resolve an initializer it
explores overloads combinatorially, and inside a large ViewBuilder that exhausts the
type-checker's budget. "Unable to type-check in reasonable time" next to an availability error
usually means *one* bug, not two.
**Fix:** raised the deployment target to `16.0` in all four build configurations and the
Podfile, rather than rewriting modern SwiftUI into iOS 14 dialect. iOS 14 shipped in 2020;
iOS 16 is a normal floor now. Also split the offending `body` into computed sub-views as
insurance, since that is where the limit bites regardless.
**Check for it:** grep for dot-shorthand style modifiers, not just type names — the shorthand
is the newer spelling of an API whose long form is often much older.

### 2026-07-28 — `xcodebuild -exportArchive` exits 139 (SIGSEGV), no message at all
**Symptom:** the archive succeeded, then `Export signed IPA` died in one second with
`##[error]Process completed with exit code 139` and no diagnostic whatsoever. 139 = 128 + 11,
i.e. segmentation fault.
**Cause:** `exportOptions.plist` set `signingStyle = manual` but omitted the
`provisioningProfiles` dictionary. That dictionary is **required** for manual signing. Xcode 15
does not report the omission — it crashes while trying to resolve a profile it was never given.
**Fix:** added `provisioningProfiles` mapping the bundle id (read out of the archive's
`Info.plist`, not hardcoded) to the profile UUID, plus `signingCertificate` and `destination`,
and `-allowProvisioningUpdates` on the command.
**Also added a fallback, because a crash with no message must not cost another release cycle:**
an `.ipa` is just a zip containing the signed `.app` under `Payload/`, and the app inside the
archive is already distribution-signed by the archive step. If `exportArchive` produces no IPA
for any reason, the step now packages one from the archive and verifies with `codesign -dv`
that what it packaged is actually signed.
**Check for it:** exit 139 from any Xcode tool is a segfault, never a configuration error
message you have missed. Look for a required key that is absent rather than one that is wrong.

### 2026-07-28 — upload step died in 0 seconds: BSD `find` rejects `-maxdepth` after a primary
**Symptom:** `Export signed IPA` succeeded and produced the IPA, then `Upload to App Store
Connect` failed instantly with no output at all — well before `altool` could have run.
**Cause:** `find "$dir" -name '*.ipa' -maxdepth 1`. GNU find accepts that ordering; **BSD find
on macOS rejects `-maxdepth` after another primary** and exits non-zero. Under
`set -o pipefail` the pipeline failed, and `set -e` killed the step before printing anything.
**Fix:** replaced `find` with a glob array (`ipas=("$dir"/*.ipa); ipa="${ipas[0]}"`), which is
portable and shellcheck-clean. Also added an `ls -la` on the failure path so an empty directory
is visible next time.
**This is the second BSD-vs-GNU trap in this one workflow** — `base64 -o` was the first. On a
macOS runner, treat every coreutils/findutils flag as suspect: the GNU spelling usually fails
silently or with an unhelpful exit code rather than a readable error.
**Also added:** `altool --validate-app` before `--upload-app`. A build number can never be
reused once App Store Connect has seen it, so one extra call to reject a bad binary is cheaper
than burning the number.

### 2026-07-28 — `altool` failed in 1s with no output; cause was a malformed `.p8` secret
**Symptom:** `Upload to App Store Connect` failed in about a second, printing nothing. The
archive, export and artifact upload had all succeeded — a valid 839 KB signed IPA existed.
**Cause:** `APPSTORE_CONNECT_PRIVATE_KEY` did not contain a PEM `.p8`. `altool` exits without
any usable message when the key it is handed cannot be parsed, so the failure was invisible
from the workflow's own output. The usual way to get this wrong is to paste the key as a
single line, or with literal `\n` escapes instead of real newlines, or to paste only the
base64 body without the `-----BEGIN PRIVATE KEY-----` / `-----END PRIVATE KEY-----` lines.
**Fix (workflow):** the step now preflights before calling `altool` and prints, without ever
echoing key material: whether the key file exists, its byte count, whether its first 27 bytes
are `-----BEGIN PRIVATE KEY-----`, and the *lengths* of the Key ID (must be 10) and Issuer ID
(must be 36, a UUID). A bad header emits `::warning::` naming the secret. `altool` output is
tee'd to a log and its last 40 lines are appended to `$GITHUB_STEP_SUMMARY` on failure.
**Fix (secret):** re-paste the whole `AuthKey_<KEYID>.p8` file, BEGIN/END lines included, with
real newlines. GitHub's secret box accepts multi-line values as-is.
**Check for it:** a CI step that fails in ~1s with no output is almost always failing on its
*input*, not its work. Make the step describe its inputs before blaming the tool — two
guessing rounds here cost more than the preflight did to write.

### 2026-07-28 — the diagnostics above never printed: `set -e` from `bash -e {0}`
**Symptom:** with a valid key (`key file header : OK (PEM)`), `altool` ran for 20s and the step
died on `##[error]Process completed with exit code 1` — still with none of the diagnostics,
no `--- altool --validate-app (exit N) ---`, no `cat "$log"`, no step summary.
**Cause:** GitHub runs a `run:` block as `shell: /bin/bash -e {0}`. **`set -e` is applied on
the command line, so a `set -uo pipefail` inside the script does not turn it off** — and
`set -o pipefail` without `-e` reads as if it had. The pattern

```bash
cmd > "$log" 2>&1
status=$?          # ← never reached when cmd fails
```

exits the shell at `cmd`. Every diagnostic after it was dead code, which is why the
carefully-added error reporting produced nothing twice in a row.
**Fix:** `status=0; cmd > "$log" 2>&1 || status=$?`. The `||` puts the command in a condition,
where `set -e` is suppressed by POSIX rule, so the assignment and everything after it runs.
**Check for it:** `status=$?` on the line after a bare command is a bug in *any* GitHub
`run:` block, not just this one. If a step must survive a failing command to report on it, the
command needs `||`, `if`, or an explicit `set +e`. Verify with `bash -e` locally —
`printf 'set -uo pipefail\nfalse\necho reached\n' | bash -e` prints nothing.

---

## 8. Pre-flight checklist before merging to `main`

- [ ] `actionlint` clean, if any workflow file changed
- [ ] `ci.yml` green on the PR
- [ ] `verify-app-classes.sh` passed (it runs inside `ci.yml`)
- [ ] `versionCode` will exceed the highest already on the internal track
- [ ] No new JVM-only APIs in `shared/src/commonMain` — `:shared:compileCommonMainKotlinMetadata` passes
- [ ] Secrets referenced by any new workflow step actually exist in repo settings
- [ ] If the release workflow file was renamed, the `run_number` offset was bumped

## 9. Rollback

- **Play:** the API cannot un-publish. Halt or discard the release in Play Console → Testing →
  Internal testing. Then ship a forward fix with a higher `versionCode`.
- **TestFlight:** expire the build in App Store Connect.
- Never attempt to reuse a version number; both stores reject it permanently.
