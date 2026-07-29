# Trip Matching & Messages - Complete Guide

## Overview

Trip Matching is the core mechanism connecting drivers and passengers in Split Cruiser. When a driver accepts a passenger's ride request (or vice versa), a TripMatch document is created that enables real-time messaging, cost negotiation, and trip coordination.

## Firestore Collection: `trip_matches`

### Document Structure
```
trip_matches/{matchId}
├── id: String                          // Unique match ID (match_xxxxx)
├── offerId: String                     // Reference to trip_offers/{offerId}
├── requestId: String                   // Reference to ride_requests/{requestId}
├── hostId: String                      // Driver's user ID
├── riderId: String                     // Passenger's user ID
├── riderName: String                   // Passenger's display name ("Maria G.")
├── riderRating: Float                  // Passenger's rating at match time
├── contribution: Double                // Total cost for this rider ($)
├── status: String                      // "pending" | "accepted" | "completed" | "cancelled"
└── timestamp: Long                     // Match creation time (Unix ms)
```

### Example Match
```json
{
  "id": "match_a1b2c3d4",
  "offerId": "offer_xyz789",
  "requestId": "request_abc123",
  "hostId": "user_driver_123",
  "riderId": "user_rider_456",
  "riderName": "Maria G.",
  "riderRating": 4.5,
  "contribution": 17.00,
  "status": "accepted",
  "timestamp": 1721754000000
}
```

## Trip Matching Lifecycle

### Stage 1: Driver Accepts Ride Request
```kotlin
suspend fun createTripMatch(offerId: String, requestId: String): Result<String>
```

**Preconditions:**
- Offer is "active" or "full"
- Offer has seatsLeft >= request.seatsNeeded
- Request is "active"
- No existing match for this offer/request pair
- User is logged in

**Process:**
1. Validate offer & request compatibility
2. Generate unique matchId (match_xxxxx)
3. Create TripMatch with status = "pending"
4. Add to local `_tripMatches` StateFlow
5. Persist to `trip_matches.json`
6. Sync to Firestore `trip_matches` collection
7. Create system message in chat
8. Send notification to passenger

**Result:**
```kotlin
TripMatch(
    id = "match_a1b2c3d4",
    offerId = "offer_xyz789",
    requestId = "request_abc123",
    hostId = "user_driver_123",      // Current user
    riderId = "user_rider_456",       // Requester
    riderName = "Maria G.",
    riderRating = 4.5,
    contribution = 17.00,              // costPerRider × seatsNeeded
    status = "pending",                // Waiting for passenger acceptance
    timestamp = System.currentTimeMillis()
)
```

**Notification Sent:**
- Title: "Driver Interested! 🚗"
- Message: "{Driver Name} is interested in your NEU → Logan request for $17.00"
- Type: "match"

### Stage 2: Message Exchange (Optional)
Driver and passenger can message before confirming:
```kotlin
suspend fun sendMessage(matchId: String, text: String): Result<Unit>
```

**Messages Flow:**
1. Message created with unique ID (msg_xxxxx)
2. Added to local `_messages` StateFlow
3. Persisted to `messages.json`
4. Synced to Firestore `messages` collection (async)
5. Recipient notified in real-time

**Notification Sent (on new message):**
- Title: "New Message from {Sender Name} 💬"
- Message: Message content preview
- Type: "new_message"

### Stage 3: Passenger Confirms (Accept Match)
```kotlin
suspend fun acceptMatch(matchId: String)
```

**Transition:**
- Match status: "pending" → "accepted"
- Request status: "active" → "matched"
- Offer seats: Decrement by seatsNeeded
- Offer status: "active" → "full" (if no seats left)

**After Acceptance:**
1. Passenger added to offer's passengers list
2. Seats reduced in trip offer
3. System message: "Trip request accepted by host! You can now chat and coordinate cash-in-person split."
4. Notification: "Ride Request Accepted! 🚗"
5. Match is now "confirmed" - trip coordination begins

### Stage 4: Trip Coordination
While match is "accepted", participants can:
- Exchange messages for logistics
- Confirm meeting location/time
- Verify vehicle details
- Establish pickup/dropoff details
- Handle special requests

### Stage 5: Complete Trip
```kotlin
suspend fun completeTrip(matchId: String)
```

