# SawaariShare

Community rideshare app for students. Started in Google AI Studio as an Android-only Jetpack
Compose app; being taken multiplatform.

## Releasing

**Read `.claude/skills/release-pipeline/SKILL.md` before touching anything under `.github/`,
signing config, version numbers, `:shared`, or the iOS project.** It carries the setup
runbooks, the secret inventory, and an append-only log of every CI failure hit so far.

**When a release or CI run fails, append the symptom, cause and fix to that skill's
"Known issues & fixes" section as part of the same change.** That log is the point of the
skill — several failures in it cost a CI round trip each to rediscover.

Before pushing a workflow change, run `actionlint`. It catches invalid contexts and expression
syntax in seconds; three separate CI failures here would have been caught by it.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :shared:compileCommonMainKotlinMetadata   # proxy for "will this compile for iOS?"
```

The Android SDK is not preinstalled in the Claude Code container — see §6 of the skill for the
one-time install.

## Layout

| Path | What |
|---|---|
| `app/` | Android application. `ui/SawaariApp.kt` is ~8,000 lines and holds nearly all the UI. |
| `shared/` | Kotlin Multiplatform library, Android + three iOS targets. |
| `iosApp/` | Reserved for the Xcode project. **Empty — iOS does not exist yet.** |

## Accuracy of the existing docs

The ~20 `.claude/*.md` guides predate the multiplatform work. **Treat their status claims as
unreliable:**

- They describe the project as Android-only and never mention KMP or iOS.
- `IMPLEMENTATION_STATUS.md` and `LAUNCH_REPORT.md` claim "95% complete" and "launch ready".
  That was written while the build was silently not compiling any Kotlin at all — the app did
  not build, and the artifact it produced contained none of the app's code.
- PR descriptions claiming "code compiles without errors" and a "comprehensive test suite"
  were not true: nothing was compiling, and four test suites referenced an API that was never
  written. They have been removed.

Their *feature* descriptions are still a useful map of intent. Their *completion* claims are
not evidence. Verify against a build.

## Known gaps

- No iOS app: no Xcode project, no Compose Multiplatform. All UI is Android-only Jetpack
  Compose, so a real iOS app requires migrating the UI to `commonMain` first.
- `:app` does not depend on `:shared`. `shared/src/commonMain/.../domain/usecase/*` and its
  copy of `Models.kt` are dead code, and the shared `Models.kt` has drifted from the Android
  one (285 vs 392 lines). Reconcile before wiring them together.
- Test coverage is close to zero after removing the suites that never compiled.
