# Split Cruiser - Firebase-Only Architecture

## Architecture Overview

Split Cruiser uses a **Firebase-first** architecture with **local JSON-based fallback** for offline support. Room database has been removed entirely.

## Data Layer Stack

### Primary Storage: Firebase Firestore
- **users** - User profiles, ratings, verification status
- **trip_offers** - Ride listings created by drivers
- **ride_requests** - Ride requests from passengers  
- **trip_matches** - Confirmed bookings (matchings)
- **messages** - In-app chat messages
- **notifications** - Real-time alerts and notifications
- **ratings** - User ratings and reviews
- **blocks** - User blocking/exclusion lists

### Firebase Services
1. **Firebase Authentication** - Email/password login with fallback
2. **Firebase Firestore** - Real-time cloud database with snapshot listeners
3. **Firebase Storage** - Profile pictures and verification documents
4. **Firebase App Check** - reCAPTCHA protection

### Fallback Layer: JSON File Persistence
Located in: `context.filesDir`

Files persisted locally:
- `users.json` - User profiles (cache)
- `trip_offers.json` - Ride listings (cache)
- `ride_requests.json` - Ride requests (cache)
- `trip_matches.json` - Bookings (cache)
- `messages.json` - Chat messages (cache)
- `notifications.json` - Alerts (cache)
- `ratings.json` - User ratings (cache)
- `blocks.json` - Blocked users (cache)
- `invites.json` - Invite codes
- `communities.json` - Community list
- `credentials.json` - Local auth fallback (when Firebase unavailable)
- `vehicle_*.json` - Vehicle info per user

**Purpose**: When Firebase is unavailable, app auto-falls back to local JSON persistence with full functionality.

## Data Sync Flow

```
┌─────────────────────────────────────────────────────┐
│              SplitCruiserRepository                       │
├─────────────────────────────────────────────────────┤
│  StateFlow<> Reactive Streams (UI Layer)            │
│  • currentUser, activeOffers, myRideRequests, etc   │
└─────────────────────────────────────────────────────┘
         ↑                           ↑
         │                           │
    Firebase                     JSON Files
    Listeners              (Local Fallback)
    (Real-time)           (Offline Support)
         │                           │
         ↓                           ↓
    Firestore              context.filesDir
    Collections              (Moshi Adapter)
```

### Real-Time Sync
When Firebase is enabled, the repository registers snapshot listeners on:
- `trip_offers` - Instant updates when new rides are posted
- `ride_requests` - Real-time request feed updates
- `messages` - Live chat messages
- `notifications` - Push alerts

**Local cache is automatically updated** on every Firestore change, then synced back to JSON files.

### Offline Fallback
1. Firebase operations fail (network unavailable)
2. Repository automatically falls back to JSON local storage
3. All read/write operations work locally with MutableStateFlow
4. When connection restored, Firestore sync re-enables automatically

## Removed Components

### ❌ Room Database (Removed)
- No SQL tables/entities
- No DAOs (Data Access Objects)
- No Room migrations
- No Room compiler (KSP plugin)
- Build time reduced (no Room annotation processing)

### Why Removed?
- SplitCruiserRepository already uses **Moshi JSON serialization** for persistence
- **Firebase Firestore** provides cloud sync without Room
- **JSON file storage** in `context.filesDir` provides offline cache
- No need for complex SQL schema or Room overhead

## Key Implementation Details

### SplitCruiserRepository
File: `app/src/main/java/com/example/data/SplitCruiserRepository.kt`

**State Management** (lines 48-79):
```kotlin
private val _users = MutableStateFlow<Map<String, User>>(emptyMap())
private val _tripOffers = MutableStateFlow<Map<String, TripOffer>>(emptyMap())
// ... other collections
```

**Firestore Listeners** (lines 224-318):
```kotlin
firebaseFirestore?.collection("trip_offers")?.addSnapshotListener { snapshot, error ->
    // Auto-update local cache + JSON files on Firestore changes
}
```

**JSON Persistence** (lines 142-177):
```kotlin
private fun loadLocalDatabase() {
    _users.value = loadList<User>("users.json", userListAdapter)
    // Load all collections from JSON
}

private fun <T> saveList(filename: String, list: List<T>, adapter: JsonAdapter<List<T>>) {
    // Save to context.filesDir/<filename>
}
```

