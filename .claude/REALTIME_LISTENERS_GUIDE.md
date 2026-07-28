# Real-time Listeners - Complete Guide

## Overview

Real-time listeners enable live data synchronization between Firestore and the app using snapshot listeners. When data changes in Firestore, the app receives updates instantly, providing a seamless user experience without manual refresh.

## Architecture

### Firestore Snapshot Listeners

Snapshot listeners subscribe to document/collection changes and receive callbacks whenever data updates:

```kotlin
collection.addSnapshotListener { snapshot, error ->
    if (error != null) {
        // Handle connection error
        return@addSnapshotListener
    }
    if (snapshot != null) {
        // Process updated documents
        val documents = snapshot.documents.mapNotNull { it.toObject(Model::class.java) }
        // Update UI
    }
}
```

### Connection Management

```kotlin
private val _isConnected = MutableStateFlow<Boolean>(isFirebaseEnabled)
val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

private val _lastSyncTime = MutableStateFlow<Long>(0L)
val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

private var listenerRegistrations = mutableListOf<ListenerRegistration>()
```

- **isConnected**: Track online/offline status
- **lastSyncTime**: Show when last sync occurred
- **listenerRegistrations**: Manage listener lifecycle

## Implemented Listeners

### 1. Trip Offers Listener
```kotlin
firebaseFirestore?.collection("trip_offers")?.addSnapshotListener { snapshot, error ->
    // Listen to all trip offers
    // Updates: _tripOffers StateFlow
    // Actions: updateFeeds(), updateUI()
}
```

**Events:**
- New offer posted
- Offer seats changed (passengers joined)
- Offer status changed (active → full → completed)
- Offer cancelled

**Result:**
- Instant update in `activeOffers` feed
- Expired offers auto-hidden

### 2. Ride Requests Listener
```kotlin
firebaseFirestore?.collection("ride_requests")?.addSnapshotListener { snapshot, error ->
    // Listen to all ride requests
    // Updates: _rideRequests StateFlow
    // Actions: updateFeeds(), updateUI()
}
```

**Events:**
- New request posted
- Request status changed (active → matched → completed)
- Request cancelled

**Result:**
- Instant update in `activeRequests` feed
- Notifications sent to matching drivers

### 3. Trip Matches Listener
```kotlin
firebaseFirestore?.collection("trip_matches")?.addSnapshotListener { snapshot, error ->
    // Listen to all trip matches
    // Updates: _tripMatches StateFlow
    // Actions: updateFeeds(), updateUI()
}
```

**Events:**
- New match created (pending)
- Match accepted by passenger
- Match cancelled by either party
- Match completed

**Result:**
- Update user's active matches list
- Notify both parties of status changes
- Update hosted/joined trips counts

### 4. Messages Listener
```kotlin
firebaseFirestore?.collection("messages")?.addSnapshotListener { snapshot, error ->
    // Listen to all messages
    // Updates: _messages StateFlow
    // No filtering - all participants get full history
}
```

**Events:**
- New message sent
- System messages (match accepted, cancelled)
- Message timestamps

**Result:**
- Real-time chat updates
- No manual refresh needed
- Message ordered by timestamp

### 5. Notifications Listener
```kotlin
firebaseFirestore?.collection("notifications")?.addSnapshotListener { snapshot, error ->
    // Listen to user-specific notifications
    // Filters: userId matches current user
    // Updates: _notifications StateFlow
}
```

**Events:**
- Match events (driver interested, passenger accepted)
- New messages received
- Ride accepted/cancelled
- Rating requests

**Result:**
- Instant notification delivery
- Sorted by timestamp (newest first)
- Auto-cleaned on dismissal

### 6. Users Listener
```kotlin
firebaseFirestore?.collection("users")?.addSnapshotListener { snapshot, error ->
    // Listen to user profile updates
    // Updates: _users StateFlow, _currentUser if match
}
```

**Events:**
- Profile updates (name, avatar, etc.)
- Rating changes (from rating system)
- Verification status changes
- Vehicle info updates

**Result:**
- Current user profile updated instantly
- Other users' profiles cached
- Display names updated in offers/requests

## Real-time Data Flow

