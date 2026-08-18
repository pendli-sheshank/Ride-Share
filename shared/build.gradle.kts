import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidTarget()

  // An XCFramework, not a fat framework. `linkReleaseFrameworkIosFat` lipos iosArm64 (device)
  // together with iosX64 (simulator) into one binary; App Store upload rejects an archive whose
  // embedded framework carries simulator slices (ITMS-90240). An XCFramework keeps device and
  // simulator slices in separate, correctly-tagged directories, so the same artifact serves both
  // local simulator runs and TestFlight uploads.
  val xcf = XCFramework("Shared")

  // Each iOS target exports a static Shared.framework for the Xcode project to link against.
  listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "Shared"
      isStatic = true
      xcf.add(this)
    }
  }

  sourceSets {
    commonMain.dependencies {
      // Only genuinely multiplatform artifacts belong here. Retrofit, OkHttp and Moshi were
      // previously declared in commonMain: they are JVM-only, have no Kotlin/Native artifacts,
      // and break iOS dependency resolution before anything can even be compiled. They were
      // also entirely unused by this module's sources.
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization)
      implementation(libs.kotlinx.datetime)
      implementation(libs.koin.core)

      // ktor-client-core is engine-free and resolves for every target. The engines below are
      // declared per platform; never move one of them up here.
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.client.mock)
    }

    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.ktor.client.okhttp)
      // Keystore-backed EncryptedSharedPreferences for the session store (holds the refresh token).
      implementation(libs.androidx.security.crypto)
      // Compose remains in app module only (platform-specific UI)
    }

    iosMain.dependencies {
      // NSURLSession-backed, pure Kotlin/Native. Needs no CocoaPods entry, which is the whole
      // reason the Firebase backend is spoken over REST rather than through the native SDK.
      implementation(libs.ktor.client.darwin)
    }
  }
}

// --- Firebase configuration ---------------------------------------------------------------
//
// Both platforms read their Firebase settings from this one generated file. :app picks it up
// through `implementation(project(":shared"))`; the iOS app gets it baked into
// Shared.xcframework when :shared:assembleSharedReleaseXCFramework runs. That is why the CI
// workflows pass the FIREBASE_* secrets to the Gradle step rather than only writing .env.
//
// `providers.environmentVariable` (never System.getenv) keeps the configuration cache valid —
// same reason app/build.gradle.kts reads VERSION_CODE that way. `.orElse("")` matters too: a
// conditional CI step sets a missing secret to the empty string, not to absent.
val firebaseApiKey = providers.environmentVariable("FIREBASE_API_KEY").orElse("")
val firebaseProjectId = providers.environmentVariable("FIREBASE_PROJECT_ID").orElse("")
val firebaseStorageBucket = providers.environmentVariable("FIREBASE_STORAGE_BUCKET").orElse("")

// The OAuth 2.0 **Web** client ID Firebase creates when the Google sign-in provider is enabled —
// not the Android client ID. Credential Manager asks Google for an ID token whose audience is this
// value, and Identity Toolkit only accepts a token minted for the project's own web client. Empty
// is a supported state: the Google button hides itself rather than failing at the tap.
val googleWebClientId = providers.environmentVariable("GOOGLE_WEB_CLIENT_ID").orElse("")

// This project's Firestore database was created with the id "splitcruiser" rather than the
// "(default)" every project gets automatically — the console's "Create database" dialog accepts
// any Database ID typed over its suggestion, with no warning that doing so means every
// `databases/(default)/...` REST call this app makes will 404 against a database that, from the
// API's point of view, does not exist. Overridable via FIRESTORE_DATABASE_ID for anyone who
// creates a fresh (default) database instead.
//
// `.orElse("")`, not `.orElse("splitcruiser")` — matching every other FIREBASE_* var above, and
// for the same reason: a GitHub Actions `env:` line referencing an unset secret does not leave the
// variable absent, it sets it to the empty string, so `.orElse(...)` never fires in CI regardless
// of what it names. The "splitcruiser" fallback that actually reaches production is applied to the
// *value*, below, with `ifBlank`, which catches both "absent" and "present but empty".
val firestoreDatabaseId = providers.environmentVariable("FIRESTORE_DATABASE_ID").orElse("")

// Google Places (New) autocomplete key. Optional: when empty, the location search falls back to the
// free Photon/OSM path, so a build without this secret behaves exactly as before. Metered when set —
// see OsmLocationService. Passed to the Gradle step by CI the same way as the FIREBASE_* secrets.
val googleMapsApiKey = providers.environmentVariable("GOOGLE_MAPS_API_KEY").orElse("")

val firebaseConfigDir: Provider<Directory> = layout.buildDirectory.dir("generated/firebaseConfig")

val generateFirebaseConfig by tasks.registering {
  val apiKey = firebaseApiKey
  val projectId = firebaseProjectId
  val storageBucket = firebaseStorageBucket
  val webClientId = googleWebClientId
  val databaseId = firestoreDatabaseId
  val mapsApiKey = googleMapsApiKey
  val outputDir = firebaseConfigDir

  inputs.property("firebaseApiKey", apiKey)
  inputs.property("firebaseProjectId", projectId)
  inputs.property("firebaseStorageBucket", storageBucket)
  inputs.property("googleWebClientId", webClientId)
  inputs.property("firestoreDatabaseId", databaseId)
  inputs.property("googleMapsApiKey", mapsApiKey)
  outputs.dir(outputDir)

  doLast {
    // A value can legitimately contain characters that would break out of a Kotlin string
    // literal, so escape rather than interpolate blindly.
    fun quote(raw: String): String = buildString {
      append('"')
      raw.forEach { c ->
        when (c) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '$' -> append("\\$")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          else -> append(c)
        }
      }
      append('"')
    }

    val target = outputDir.get().asFile.resolve("com/splitcruiser/app/data")
    target.mkdirs()
    target.resolve("FirebaseBuildConfig.kt").writeText(
      """
      package com.splitcruiser.app.data

      // GENERATED by the :shared `generateFirebaseConfig` task. Do not edit.
      // Values come from the FIREBASE_* environment variables at build time.
      object FirebaseBuildConfig {
          const val API_KEY: String = ${quote(apiKey.get())}
          const val PROJECT_ID: String = ${quote(projectId.get())}
          const val STORAGE_BUCKET: String = ${quote(storageBucket.get())}
          const val FIRESTORE_DATABASE_ID: String = ${quote(databaseId.get().ifBlank { "splitcruiser" })}
          const val GOOGLE_WEB_CLIENT_ID: String = ${quote(webClientId.get())}
          const val MAPS_API_KEY: String = ${quote(mapsApiKey.get())}
      }
      """.trimIndent() + "\n"
    )
  }
}

kotlin.sourceSets.commonMain.configure { kotlin.srcDir(generateFirebaseConfig) }

// Wiring the generated directory into the source set is not enough on its own. KotlinCompileTool
// is the common supertype of the JVM, metadata *and* Native compile tasks — the narrower
// KotlinCompilationTask would leave the iOS link tasks to infer the dependency, which is exactly
// the sort of thing that only fails on the macOS runner. The Jar rule covers sourcesJar, which
// Gradle's task validation otherwise flags for consuming an undeclared output.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool>().configureEach {
  dependsOn(generateFirebaseConfig)
}
tasks.withType<Jar>().configureEach {
  dependsOn(generateFirebaseConfig)
}

android {
  namespace = "com.splitcruiser.app.shared"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}