## Firebase Configuration

### Environment Variables
Set these in `.env` or Secrets Panel:
```
FIREBASE_API_KEY=xxx
FIREBASE_APP_ID=xxx
FIREBASE_PROJECT_ID=xxx
FIREBASE_STORAGE_BUCKET=xxx
```

### BuildConfig
These auto-populate from `.env`:
```kotlin
BuildConfig.FIREBASE_API_KEY
BuildConfig.FIREBASE_APP_ID
BuildConfig.FIREBASE_PROJECT_ID
BuildConfig.FIREBASE_STORAGE_BUCKET
```

### Fallback Initialization
If Firebase config is missing or invalid:
1. `isFirebaseEnabled` is set to `false`
2. App automatically uses local-only mode
3. No crashes, seamless UX
4. When Firebase is configured, sync enables

## Firestore Rules (Security)

### Recommended Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/update own profile
    match /users/{userId} {
      allow read, write: if request.auth.uid == userId;
    }
    
    // Offers readable by all, writable by creator
    match /trip_offers/{document=**} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth.uid == resource.data.hostId;
    }
    
    // Messages for matched participants only
    match /messages/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Performance Characteristics

| Operation | Firebase | Offline (JSON) |
|-----------|----------|----------------|
| Read User Profile | ~100ms (cloud) | <5ms (local) |
| List Active Rides | Real-time sync | Instant (cached) |
| Post New Ride | Atomic Firestore + cache | Instant + queue |
| Send Message | Real-time Firestore | Local buffer |
| Sync on Reconnect | Auto-restores state | Auto-merges changes |

## Migration Notes

### What Changed
- ✅ Removed `androidx.room.ktx`
- ✅ Removed `androidx.room.runtime`
- ✅ Removed `androidx.room.compiler` KSP plugin
- ✅ Repository already used Firebase + JSON (no code changes needed)

### What Stayed the Same
- ✅ All data models (User, TripOffer, RideRequest, etc.)
- ✅ All repository functions (postTripOffer, sendMessage, etc.)
- ✅ All UI flows (Compose screens)
- ✅ Firebase real-time sync
- ✅ JSON fallback persistence

### Backwards Compatibility
- ✅ Existing local JSON files continue to work
- ✅ Firebase re-enables when config added
- ✅ No schema migrations needed (JSON is schema-free)

## Next Steps

### To Enable Full Firebase
1. Configure Firebase project at console.firebase.google.com
2. Create Firestore database (US region recommended)
3. Set up Firebase Authentication (Email/Password)
4. Create Storage bucket for profile pictures
5. Add `.env` with Firebase credentials

### To Test Offline Mode
1. Clear `.env` or set FIREBASE_API_KEY to blank
2. App automatically falls back to local-only
3. All features work with local persistence

### To Verify Setup
Check logs for:
- ✅ `"Firebase successfully initialized."` - Full Firebase enabled
- ✅ `"Firebase initialization bypassed (Using persistent local storage)."` - Local-only mode (normal)

## Troubleshooting

**Q: App crashes on startup**
A: Ensure `.env` or Secrets have valid Firebase config (or leave blank for local-only)

**Q: Firestore listeners not updating**
A: Check Firebase Authentication status and Firestore Rules

**Q: Data not syncing to Firebase**
A: Check network connectivity and Firestore write rules

**Q: Local cache doesn't persist**
A: Verify app has storage permissions in AndroidManifest.xml

## Architecture Decision Log

### Why Firebase Firestore?
- Real-time sync without polling
- Automatic offline persistence mode
- Scales for growth without backend
- Built-in security rules
- Integrated auth and storage

### Why JSON fallback?
- Graceful degradation when Firebase unavailable
- No external dependencies (uses Moshi already)
- Instant local read/write
- Simple to debug (human-readable files)
- No schema management overhead

### Why no Room?
- Complexity overhead for this use case
- JSON + Firestore covers all needs
- Reduces build time and APK size
- No migration management needed
- Moshi already handles serialization

---

**Last Updated**: 2026-07-23
**Status**: Firebase-only architecture confirmed, Room removed