**Transition:**
- Match status: "accepted" → "completed"
- Offer status: Updated if all matches done

**After Completion:**
1. Both parties can rate each other
2. Rating triggers feedback system
3. Ratings persist in `ratings` collection
4. User averages recalculated

**Parallel: Decline/Cancel**
```kotlin
suspend fun declineMatch(matchId: String)    // Before accepted
suspend fun cancelMatch(matchId: String)      // Anytime
```

## Messaging System

### Firestore Collection: `messages`

```
messages/{messageId}
├── id: String                          // Unique message ID (msg_xxxxx)
├── matchId: String                     // Reference to trip_matches/{matchId}
├── senderId: String                    // Sender user ID or "system"
├── senderName: String                  // Display name or ""Split Cruiser" (the system sender name)"
├── text: String                        // Message content
└── timestamp: Long                     // Unix timestamp (milliseconds)
```

### Message Types

**1. User Messages**
- Sent by driver or passenger
- Enables real-time coordination
- Stored with user ID and display name

**2. System Messages**
- Sent by "Split Cruiser" (the system sender name) (senderId = "system")
- Announce match events:
  - "Match created! Waiting for driver to confirm."
  - "Trip request accepted by host! You can now chat and coordinate cash-in-person split."
  - "Match cancelled by {User Name}"

**3. Notifications (Separate)**
- Alert messages sent to users
- Stored in `notifications` collection
- Not part of chat history

### Getting Chat Messages
```kotlin
suspend fun getMatchConversation(matchId: String): Flow<List<Message>>
```

Returns real-time flow of all messages for a match, sorted by timestamp.

**Usage:**
```kotlin
// In UI layer (Compose):
val messages by repo.getMatchConversation(matchId).collectAsState(emptyList())

LazyColumn {
    items(messages) { message ->
        MessageBubble(message)
    }
}
```

### Sending Messages
```kotlin
suspend fun sendMessage(matchId: String, text: String): Result<Unit>
```

**Process:**
1. Validate user logged in
2. Create message with timestamp
3. Update local flow
4. Save to messages.json
5. Async sync to Firestore
6. Send notification to recipient

**Error Cases:**
- Not logged in → Result.failure("Not logged in")
- Match not found → No recipient notification
- Firebase sync fails → Still stored locally

### Read Receipts
```kotlin
suspend fun markMessagesAsRead(matchId: String, readUntilTimestamp: Long)
```

Marks notification alerts as read for messages received before given timestamp.

## Trip Matching Operations

### Fetching Matches
```kotlin
// Get user's matches (both hosted and joined)
suspend fun getUserMatches(userId: String): Result<Pair<List<TripMatch>, List<TripMatch>>>
// Returns: (hostedMatches, joinedMatches)

// Get single match
fun getTripMatchById(matchId: String): TripMatch?

// Get active matches
suspend fun getActiveMatches(): List<TripMatch>
```

### Match Status Transitions

```
┌─────────────────────────────────────────┐
│  PENDING (Driver accepted request)      │
│                                         │
│  Passenger sees notification:           │
│  "Driver Interested! 🚗"               │
│                                         │
│  Waiting for passenger to:              │
│  ├─ [Accept] → ACCEPTED                │
│  ├─ [Decline] → CANCELLED              │
│  └─ [Ignore] → CANCELLED (timeout)     │
└─────────────────────────────────────────┘
          ↓ [Accept]
┌─────────────────────────────────────────┐
│  ACCEPTED (Confirmed booking)           │
│                                         │
│  Match is confirmed:                    │
│  ├─ Passenger added to offer            │
│  ├─ Seats decremented                   │
│  ├─ Message channel active              │
│  ├─ Both parties can chat               │
│  │                                      │
│  Either party can:                      │
│  ├─ [Complete] → COMPLETED             │
│  ├─ [Cancel] → CANCELLED               │
│  └─ Message for coordination            │
└─────────────────────────────────────────┘
          ↓ [Complete]
┌─────────────────────────────────────────┐
│  COMPLETED (Trip finished)              │
│                                         │
│  ├─ Both parties rate each other       │
│  ├─ Ratings update averages            │
│  ├─ Feedback recorded                  │
│  ├─ Match history visible              │
│  └─ Message archive available          │
└─────────────────────────────────────────┘
```

## Cost & Contribution

