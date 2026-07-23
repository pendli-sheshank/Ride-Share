plugins {
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":shared"))
    }
  }
}

// iOS app targets for future SwiftUI/Compose implementation
// CocoaPods integration will be configured during Phase 6
