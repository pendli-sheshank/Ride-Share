# iOS Project Setup Guide

This document describes the iOS project structure and how to build and run the SawaariShare app on iOS.

## Project Structure

```
iosApp/
├── generate-project.py          # Python script to generate valid Xcode project
├── create-xcode-project.sh      # Wrapper script for project generation
├── setup.sh                     # Setup script for development environment
├── Podfile                      # CocoaPods dependency configuration
├── iosApp.xcodeproj/            # Xcode project (auto-generated)
│   ├── project.pbxproj          # Main project configuration
│   └── project.xcworkspace/     # Xcode workspace
└── iosApp/                      # Swift source code and resources
    ├── iOSApp.swift             # App entry point
    ├── ContentView.swift        # Main UI view
    ├── Info.plist               # App configuration
    ├── LaunchScreen.storyboard  # Launch screen UI
    └── Assets.xcassets/         # App assets (icons, colors)
        ├── AppIcon.appiconset/  # App icon definitions
        └── AccentColor.colorset/ # App accent color
```

## Build System

### Project Generation

The Xcode project is **generated automatically** using `generate-project.py`. This approach:

- ✅ Ensures consistent, valid Xcode project structure
- ✅ Makes the project reproducible and version-controllable
- ✅ Avoids manual pbxproj editing errors
- ✅ Can be regenerated if corrupted

### Build Phases

The iOS app build includes these phases:

1. **Sources**: Compiles Swift files (iOSApp.swift, ContentView.swift)
2. **Frameworks**: Links Shared.framework from Kotlin Multiplatform
3. **Resources**: Copies assets, Info.plist, and LaunchScreen.storyboard

## Development Workflow

### Prerequisites

- **macOS 12+** with Xcode 14+
- **Java 21** (for Gradle)
- **Ruby** and **CocoaPods** (optional, for pod dependencies)

### Local Setup on macOS

1. **Set up the iOS environment:**

```bash
cd iosApp
./setup.sh
```

This script:
- Generates the Xcode project (if needed)
- Builds Shared.framework from Kotlin Multiplatform
- Installs CocoaPods dependencies (if configured)

2. **Open in Xcode:**

```bash
open iosApp.xcodeproj
```

3. **Select build configuration:**
   - Scheme: `iosApp`
   - Destination: iOS Simulator (or connected device)

4. **Build and Run:**
   - Press `Cmd+R` to build and run
   - Or use Product → Build/Run menu

## Components

### Swift Files

- **iOSApp.swift**: App entry point with @main attribute
- **ContentView.swift**: SwiftUI view with navigation and Shared framework integration

### Resources

- **Info.plist**: App metadata (bundle ID, permissions, deployment target)
- **LaunchScreen.storyboard**: Launch screen UI
- **Assets.xcassets**: App icons and colors

### Build Configurations

- **Debug**: Development build with optimizations disabled
- **Release**: Production build with optimizations enabled

## Kotlin Multiplatform Integration

### Shared Framework

The iOS app links against **Shared.framework**, which is built from KMP:

```bash
./gradlew :shared:linkReleaseFrameworkIosFat -x test
```

### Framework Path

```
../shared/build/XCFrameworks/Shared.xcframework
```

This framework contains all Kotlin Multiplatform code compiled for iOS.

## Build Settings

### Target Deployment

- **Minimum Deployment Target**: iOS 16.0
- **Supported Devices**: iPhone and iPad (both orientations)
- **Swift Version**: 5.0

### Code Signing

- **Code Sign Identity**: iPhone Developer (automatic)
- **Development Team**: Set in Xcode

## Troubleshooting

### Project Generation Issues

If the Xcode project becomes corrupted:

```bash
rm -rf iosApp.xcodeproj
./create-xcode-project.sh
```

### Framework Not Found

Ensure Shared.framework is built:

```bash
cd .. && ./gradlew :shared:linkReleaseFrameworkIosFat && cd iosApp
```

### CocoaPods Issues

If pod install fails, you can still build without external pods:

```bash
rm Podfile.lock
pod repo update
pod install
```

### Xcode Cache Issues

Clear Xcode cache:

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData
```

## CI/CD Integration

The iOS build is validated in GitHub Actions (`.github/workflows/build-ios.yml`):

1. Checks out code
2. Sets up Java 21 and Gradle
3. Builds Shared.framework for iOS targets
4. Validates Swift code syntax

For full iOS app builds, use local macOS development environment.

## Next Steps

1. **Implement Location Services** for ride matching
2. **Add Network Layer** for API communication
3. **Build Authentication UI** for user login
4. **Integrate with Shared Models** from KMP
5. **Set up Tests** for Swift code
