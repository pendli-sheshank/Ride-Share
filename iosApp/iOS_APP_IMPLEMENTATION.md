# iOS App Implementation Guide

## Overview
The iOS Split Cruiser app is built with native SwiftUI and integrates with the shared Kotlin Multiplatform models and business logic via the Shared.framework.

## Architecture

### App Structure
```
iosApp/
├── iosApp.xcodeproj/          # Xcode project (auto-generated)
├── iosApp/
│   ├── iOSApp.swift           # App entry point (@main)
│   ├── ContentView.swift       # Main navigation & tabs
│   ├── ViewModel.swift         # iOS-Swift bridge to shared models
│   ├── Info.plist             # App configuration
│   ├── LaunchScreen.storyboard # Launch screen UI
│   └── Assets.xcassets/       # App icons & colors
├── setup.sh                   # Development setup script
├── Podfile                    # CocoaPods dependencies (optional)
└── iOS_PROJECT_SETUP.md       # Original setup guide
```

### Integration Points

**Shared Framework** (`../shared/build/XCFrameworks/Shared.xcframework/`)
- Provides multiplatform models: `User`, `TripOffer`, `RideRequest`, `TripMatch`, etc.
- Contains business logic through `SplitCruiserRepository`
- All models use Kotlin `@Serializable` for JSON/Firestore serialization

**ViewModel Bridge** (`ViewModel.swift`)
- Adapts shared Kotlin models to Swift/SwiftUI
- Manages local state with `@Published` properties
- Provides async/await interface for iOS concurrency model

## App Features

### 1. Authentication
**Screen:** `LoginView`
- Phone number + password login
- Sign-up for new users
- Error handling and loading states

**ViewModel Methods:**
- `loginUser(phoneNumber:password:)` - Authenticate user
- `currentUser` - Published user state

### 2. Browse Rides (Home Tab)
**Screen:** `HomeTabView` + `RideDetailView`
- Display available trip offers
- Filter by route, price, seats
- Request a ride

**Data:**
- `activeOffers: [TripOffer]` - Available rides
- Each offer shows: origin, destination, cost, seats left, vehicle info

**ViewModel Methods:**
- `fetchActiveOffers()` - Load available rides
- `acceptMatch(TripMatch)` - Book a ride

### 3. My Rides
**Screen:** `MyRidesTabView`
- View posted trip offers
- View active ride requests
- Manage ongoing rides

**Data:**
- `activeOffers` - User's posted rides
- `activeRequests` - User's ride requests
- `userMatches` - Active ride matches

### 4. Messages
**Screen:** `MessagesTabView`
- (Placeholder for future messaging UI)
- Will integrate with shared `Message` model

### 5. Profile
**Screen:** `ProfileTabView`
- User information (name, email, phone)
- Rating and statistics
- Logout functionality

**Data:**
- `currentUser: User?` - Current logged-in user
- Shows rating average and ride count

## Data Flow

```
SwiftUI Views
    ↓
ViewModel (AppViewModel)
    ↓
Shared.framework
    ├── SplitCruiserRepository (business logic)
    └── Models (User, TripOffer, etc.)
    ↓
Firebase/Firestore (backend)
```

### Example Flow: Create Ride Request

```swift
// 1. User fills out form in CreateRideView
let request = RideRequest(
    id: UUID().uuidString,
    riderId: viewModel.currentUser!.id,
    origin: "NEU",
    destination: "Logan Airport",
    seatsNeeded: 2
)

// 2. Call ViewModel method
await viewModel.createRideRequest(request)

// 3. ViewModel calls shared repository
// repository.createRideRequest(request)

// 4. Repository persists to Firebase
// Firestore update + local state refresh

// 5. SwiftUI re-renders
@Published var activeRequests updates → UI refreshes
```

## Building & Running

### Prerequisites
- macOS 12+ with Xcode 14+
- Java 21 (for Gradle to build shared framework)
- Shared.framework already built (run `./setup.sh`)

### Local Development

1. **Initial Setup:**
```bash
cd iosApp
./setup.sh
```

This script:
- Generates Xcode project if needed
- Builds Shared.framework from Kotlin Multiplatform
- Sets up CocoaPods dependencies

2. **Open in Xcode:**
```bash
open iosApp.xcodeproj
```

