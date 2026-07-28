# Split Cruiser iOS App

iOS implementation of Split Cruiser using Kotlin Multiplatform and SwiftUI.

## Architecture

- **Shared Framework**: `../shared/` — Kotlin Multiplatform code compiled to `Shared.xcframework`
- **iOS App**: Swift/SwiftUI frontend that calls Kotlin code via the framework
- **Deployment Target**: iOS 16.0+

## Prerequisites

- macOS 12.0+
- Xcode 14.0+
- CocoaPods: `sudo gem install cocoapods`
- Java 21 (for building Shared.framework)

## Setup

### 1. Build Shared Framework

From the project root:

```bash
./gradlew :shared:assembleSharedXCFramework
```

This compiles Kotlin code for iOS targets (iphoneArm64, iphoneSimulatorArm64, iphoneX64) and generates `Shared.xcframework`.

### 2. Install CocoaPods Dependencies

```bash
cd iosApp
pod install
```

This installs the `Shared` framework pod and generates `iosApp.xcworkspace`.

### 3. Create Xcode Project

If the `.xcodeproj` doesn't exist yet, you'll need to create it in Xcode:

1. Open Xcode
2. **File** → **New** → **Project**
3. Choose **iOS** → **App**
4. Configure:
   - **Product Name**: `iosApp`
   - **Team ID**: (if you have one)
   - **Organization Identifier**: `com.splitcruiser`
   - **Bundle Identifier**: `com.splitcruiser.app`
   - **Language**: Swift
   - **Storage Location**: Save in `iosApp/`
5. **Create**

### 4. Link Shared Framework

In Xcode:

1. Select the **iosApp** project
2. Select the **iosApp** target
3. **Build Phases** tab → **Link Binary With Libraries**
4. Ensure `Shared.framework` is listed

### 5. Open Workspace

Always open `iosApp.xcworkspace` (not `.xcodeproj`):

```bash
open iosApp.xcworkspace
```

## Building & Running

### From Xcode

1. Select a simulator or device
2. Press `Cmd+R` to build and run
3. Alternatively: **Product** → **Run** (or **Cmd+R**)

### From Command Line

```bash
xcodebuild -workspace iosApp.xcworkspace \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build
```

## Development Workflow

1. **Kotlin changes**: Modify code in `../shared/src/commonMain/` or `../shared/src/iosMain/`
2. **Rebuild framework**: `./gradlew :shared:assembleSharedXCFramework`
3. **Swift changes**: Modify code in `iosApp/`
4. **Build in Xcode**: Press `Cmd+B` or `Cmd+R`

## Current Status

- ✅ Shared framework configured for iOS targets
- ✅ Basic SwiftUI app structure
- ✅ CocoaPods setup
- ⏳ UI migration to Compose Multiplatform (Task #3)
- ⏳ Firebase integration for iOS
- ⏳ Release pipeline setup

## Troubleshooting

**"Shared.framework not found"**
- Run `./gradlew :shared:assembleSharedXCFramework`
- Run `pod install`
- Clean Xcode: `Cmd+Shift+K`

**Pod installation fails**
- Update CocoaPods: `sudo gem install cocoapods`
- Clear cache: `rm -rf Pods && pod install`

**Xcode build fails**
- Ensure Java 21 is installed
- Check Xcode is up to date
- See build logs in Xcode

## Next Steps

1. Create Xcode project (.xcodeproj) if needed
2. Reconcile shared models (:shared dependency) — Task #2
3. Migrate UI to Compose Multiplatform — Task #3
4. Implement Firebase for iOS — Task #4
5. Set up release pipeline — Task #5