### Contribution Calculation
```kotlin
contribution = offer.costPerRider × request.seatsNeeded

// Example:
offer.costPerRider = $8.50
request.seatsNeeded = 2
contribution = $8.50 × 2 = $17.00
```

### Safety Caps
```kotlin
maxContribution = offer.costPerRider × 2

// Example:
costPerRider = $8.50
maxContribution = $17.00

// Passenger cannot pay more than $17.00
```

### Payment Flow (Future)
1. Driver offers ride at costPerRider
2. Passenger requests X seats
3. Match created with total contribution
4. Before/after trip: payment collected
5. Split Cruiser takes commission (5-10%)
6. Driver receives: contribution - commission

## Data Retrieval & Queries

### Get Match Details
```kotlin
suspend fun getMatchDetails(matchId: String): Result<MatchDetails>
```

Returns complete match info with:
- TripMatch document
- TripOffer details
- RideRequest details
- Host profile info
- Rider profile info

**Usage:**
```kotlin
when (val result = repo.getMatchDetails(matchId)) {
    is Result.Success -> {
        val details = result.value
        showMatchHeader(details.hostProfile, details.riderProfile)
        showRouteInfo(details.offer, details.request)
        showContribution(details.match.contribution)
    }
    is Result.Failure -> showError(result.exception.message)
}
```

## Real-time Features

### Live Message Updates
Messages update in real-time through StateFlow:
```kotlin
// In repository
repo.getMatchConversation(matchId).collect { messages ->
    updateUI(messages)
}
```

### Firestore Listeners (Future)
Currently uses polling on app focus. Future enhancement:
```kotlin
firebaseFirestore?.collection("messages")
    ?.whereEqualTo("matchId", matchId)
    ?.addSnapshotListener { snapshot, error ->
        snapshot?.documents?.mapNotNull { it.toObject(Message::class.java) }
            ?.let { updateUI(it) }
    }
```

## Validation & Error Handling

### Match Creation Validation
```
✓ Offer status is "active" or "full"
✓ Offer has seatsLeft >= request.seatsNeeded
✓ Request status is "active"
✓ No duplicate match for this offer/request
✓ User logged in
✗ Any failure → Result.failure()
```

### Message Sending Validation
```
✓ Match exists
✓ User logged in
✓ Message text not empty
✓ Recipient user found
✗ Firebase sync failure → Still saves locally
```

### Match Cancellation Validation
```
✓ Match exists
✓ User is host or rider
✓ If accepted, revert seat count
✓ Notify other party
✗ Only host/rider can cancel
```

## Error Messages

| Error | Cause | Fix |
|-------|-------|-----|
| "Offer not found" | Offer deleted/moved | Refresh feed |
| "Request not found" | Request deleted | Refresh feed |
| "Not enough seats available" | Offer full | Choose different offer |
| "Request is no longer active" | Request completed/cancelled | Post new request |
| "Already matched" | Offer/request already linked | Check matches |
| "Not logged in." | Session expired | Login again |
| "Match not found" | Invalid match ID | Refresh screen |
| "Only host or rider can cancel" | Wrong user | Only participants can cancel |

## UI Screens

### 1. Match Card (Browse/Feed)
Shows on offer/request cards after match created:
- Driver/Passenger profile
- Rating & verification badge
- Vehicle info (if driver)
- Cost total
- [Accept/Decline] buttons (if pending)
- [Message] button

### 2. Match Details Screen
Full match information:
- Both party profiles
- Origin → Destination route
- Departure time & ETA
- Cost breakdown
- Vehicle details
- Message history
- [Accept/Complete/Cancel] buttons
- Message input field

### 3. Message Thread
Real-time chat interface:
- Conversation history (newest last)
- System messages (match events)
- Send message input
- User avatars & names
- Timestamps

### 4. Active Trips Tab
Lists user's current matches:
- Hosted trips (driver view)
- Joined trips (passenger view)
- Status badges: pending/accepted/completed
- Next departure countdown
- Quick actions: [Message], [Cancel]

## Testing Scenarios

### Scenario 1: Driver Accepts Request
```
1. Passenger posts: NEU → Logan, 2 seats, 2 hrs from now
2. Driver sees notification: "New Ride Request Matching Your Trip!"
3. Driver opens request details
4. [Accept] → Match created
5. Passenger notified: "Driver Interested! 🚗"
6. Match status = "pending"
7. Both can message to confirm
```

