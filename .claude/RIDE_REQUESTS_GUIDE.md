# Ride Requests & Joining - Complete Guide

## Overview

Ride Requests are the passenger side of Split Cruiser - riders post their travel needs, and drivers can join or accept them. This complements Trip Offers (driver-centric) with a passenger-centric discovery model.

## Firestore Collection: `ride_requests`

### Document Structure
```
ride_requests/{requestId}
├── id: String                          // Unique request ID (request_xxxxx)
├── riderId: String                     // Passenger's user ID
├── riderName: String                   // Passenger's display name ("Maria G.")
├── riderRating: Float                  // Passenger's average rating (0.0-5.0)
├── origin: String                      // Pickup location ("NEU Snell Library")
├── destination: String                 // Dropoff location ("Boston Logan Airport")
├── originLat: Double                   // Pickup latitude
├── originLng: Double                   // Pickup longitude
├── destLat: Double                     // Dropoff latitude
├── destLng: Double                     // Dropoff longitude
├── originGeohash: String               // Geohash for proximity search
├── destGeohash: String                 // Geohash for proximity search
├── departureTime: Long                 // Unix timestamp (milliseconds)
├── seatsNeeded: Int                    // Number of seats (1-8)
├── notes: String                       // Additional info ("Quiet rider")
├── womenOnly: Boolean                  // Women drivers only
├── status: String                      // "active" | "matched" | "completed" | "cancelled"
└── created: Timestamp                  // Auto-generated creation time
```

## Ride Request Lifecycle

### Stage 1: Creation (Passenger Posts Request)
```kotlin
suspend fun postRideRequest(request: RideRequest): Result<Unit>
```

**Input:**
```kotlin
RideRequest(
    origin = "NEU Snell Library, Boston, MA",
    destination = "Logan Airport, Boston, MA",
    originLat = 42.3383,
    originLng = -71.0881,
    destLat = 42.3656,
    destLng = -71.0096,
    departureTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000), // 2 hours from now
    seatsNeeded = 2,
    notes = "Heading to airport, quiet rider",
    womenOnly = false
)
```

**Process:**
1. Validate all required fields
2. Generate unique request ID (request_xxxxx)
3. Calculate geohashes for location
4. Save to local `_rideRequests` StateFlow
5. Persist to `requests.json`
6. Sync to Firestore `ride_requests` collection
7. Auto-find matching trip offers
8. Send notifications to drivers with matching offers

**Validations:**
```
✓ Origin & destination not empty
✓ Coordinates valid (latitude -90 to 90, longitude -180 to 180)
✓ Departure time > current time (future only)
✓ Seats needed 1-8
✓ User must be logged in and verified
```

**Auto-Notifications Sent:**
- Email alert to drivers with matching offers (if enabled)
- Push notification to drivers with matching offers (if enabled)

### Stage 2: Driver Accepts Request
```kotlin
suspend fun acceptRideRequest(requestId: String): Result<Unit>
```

**Process:**
1. Driver sees matching request in notification or browse screen
2. Views request details (passenger profile, route, cost)
3. [Accept] to join as driver
4. Creates TripMatch document linking offer & request
5. Passenger notified (driver accepted)
6. Message channel opens between driver & passenger
7. Status updates: request "matched", offer shows passenger

**Validations:**
```
✓ Request still active
✓ User has available seat(s)
✓ User is not the requester
✓ Route compatibility verified
```

### Stage 3: Coordinate Details
During acceptance, driver and passenger can:
- Message through in-app chat
- Confirm pickup time/location
- Share vehicle details
- Establish ground rules (music, temperature, etc.)

### Stage 4: Complete Trip
```kotlin
suspend fun completeRideRequest(requestId: String): Result<Unit>
```

**Status Transitions:**
```
active   → matched     (driver accepts)
matched  → completed   (after ride finishes)
active   → cancelled   (passenger cancels)
matched  → cancelled   (either party cancels)
```

**Rules:**
- Only requester (riderId) can cancel before matched
- Either party can cancel after matched (with notification)
- Once completed, both parties can rate each other

## Matching Algorithm

### Auto-Matching on Ride Request Posting
When passenger posts request:
```kotlin
fun findMatchingOffers(request: RideRequest): List<TripOffer> {
    return _tripOffers.value.values.filter { offer ->
        offer.status == "active" &&                    // Offer still active
        offer.seatsLeft >= request.seatsNeeded &&      // Enough seats
        isRouteCompatible(offer, request) &&           // Route overlap
        !isBlocked(request.riderId, offer.hostId) &&   // Not blocked
        (!offer.womenOnly || // Offer is not women-only, OR
         request.womenOnly == false)                   // Passenger not filtering
    }
}

fun isRouteCompatible(offer: TripOffer, request: RideRequest): Boolean {
    val originDist = haversineDistance(
        offer.originLat, offer.originLng,
        request.originLat, request.originLng
    )
    val destDist = haversineDistance(
        offer.destLat, offer.destLng,
        request.destLat, request.destLng
    )
    
    // Allow up to 1 mile deviation on either end
    return originDist <= 1.609 && destDist <= 1.609 &&
           areDepartureTimesClose(offer.departureTime, request.departureTime)
}
```

