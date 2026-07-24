plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidTarget()

  // Each iOS target exports a static Shared.framework for the Xcode project to link against.
  listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "Shared"
      isStatic = true
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
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }

    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
    }
  }
}

android {
  namespace = "com.example.shared"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}