```
┌─────────────────────────────────────────────────┐
│         Firestore Database                      │
│  ┌──────────────────────────────────────────┐  │
│  │ Collection Changes (write by any client) │  │
│  └──────────────────────────────────────────┘  │
│                      ↓                          │
│  ┌──────────────────────────────────────────┐  │
│  │  Snapshot Listener (detects change)      │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│       SplitCruiserRepository (Local Cache)           │
│  ┌──────────────────────────────────────────┐  │
│  │ Callback: Document data received         │  │
│  │ - Parse to object                        │  │
│  │ - Merge with local state                 │  │
│  │ - Update StateFlow                       │  │
│  │ - Save to JSON file                      │  │
│  │ - Trigger UI update                      │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│       UI Layer (Compose State)                  │
│  ┌──────────────────────────────────────────┐  │
│  │ Compose collects from StateFlow          │  │
│  │ - Re-compose triggered                   │  │
│  │ - Lists updated instantly                │  │
│  │ - New offers/requests appear             │  │
│  │ - Messages appear in chat                │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

## Connection States

### Online (Connected)
- All listeners active
- Real-time updates received instantly
- Automatic sync on write
- `isConnected = true`

### Offline (Disconnected)
- Listeners paused
- Local writes queued
- Read from local cache
- `isConnected = false`

### Recovering
- Listeners re-establish
- Queued writes sync
- Remote changes received
- `isConnected = true`

## Setup & Lifecycle

### Initialization (In `init {}`)
1. Firebase initialized
2. `observeDataChanges()` called
3. Listeners registered in `setupRealtimeListeners()`
4. Local cache loaded from JSON files
5. Listeners start receiving updates

### Ongoing (While App Running)
```kotlin
scope.launch {
    while (true) {
        delay(10000) // Every 10 seconds
        updateFeeds(_currentUser.value) // Auto-hide expired rides
    }
}
```

### Cleanup (On Logout or App Close)
```kotlin
fun logout() {
    stopRealtimeListeners()    // Remove all listeners
    _currentUser.value = null  // Clear user
}

fun stopRealtimeListeners() {
    listenerRegistrations.forEach { it.remove() }
    listenerRegistrations.clear()
}
```

## Offline Persistence

### Firestore Persistence
```kotlin
firebaseFirestore?.firestoreSettings = FirestoreSettings.Builder()
    .setPersistenceEnabled(true)
    .build()
```

Caches all local writes and reads:
1. User posts offer → Written locally first
2. Listener detects local change
3. `_tripOffers` updated immediately
4. UI refreshes instantly
5. Background sync to Firestore (async)
6. If offline, queued until connection restored

### Local JSON Fallback
Each StateFlow backed by JSON file:
```
_tripOffers ↔ trip_offers.json
_rideRequests ↔ ride_requests.json
_tripMatches ↔ trip_matches.json
_messages ↔ messages.json
```

On cold start:
1. Load from JSON if Firebase unavailable
2. Show cached data immediately
3. When Firebase connects, listeners sync
4. Old JSON data merged with Firestore

## Manual Sync

### Force Sync All Data
```kotlin
suspend fun syncDataWithFirestore(): Result<Unit>
```

**When to use:**
- After resuming from background
- When network reconnected
- Manual "Pull to Refresh"
- Debugging data consistency

**Process:**
1. Fetch all offers/requests/matches/messages
2. Merge with local state
3. Save to JSON
4. Update feeds
5. Record sync time

**Usage:**
```kotlin
// In ViewModel or UI
viewModelScope.launch {
    when (val result = repository.syncDataWithFirestore()) {
        is Result.Success -> showToast("Synced!")
        is Result.Failure -> showToast("Sync failed")
    }
}
```

## Error Handling

### Listener Errors
```kotlin
if (error != null) {
    Log.e("Split Cruiser", "Listen failed: ${error.message}")
    _isConnected.value = false
    // Fall back to JSON cache
    return@addSnapshotListener
}
```

**Common errors:**
- **Permission denied**: User auth token expired
- **Connection error**: Network unavailable
- **Invalid listener**: Collection doesn't exist

**Recovery:**
1. Set `isConnected = false`
2. Use local cache
3. Retry when connection restored
4. Auto-sync when reconnected

### Graceful Degradation
```
Offline (no listeners):
├─ Show cached offers/requests
├─ Allow local writes (queue them)
├─ Show last sync time
└─ Show "Offline" badge

Online (listeners active):
├─ Real-time updates
├─ Sync queued writes
├─ Update current sync time
└─ Remove "Offline" badge
```

## Performance Optimization

### Listener Scope (Efficient)
```kotlin
// ✅ Good: Listen to specific data
firebaseFirestore?.collection("trip_offers")
    ?.addSnapshotListener { snapshot, error -> ... }