### Notification Logic
```kotlin
val matchingOffers = findMatchingOffers(request)
matchingOffers.forEach { offer ->
    val driver = _users.value[offer.hostId]
    if (driver?.emailNotificationsEnabled == true) {
        sendNotificationAlert(
            targetUserId = offer.hostId,
            title = "New Ride Request Matching Your Trip!",
            message = "${request.riderName} needs ${request.seatsNeeded} seat(s) from ${request.origin} to ${request.destination}",
            type = "match"
        )
    }
}
```

## Cost Negotiation

### Cost Model
Driver sets costPerRider, passenger sees total cost:

```kotlin
totalCost = offer.costPerRider × request.seatsNeeded

// Example:
offer.costPerRider = $8.50
request.seatsNeeded = 2
totalCost = $8.50 × 2 = $17.00
```

### Cost Capping (Safety)
```
Max contribution per rider: 2× costPerRider

Example:
  costPerRider = $8.50
  maxContribution = $8.50 × 2 = $17.00
  
  Passenger cannot be charged more than $17.00
  (protects from unfair pricing)
```

### Payment Flow (Future)
1. Passenger posts request with budget/flexibility
2. Driver accepts with proposed cost
3. Passenger confirms before pickup
4. Payment collected (Venmo, card, etc.)
5. Split Cruiser takes commission (5-10%)
6. Driver receives: (costPerRider × seatsNeeded) - commission

## Comparison: Trip Offers vs Ride Requests

| Feature | Trip Offer | Ride Request |
|---------|-----------|--------------|
| Posted By | Driver | Passenger |
| Discovery | Passengers browse offers | Drivers browse requests |
| Capacity | Fixed (4 seats) | Variable (1-8 seats) |
| Matching | Automatic via feed | Auto-notifications |
| Initiation | Passenger joins offer | Driver accepts request |
| Cost | Driver sets price | Driver proposes price |
| Status | active/full/completed | active/matched/completed |
| Visibility | All riders see active offers | All drivers see active requests |

**Use Case Examples:**
- Trip Offer: "I'm driving to airport Tuesday 2pm, 4 seats, $8.50 each"
- Ride Request: "Need 2 seats to airport Tuesday 1-3pm, budget $15"

## Data Retrieval Methods

### Fetch Single Request
```kotlin
fun getRideRequestById(requestId: String): RideRequest?
suspend fun fetchRideRequestFromFirestore(requestId: String): Result<RideRequest>
```

### Fetch User's Posted Requests
```kotlin
suspend fun getPassengerRequests(riderId: String): List<RideRequest>
// Returns: All requests posted by this passenger
```

### Fetch Active Requests (Browse)
```kotlin
fun getActiveRequests(): List<RideRequest>
// Returns: All active requests sorted by departure time
```

Filtered by `updateFeeds()`:
- Excludes expired requests (departureTime < now)
- Excludes blocked users
- Excludes if women-only and requester is male
- Sorts by rating (high first)

## Validation & Error Handling

### Pre-Creation Validation
```kotlin
fun validateRideRequest(request: RideRequest): Result<Unit> {
    if (request.origin.trim().isEmpty() || request.destination.trim().isEmpty()) {
        return Result.failure(Exception("Pickup and dropoff are required"))
    }
    if (request.departureTime <= System.currentTimeMillis()) {
        return Result.failure(Exception("Departure time must be in the future"))
    }
    if (request.seatsNeeded < 1 || request.seatsNeeded > 8) {
        return Result.failure(Exception("Seats needed must be 1-8"))
    }
    return Result.success(Unit)
}
```

### Accept Validation
```
✓ Request is active
✓ Driver has available seat(s)
✓ Driver is not the requester
✓ Route compatible (same/near location)
✓ Driver logged in
```

### Cancellation Validation
```
✓ Request exists
✓ Either: requester (before matched), OR both parties agree (after matched)
✓ Can't cancel if completed
```

## Error Messages

| Error | Cause | Fix |
|-------|-------|-----|
| "Please log in to post a request." | Not authenticated | Login first |
| "Pickup and dropoff are required." | Missing location | Enter both locations |
| "Departure time must be in the future." | Time in past | Select future time |
| "Seats needed must be between 1 and 8." | Invalid seat count | Choose 1-8 seats |
| "No drivers available for this route." | No matches | Try different time/location |
| "You cannot request from your own offer." | Can't join own request | Only drivers post requests |
| "Driver already has matching request." | Duplicate match | Choose different driver |

## UI Screens

