import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Release signing and versioning are driven by the environment so that CI can supply them
// without any of it being committed. See .github/workflows/release.yml and
// .claude/skills/release-pipeline.md. `providers.environmentVariable` is used rather than
// `System.getenv` so the reads are declared inputs and the configuration cache stays valid.
// takeIf(isNotBlank) is load-bearing: a workflow that computes this value conditionally sets
// the variable to an EMPTY STRING rather than leaving it unset, and `orNull` then returns ""
// rather than null. Without this, the block below runs with a blank path and file("") throws
// while the project is being evaluated — failing the build before any task runs.
val releaseKeystorePath: String? =
  providers.environmentVariable("KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }

android {
  namespace = "com.splitcruiser.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.splitcruiser.app"
    minSdk = 24
    targetSdk = 36
    // Play rejects an upload whose versionCode does not exceed the published one (currently 2).
    versionCode = providers.environmentVariable("VERSION_CODE").orElse("2").get().toInt()
    versionName = providers.environmentVariable("VERSION_NAME").orElse("1.0.1").get()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      // Left unconfigured when KEYSTORE_PATH is absent, so local release builds produce an
      // unsigned artifact instead of failing on a null password.
      if (releaseKeystorePath != null) {
        storeFile = file(releaseKeystorePath)
        storePassword = providers.environmentVariable("STORE_PASSWORD").orNull
        keyAlias = providers.environmentVariable("KEY_ALIAS").orElse("upload").get()
        keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (releaseKeystorePath != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    // debug uses AGP's auto-generated debug keystore.
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Must match android.compileOptions above, or the build fails with
// "Inconsistent JVM-target compatibility detected between compileDebugJavaWithJavac (11)
// and compileDebugKotlin (1.8)".
kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.storage)
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Moshi codegen (and therefore KSP) removed: nothing in this project is annotated with
  // @JsonClass — SawaariRepository uses the reflective KotlinJsonAdapterFactory instead.
}
