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
│   ├── Theme.swift            # Brand palette + shared views, read from `:shared` tokens
│   ├── ContentView.swift       # Main navigation & tabs
│   ├── RideDetailView.swift   # The pre-booking trust screen
│   ├── ChatView.swift         # Coordinating a pickup after a match
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
- Email + password login, matching Android. It asked for a phone number until 2026-07; no
  backend ever authenticated against one.
- Sign-up for new users, with the same client-side checks Android does: 6-character minimum and
  a confirm-password field.
- Error handling and loading states

**ViewModel Methods:**
- `logIn(email:password:)` / `signUp(email:password:)` - Authenticate user
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

### 4. Matches and chat
**Screens:** `MatchesTabView` → `ChatView`
- Accept or decline a pending match
- Once accepted, open the conversation: message thread, quick replies, and a structured
  pickup proposal the other side can confirm
- Messages arrive through `repository.observeChat(matchId:)`, polled every 3s while open —
  Firestore's realtime channel is gRPC-only, so there are no snapshot listeners

**Message types** are a real field (`Message.type`, see `MessageType` in `:shared`), not a
`[PROPOSAL]` prefix parsed out of the text.

### 5. Profile
**Screen:** `ProfileTabView`
- The user's own avatar (`avatarUrl`, falling back to initials), name, email and contact number
- Rating and statistics
- Logout functionality
- Backend connectivity appears **only in debug builds**. It used to sit alongside the rating
  with the same visual weight, which is developer instrumentation shipped to riders.

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
- [ ] Login with email
- [ ] Sign up new account
- [ ] Browse available rides
- [ ] Request a ride
- [ ] View my posted rides
- [ ] Check profile information
- [ ] Open a ride's detail screen: host rating, vehicle, contact, cost breakdown, passengers
- [ ] Accept a match, then open the chat and confirm a pickup proposal
- [ ] Onboarding stores a phone number and home address; a new ride request prefills its pickup
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