3. **Select Target & Scheme:**
- Target: `iosApp`
- Scheme: `iosApp`
- Destination: iOS Simulator or connected device

4. **Build & Run:**
- Press `Cmd+R` or Product → Run
- Or use terminal: `xcodebuild -scheme iosApp`

## Shared Models Usage

### Importing Shared Types
```swift
import Shared

// User model
let user = User(
    id: "user_123",
    name: "Alice",
    email: "alice@example.com",
    ratingAvg: 4.8
)

// TripOffer model
let offer = TripOffer(
    id: UUID().uuidString,
    hostId: "host_456",
    origin: "Boston",
    destination: "NYC",
    costPerRider: 25.0,
    seatsLeft: 3
)

// Use in views
ForEach(viewModel.activeOffers, id: \.id) { offer in
    RideOfferRow(offer: offer)
}
```

### Type Compatibility
- Kotlin `Long` (timestamps) → Swift `Int64`
- Kotlin `List<String>` → Swift `[String]`
- Kotlin `Double` → Swift `Double`
- Kotlin `Boolean` → Swift `Bool`
- Kotlin `null` (Optional) → Swift `Optional`

## ViewModel Extension Points

### Add New Data Streams
```swift
// In AppViewModel
@Published var userReviews: [Rating] = []

// Add async method
func fetchUserReviews() async {
    // Call: repository.fetchUserReviews()
}
```

### Add New Actions
```swift
func updateUserProfile(_ updatedUser: User) async {
    // Call: repository.updateUser(updatedUser)
}
```

## State Management

### Published Properties
All major data is wrapped in `@Published` for SwiftUI binding:
- `@Published var currentUser` - Reactive to auth changes
- `@Published var activeOffers` - Updates when rides change
- `@Published var isLoading` - Loading state UI
- `@Published var errorMessage` - Error display

### View State
- `@State` for local UI state (text fields, selections)
- `@ObservedObject` for ViewModel access in views
- `@Binding` for parent-child state sharing

## Testing

### Preview Testing
All views include `ContentView_Previews` for SwiftUI Canvas preview.

To test a specific view:
1. Select the view file in Xcode
2. Press `Cmd+Opt+Enter` to show Canvas
3. Adjust `AppViewModel` initialization for test data

### Manual Testing Checklist
- [ ] Login with phone number
- [ ] Sign up new account
- [ ] Browse available rides
- [ ] Request a ride
- [ ] View my posted rides
- [ ] Check profile information
- [ ] Logout
- [ ] Network error handling
- [ ] Shared.framework linking verification

## Troubleshooting

### "Cannot find Shared in scope"
- Run `./setup.sh` to build Shared.framework
- Verify path: `Build Settings` → `Framework Search Paths`
- Clean build: `Cmd+Shift+K`, then rebuild

### Crashes on Model Access
- Verify Kotlin model changes have been rebuilt
- Run `./gradlew :shared:linkReleaseFrameworkIosFat`
- Clean Xcode DerivedData: `rm -rf ~/Library/Developer/Xcode/DerivedData`

### ViewModel Methods Not Working
- TODO: Implement repository methods in `SplitCruiserRepository.kt`
- Current implementation has placeholders for Firebase integration
- Check `.logs()` in Xcode console for runtime errors

## Next Steps

### Phase 1: Core Features (Current)
- ✅ Login/authentication UI
- ✅ Browse available rides
- ✅ Profile management
- ⏳ Shared framework integration (TODO: complete repository methods)

### Phase 2: Enhanced Features
- [ ] Real-time messaging
- [ ] Advanced filtering (date, price, route)
- [ ] Rating & review system
- [ ] In-app notifications
- [ ] Payment integration

### Phase 3: Optimization
- [ ] Performance tuning
- [ ] Offline sync
- [ ] Network error recovery
- [ ] Analytics integration
- [ ] App Store release pipeline

## Resources

- [SwiftUI Documentation](https://developer.apple.com/tutorials/swiftui)
- [Shared Framework Documentation](../iOS_PROJECT_SETUP.md)
- [Kotlin Multiplatform for iOS](https://kotlinlang.org/docs/multiplatform-mobile.html)
- [Build & Release](../../../.claude/skills/release-pipeline/SKILL.md)
