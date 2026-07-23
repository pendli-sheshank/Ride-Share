plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
}

kotlin {
  // Targets
  androidTarget()
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  // Common source set
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization)
      implementation(libs.koin.core)
      implementation(libs.retrofit)
      implementation(libs.converter.moshi)
      implementation(libs.logging.interceptor)
      implementation(libs.okhttp)
      implementation(libs.moshi.kotlin)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.koin.test)
    }

    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.firebase.auth)
      implementation(libs.firebase.firestore)
      implementation(libs.firebase.storage)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.compose.material3)
      implementation(libs.androidx.core.ktx)
    }

    androidUnitTest.dependencies {
      implementation(libs.robolectric)
    }

    iosMain.dependencies {
      // iOS-specific dependencies will be added during Phase 6
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

  buildFeatures {
    compose = true
  }
}