### Scenario 2: Passenger Accepts Match
```
1. Match status = "pending"
2. Passenger reads driver's message: "Can pick up at NEU front"
3. Passenger sends: "Perfect! See you at 2pm"
4. Passenger [Confirms] → acceptMatch()
5. Match status = "accepted"
6. Passenger added to driver's offer
7. Seats reduced in offer
8. System message: "Trip request accepted by host!"
```

### Scenario 3: Message Exchange
```
1. Match pending
2. Driver: "I drive a silver Camry, license ABC1234"
3. Passenger: "Great, I'll be by the front entrance"
4. Driver: "Perfect! See you at 2pm"
5. Passenger confirms match
6. Match accepted
```

### Scenario 4: Trip Completion
```
1. Match accepted
2. Departure time arrives
3. Trip in progress (both parties message)
4. Trip completes
5. completeTrip() called
6. Match status = "completed"
7. Rating dialog shown
8. Both rate each other: ⭐4.5, ⭐5.0
9. Ratings recorded
10. User averages updated
```

### Scenario 5: Cancel After Acceptance
```
1. Match accepted
2. Driver needs to cancel (emergency)
3. [Cancel] → cancelMatch()
4. Seats reverted in offer
5. Status = "cancelled"
6. Passenger notified: "Driver Cancelled Match ❌"
7. Offer reopened for other riders
```

## Performance Optimization

### Firestore Queries
```
Efficient (indexed):
  - Get matches by hostId or riderId
  - Filter by status
  - Sort by timestamp

Expensive (full scan):
  - Search by route
  - Search by cost range
  - Text search in messages
```

### Caching Strategy
- **Local Cache:** All matches in `_tripMatches` StateFlow
- **Message Cache:** All messages in `_messages` StateFlow
- **Refresh:** On app launch, every 30 seconds
- **Invalidation:** Match created/accepted, message sent

### Large Scale (Many Messages)
If message history grows large:
- Paginate by date range
- Load last 50 messages initially
- Load older messages on scroll
- Archive old conversations

## Integration Points

### With User Profiles
- Driver/passenger profiles displayed
- Ratings shown
- Verification badges
- Block list checked

### With Trip Offers
- Offer seat count decremented
- Offer status updated (active → full)
- Offer completion triggered

### With Ride Requests
- Request status updated (active → matched)
- Request completion triggered

### With Ratings
- After trip completion
- Both parties submit ratings
- Ratings update user averages

### With Notifications
- Match created → notify passenger
- New message → notify recipient
- Match cancelled → notify other party

## Security Considerations

### Firestore Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /trip_matches/{matchId} {
      allow read: if request.auth.uid in [resource.data.hostId, resource.data.riderId];
      allow create: if request.auth.uid == request.resource.data.hostId;
      allow update: if request.auth.uid in [resource.data.hostId, resource.data.riderId];
      allow delete: if false; // Never delete, only cancel
    }
    match /messages/{messageId} {
      allow read: if request.auth.uid in get(/databases/$(database)/documents/trip_matches/$(resource.data.matchId)).data.hostId;
      allow create: if request.auth.uid == request.resource.data.senderId;
      allow delete: if false;
    }
  }
}
```

### Privacy
- Messages only visible to match participants
- Cannot access other users' matches/messages
- User profiles show public info only
- Phone numbers hidden (future: show after match accepted)

## Future Enhancements

### Phase 2: Real-time Sync
- [ ] Firestore snapshot listeners for live messages
- [ ] Typing indicators ("User is typing...")
- [ ] Read receipts ("Seen at 2:30pm")
- [ ] Online status indicators

### Phase 3: Trip Coordination
- [ ] Real-time location sharing during trip
- [ ] ETA updates to passengers
- [ ] Driver can route modifications (detours)
- [ ] SOS/emergency button in trip

### Phase 4: Advanced Features
- [ ] Audio/video calling through match
- [ ] File sharing (receipts, contact info)
- [ ] Automatic message cleanup (after 30 days)
- [ ] Conversation pinning
- [ ] Message reactions (emoji)

---

**Last Updated**: 2026-07-23
**Status**: Core trip matching and messaging system complete

