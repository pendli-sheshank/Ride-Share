# UI Migration to Compose Multiplatform - Strategy & Progress

## Overview
The Split Cruiser app currently has an 8000+ line Jetpack Compose UI in the Android module. The goal is to migrate this to Kotlin Multiplatform to support both Android and iOS platforms while sharing UI logic.

## Current State (Task #3)

### Completed
✅ Created commonMain UI structure in shared module:
- `shared/src/commonMain/kotlin/com/splitcruiser/app/ui/`
- Theme files (Color.kt, Type.kt, Theme.kt) moved to commonMain
- Platform-specific theme implementations for Android and iOS
- PlatformContext abstraction for vibration and alerts
- Basic App.kt composable entry point

✅ Abstracted platform-specific APIs:
- `shouldUseDarkTheme()` - with Android/iOS implementations
- `PlatformContext.vibrate()` - haptic feedback
- `PlatformContext.showMessage()` - toast notifications

### Current Limitations
❌ Full Compose Multiplatform support not yet available in build dependencies
- Current setup uses Android Compose only
- Compose Multiplatform would require adding `org.jetbrains.compose` plugin
- Adds significant complexity and requires library updates

## Migration Strategy

### Phase 1: Infrastructure (Current - Task #3)
✅ Set up expect/actual declarations
✅ Move theme definitions to commonMain
✅ Create platform abstractions for system APIs
- [ ] Add Compose Multiplatform plugin to build.gradle
- [ ] Resolve dependency versions for multiplatform Compose

### Phase 2: Component Migration (Future Task)
- Move reusable UI components (cards, buttons, dialogs) to commonMain
- Extract navigation logic to common module
- Create ViewModel abstraction for multiplatform support
- Separate presentation layer from platform-specific concerns

### Phase 3: Platform-Specific UI (Tasks #4-5)
**Android:** Continue using existing Compose in app module
**iOS:** Create native SwiftUI UI that uses shared data models and business logic

**Rationale:** 
- Maximizes native experience on each platform
- Avoids complexity of forcing Compose Multiplatform adoption
- Shares data models, business logic, and state management
- Faster to implement and maintain

## Implementation Path

### Option A: Full Compose Multiplatform (Complex)
```
commonMain UI → Android (Compose) + iOS (Compose)
Benefits: UI code parity
Costs: Complexity, library maturity, larger binaries on iOS
```

### Option B: Selective Compose Multiplatform (Recommended)
```
commonMain: Theme, Models, Business Logic, State Management
Android: Full Compose UI
iOS: Native SwiftUI UI + Shared models
Benefits: Best UX per platform, manageable complexity
Costs: Duplicate UI code (but different frameworks)
```

### Option C: Current Approach (Minimum)
```
commonMain: Models, Business Logic
Android: Existing Compose UI
iOS: Minimal Compose + Native UI
Benefits: Easiest to implement now
Costs: Limited code sharing
```

## Recommendation
**Proceed with Option B** - It provides a good balance between code sharing (models, business logic) and platform-native user experience (Android Compose, iOS SwiftUI).

## Next Steps
1. Task #4: Build iOS app entry point with native SwiftUI UI
2. Task #5: Set up iOS release pipeline
3. Deferred: Full component migration to commonMain (requires more library support)

## Architecture After Migration

```
shared/
├── src/commonMain/
│   ├── data/          (Models, Repository)
│   ├── domain/        (Use cases, business logic)
│   ├── viewmodel/     (Multiplatform state management)
│   └── ui/            (Theme, reusable components)
├── src/androidMain/
│   └── ui/            (Platform-specific adaptations)
└── src/iosMain/
    └── ui/            (Platform-specific adaptations)

app/
├── ui/
│   └── SplitCruiserApp.kt (Android Compose UI - 8000 lines)
└── ...

iosApp/
├── iosApp/
│   ├── ContentView.swift (iOS SwiftUI UI)
│   └── ...
└── ...
```

## Dependency Considerations

### Current Dependencies
- androidx.compose.* (Android-only)
- androidx.lifecycle.* (Android-specific)
- kotlinx.serialization (✅ multiplatform)
- kotlinx.coroutines (✅ multiplatform)

### If Adopting Full Compose Multiplatform
Would add: `org.jetbrains.compose:compose-gradle-plugin`
- Requires Gradle 8.1+
- Supports Kotlin 2.0+
- Still experimental for iOS

## Known Issues & Workarounds
1. **No multiplatform ViewModel yet** - Use expect/actual or local MVI pattern
2. **Compose on iOS is preview** - Consider deferring until more stable
3. **Build complexity increases** - Good documentation and CI validation needed

## Files Modified
- `shared/src/commonMain/kotlin/com/splitcruiser/app/ui/theme/*.kt` - Common theme
- `shared/src/androidMain/kotlin/com/splitcruiser/app/ui/theme/*.kt` - Android theme impl
- `shared/src/iosMain/kotlin/com/splitcruiser/app/ui/theme/*.kt` - iOS theme impl
- `shared/src/commonMain/kotlin/com/splitcruiser/app/ui/PlatformContext.kt` - Platform API abstraction
- `shared/src/androidMain/kotlin/com/splitcruiser/app/ui/PlatformContext.kt` - Android implementation
- `shared/src/iosMain/kotlin/com/splitcruiser/app/ui/PlatformContext.kt` - iOS implementation
- `shared/src/commonMain/kotlin/com/splitcruiser/app/ui/App.kt` - Common app entry point

## References
- [Jetpack Compose Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-setup.html)
- [Kotlin Multiplatform expect/actual](https://kotlinlang.org/docs/multiplatform-expect-actual.html)