### 1. Post Ride Request Screen
**Inputs:**
- Pickup location (autocomplete)
- Dropoff location (autocomplete)
- Departure date/time picker
- Number of seats (1-8)
- Notes/preferences (optional)
- Women drivers only toggle

**Actions:**
- [Post Request] - Submit request
- [Cancel] - Discard

### 2. Browse Ride Requests Tab (Driver View)
**Shows:**
- List of active requests matching area
- Each card displays:
  - "Maria G. needs: NEU → Logan"
  - 2 seats needed
  - Departs in 45 mins
  - Rating: ⭐4.2 (8 ratings)
  - Notes: "Quiet rider"

**Actions:**
- [View Details] - See full request + passenger profile
- [Accept] - Join as driver

### 3. Request Details Screen (Driver View)
- Passenger profile: "Maria G." ⭐4.2 (8 ratings)
- Origin & destination
- Departure time countdown
- Seats needed
- Notes/preferences
- Cost estimate
- [Accept Request] button
- [Message Passenger] option

### 4. My Ride Requests Tab (Passenger View)
**Shows:**
- List of requests posted by user
- Status: active/matched/completed
- [View Details] to see interested drivers
- [Cancel] if still active

## Testing Scenarios

### Scenario 1: Post & Get Accepted
```
1. Passenger logs in
2. Click "Post Ride Request"
3. Enter: NEU → Logan, 2 hrs from now, 2 seats
4. [Post Request] → Request created with status "active"
5. Driver logs in (different user)
6. Browses "Ride Requests" feed
7. Sees passenger's request
8. [Accept] → Creates TripMatch
9. Result:
   - Request status = "matched"
   - TripMatch document created
   - Both parties notified
10. Open message channel
```

### Scenario 2: Auto-Matching Notification
```
1. Driver posts trip offer: NEU → Logan, 4 seats, $8.50
2. Passenger posts request: Same route, 2 seats
3. System auto-finds matching offer
4. Driver receives notification: "Maria G. needs 2 seats on your NEU→Logan trip"
5. Driver sees request in app
6. [Accept] to confirm
```

### Scenario 3: Cancel Request
```
1. Passenger posts request
2. Changes mind before any driver accepts
3. [Cancel Request]
4. Status = "cancelled"
5. Removed from driver browse feed
6. No notifications sent
```

### Scenario 4: Complete Matched Request
```
1. Driver accepted request (status = "matched")
2. Trip time arrives
3. Message exchange: confirm meeting spot, etc.
4. Trip completed
5. Status = "completed"
6. Both parties prompted to rate each other
7. Ratings update their profiles
```

### Scenario 5: Multiple Drivers Interested
```
1. Passenger posts request
2. Multiple drivers see notification
3. First driver [Accepts] → status "matched"
4. Other drivers see request now "matched"
5. Cannot accept matched request
6. Passenger can message accepted driver about confirmation
```

## Performance Optimization

### Firestore Queries
```
Expensive (full collection scan):
  - Search by passenger name
  - Search by budget range
  - Search by notes content

Optimized (indexed queries):
  - By status: "active"
  - By departureTime: > now
  - By geohash: proximity search
```

### Caching Strategy
- **Local Cache:** All requests stored in `_rideRequests` StateFlow
- **Refresh:** On app launch, every 30 seconds, on user action
- **Invalidation:** New request posted, driver accepted, status changed

## Integration Points

### Trip Matching (TripMatch)
When driver accepts request:
- Creates `trip_matches` document
- Links offer + request + driver + passenger
- Opens messaging channel
- Enables real-time coordination

### Notifications
- Auto-notify drivers when request posted
- Notify passenger when driver accepts
- Notify if driver cancels
- Notify when trip completed

### Rating System
- After completed request → passengers/driver rate each other
- Ratings affect visibility and trust score
- no_show_count incremented if passenger cancels < 30 mins

## Firestore Security Rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Anyone can read active requests
    match /ride_requests/{requestId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null && request.auth.uid == request.resource.data.riderId;
      allow update: if request.auth.uid == resource.data.riderId;
      allow delete: if false; // Never delete, only mark cancelled
    }
  }
}
```

## Future Enhancements

### Phase 2: Smart Matching
- [ ] ML-based driver matching (preferences, ratings, history)
- [ ] Passenger preferences (music, temperature, talking)
- [ ] Driver preferences (music, smoking, pet policy)
- [ ] Repeat trip suggestions

### Phase 3: Advanced Features
- [ ] Scheduled recurring requests (daily commutes)
- [ ] Budget negotiation (passenger proposes price)
- [ ] Split payment (multiple passengers share cost)
- [ ] Group requests (friends traveling together)

### Phase 4: Safety & Trust
- [ ] Verified ID badge
- [ ] Background check integration
- [ ] Trust score calculation
- [ ] In-app payments (escrow for cost)

---

**Last Updated**: 2026-07-23
**Status**: Ride request system complete with auto-matching and notifications