// ❌ Expensive: Listen to entire database
firebaseFirestore?.addSnapshotListener { snapshot, error -> ... }
```

### Filtering on Client (After Listener)
```kotlin
val filteredOffers = _tripOffers.value.values.filter { offer ->
    offer.status == "active" &&
    offer.hostId != currentUserId &&
    !blockedUserIds.contains(offer.hostId) &&
    offer.departureTime > now
}
```

### Query Efficiency
Current: Listen to entire collection, filter client-side
Future: Firestore queries with composite indexes
```kotlin
// Future enhancement
firebaseFirestore?.collection("trip_offers")
    ?.whereEqualTo("status", "active")
    ?.whereGreaterThan("departureTime", now)
    ?.addSnapshotListener { snapshot, error -> ... }
```

### Memory Management
```kotlin
listenerRegistrations.forEach { it.remove() }  // Unsubscribe
_tripOffers.value = emptyMap()                  // Clear cache
```

## Testing Scenarios

### Scenario 1: New Offer Posted
```
1. User A posts offer: NEU → Logan
2. Firestore writes document
3. Listener detects change
4. _tripOffers updated
5. User B (different device) sees offer instantly
6. No manual refresh needed
```

### Scenario 2: Match Accepted
```
1. Match status: pending
2. Passenger [Accepts]
3. Firestore updates match document
4. Listener detects change
5. Both driver & passenger see "accepted"
6. UI updates instantly on both devices
```

### Scenario 3: New Message
```
1. Driver sends message
2. Firestore writes message document
3. Listener detects new message
4. _messages updated
5. Passenger receives message instantly
6. Notification sent
7. No polling required
```

### Scenario 4: Offline Transition
```
1. User online, listening to offers
2. Network disconnected
3. isConnected = false
4. Listeners paused
5. User sees cached offers (last known state)
6. User posts new offer (queued locally)
7. Network restored
8. Listeners re-activate
9. Queued offer syncs to Firestore
10. All documents reconciled
```

### Scenario 5: Profile Rating Update
```
1. Trip completed
2. Both parties submit ratings
3. Firestore updates user rating fields
4. User listener detects change
5. _users cache updated
6. Current user profile refreshed
7. Rating badge updated in UI instantly
```

## Debugging

### Check Connection Status
```kotlin
// In UI
val isConnected by repo.isConnected.collectAsState()
Text(if (isConnected) "🟢 Connected" else "🔴 Offline")
```

### Check Last Sync Time
```kotlin
val lastSync by repo.lastSyncTime.collectAsState()
Text("Last sync: ${formatTime(lastSync)}")
```

### Verify Listeners Active
```kotlin
// In Repository (debug only)
Log.d("Split Cruiser", "Active listeners: ${listenerRegistrations.size}")
// Output: Active listeners: 6 (trip_offers, ride_requests, trip_matches, messages, notifications, users)
```

### Force Refresh
```kotlin
// Manual sync button in settings
onRefreshClick {
    viewModelScope.launch {
        repo.syncDataWithFirestore()
    }
}
```

## Firestore Rules for Listeners

### Security Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can listen to active offers
    match /trip_offers/{offerId} {
      allow read: if request.auth != null;
    }
    
    // Users can listen to active requests
    match /ride_requests/{requestId} {
      allow read: if request.auth != null;
    }
    
    // Users can listen to own matches
    match /trip_matches/{matchId} {
      allow read: if request.auth.uid in [resource.data.hostId, resource.data.riderId];
    }
    
    // Users can listen to own messages
    match /messages/{messageId} {
      allow read: if request.auth.uid in [get(/databases/$(database)/documents/trip_matches/$(resource.data.matchId)).data.hostId, resource.data.senderId];
    }
    
    // Users can listen to own notifications
    match /notifications/{notifId} {
      allow read: if request.auth.uid == resource.data.userId;
    }
  }
}
```

## Future Enhancements

### Phase 2: Optimized Queries
- [ ] Firestore composite indexes for filtered queries
- [ ] Query-level filtering instead of client-side
- [ ] Reduce bandwidth by listening to subset

### Phase 3: Advanced Features
- [ ] Offline writes queue with sync indicator
- [ ] Conflict resolution for concurrent writes
- [ ] Batch sync (sync multiple changes at once)
- [ ] Exponential backoff for failed syncs

### Phase 4: Performance
- [ ] Pagination (listen to first 50, load on scroll)
- [ ] Incremental updates (only send deltas)
- [ ] Connection pooling
- [ ] Listener priority (e.g., messages before offers)

---

**Last Updated**: 2026-07-23
**Status**: Real-time listeners complete with offline support and manual sync

