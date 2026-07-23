# Trip Offers & Ride Hosting - Complete Guide

## Overview

Trip Offers are the core of SawaariShare - drivers post rides they're offering, and passengers can join. Offers include detailed routing, pricing, and passenger management.

## Firestore Collection: `trip_offers`

### Document Structure
```
trip_offers/{offerId}
├── id: String                          // Unique offer ID (offer_xxxxx)
├── hostId: String                      // Driver's user ID
├── hostName: String                    // Driver's display name ("John S.")
├── hostRating: Float                   // Driver's average rating (0.0-5.0)
├── origin: String                      // Pickup location ("NEU Snell Library")
├── destination: String                 // Dropoff location ("Logan Airport")
├── originLat: Double                   // Pickup latitude
├── originLng: Double                   // Pickup longitude
├── destLat: Double                     // Dropoff latitude
├── destLng: Double                     // Dropoff longitude
├── originGeohash: String               // Geohash for proximity search
├── destGeohash: String                 // Geohash for proximity search
├── departureTime: Long                 // Unix timestamp (milliseconds)
├── totalSeats: Int                     // Number of available seats (1-8)
├── seatsLeft: Int                      // Remaining available seats
├── vehicleInfo: String                 // "2020 Silver Toyota Camry" (driver info)
├── costPerRider: Double                // Cost per passenger ($)
├── womenOnly: Boolean                  // Women-only ride filter
├── status: String                      // "active" | "full" | "completed" | "cancelled"
├── routeSamplePoints: List<String>     // Optional waypoints/geohashes
├── costEstimate: Double                // Total cost (costPerRider × totalSeats)
├── passengers: List<String>            // Array of joined rider user IDs
├── passengerNames: List<String>        // Array of joined rider display names
└── created: Timestamp                  // Auto-generated creation time
```

## Trip Offer Lifecycle

### Stage 1: Creation (Driver Posts Ride)
```kotlin
suspend fun postTripOffer(offer: TripOffer): Result<Unit>
```

**Input:**
```kotlin
TripOffer(
    origin = "NEU Snell Library, Boston, MA",
    destination = "Logan Airport, Boston, MA",
    originLat = 42.3383,
    originLng = -71.0881,
    destLat = 42.3656,
    destLng = -71.0096,
    departureTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000), // 2 hours from now
    totalSeats = 4,
    costPerRider = 8.50,
    womenOnly = false,
    vehicleInfo = ""  // Auto-populated from user's vehicle
)
```

**Process:**
1. Validate all required fields
2. Generate unique offer ID (offer_xxxxx)
3. Calculate geohashes for location
4. Calculate cost estimate (costPerRider × totalSeats)
5. Save to local `_tripOffers` StateFlow
6. Persist to `trip_offers.json`
7. Sync to Firestore `trip_offers` collection
8. Send notifications to matching riders

**Validations:**
```
✓ Origin & destination not empty
✓ Coordinates valid (latitude -90 to 90, longitude -180 to 180)
✓ Departure time > current time (future only)
✓ Total seats 1-8
✓ Cost per rider >= 0
✓ User must be logged in and verified
```

**Notifications Sent:**
- Email alert to riders with matching requests (if enabled)
- Push notification to riders with matching requests (if enabled)

### Stage 2: Passengers Join
```kotlin
suspend fun joinTripOfferDirect(offerId: String): Result<Unit>
```

**Validations:**
```
✓ Trip status is "active"
✓ Seats available (seatsLeft > 0)
✓ User not already joined
✓ User is not the host
✓ User is logged in
```

**Process:**
1. Add user ID to `passengers` array
2. Add user display name to `passengerNames` array
3. Decrement `seatsLeft`
4. Update status to "full" if no seats left
5. Sync updated offer to Firestore
6. Send notification to driver (new passenger joined)

**Result:**
```kotlin
offer.passengers = ["user_123", "user_456"]          // User IDs
offer.passengerNames = ["John S.", "Maria G."]      // Display names
offer.seatsLeft = 2  // Was 4, now 2 after 2 joined
offer.status = if (seatsLeft == 0) "full" else "active"
```

### Stage 3: Manage Passengers
During the ride lifecycle, driver can:
- View passenger list
- Contact passengers (call/message)
- Mark passengers as no-show
- Track trip progress
- Accept additional riders if spots open

### Stage 4: Complete Trip
```kotlin
suspend fun updateTripOfferStatus(offerId: String, newStatus: String): Result<Unit>
```

**Status Transitions:**
```
active  → full        (when all seats taken)
active  → completed   (after ride finishes)
active  → cancelled   (driver cancels)
full    → completed   (after ride finishes)
```

**Rules:**
- Only driver (hostId) can update status
- Can transition backwards if needed (e.g., full → active if rider cancels)
- Once completed, passengers can rate driver

## Cost Splitting

### Cost Calculation
```kotlin
costPerRider: Double = 8.50              // Cost per passenger
totalSeats: Int = 4                      // Total available seats
costEstimate: Double = 8.50 × 4 = 34.00 // Total trip cost

// When passengers join:
actualCost = costPerRider × passengers.size
```

### Cost Capping (Safety)
```
Max contribution: 2× costPerRider

Example:
  costPerRider = $8.50
  maxContribution = $8.50 × 2 = $17.00
  
  Rider cannot contribute more than $17.00
  (protects from unfair pricing)
```

### Payment Flow (Future)
1. Driver posts ride with costPerRider
2. Passengers join and see total cost
3. Before/after ride: payment collected
4. SawaariShare takes commission (e.g., 5-10%)
5. Driver receives: costPerRider × passengers - commission

## Location & Routing

### Geohashing
Locations encoded as 7-character geohashes for proximity search:

```
NEU Snell Library (42.3383, -71.0881) → "drt2hkm"
Logan Airport (42.3656, -71.0096)     → "drt2jqy"
```

**Benefits:**
- Enables proximity-based searches (within 1 mile)
- Reduces number of Firestore queries
- Faster filtering on client

### Route Display
Optional `routeSamplePoints` array for:
- Showing waypoints on map
- Indicating if ride goes near user's location
- Route optimization visualization

### Location Sources
- User-selected locations (with autocomplete)
- GPS coordinates (if available)
- Pre-populated common locations (NEU, Logan, etc.)

## Women-Only Rides

### Feature
Driver can mark ride as women-only:
```kotlin
womenOnly = true
```

### Visibility
- Only shown to women who have women-only filter enabled
- Filter: User setting `isWomenOnlyFilterEnabled`

### Logic**
```kotlin
val showToUser = !offer.womenOnly || 
                 (currentUser.isWomenOnlyFilterEnabled == true)
```

**Note:** Honor system based on trust. Future: Enhanced verification options.

## Real-time Features

### Real-time Sync
When offer is updated (passengers join, status changes):
1. Update local `_tripOffers` StateFlow instantly
2. Async write to Firestore
3. If sync fails, logged and retried

### Listening for Updates
Current: Manual refresh via `fetchMyTripsFromFirestore()`

Future: Add Firestore snapshot listener for live updates
```kotlin
firebaseFirestore?.collection("trip_offers")
    ?.document(offerId)
    ?.addSnapshotListener { snapshot, error ->
        if (snapshot != null) {
            val updatedOffer = snapshot.toTripOfferSafe()
            // Update local cache instantly
        }
    }
```

## UI Screens

### 1. Create Offer Screen
**Inputs:**
- Origin location (autocomplete)
- Destination location (autocomplete)
- Departure date/time picker
- Number of seats (1-8)
- Cost per rider ($)
- Women-only toggle
- Vehicle info (auto-populated from profile)

**Actions:**
- [Post Ride] - Submit offer
- [Cancel] - Discard

### 2. My Hosted Rides Tab
**Shows:**
- List of rides driver is hosting
- Each card displays:
  - "John S. hosting: NEU → Logan"
  - 2/4 passengers joined
  - Departs in 45 mins
  - $8.50/person
  - Status badge: active/full/completed

**Actions:**
- [View Details] - See passengers, edit
- [Cancel Ride] - Cancel if no passengers joined
- [Message Passengers] - Contact riders

### 3. Ride Details Screen
**Driver View:**
- Origin & destination
- Departure time countdown
- Vehicle info
- Cost breakdown
- Passenger list with profiles
- Map preview of route
- [Contact Passenger] for each rider
- [Mark No-Show] button
- [Complete Trip] button

**Passenger View (when browsing):**
- Offer details
- Driver profile: "John S." ⭐4.5 (12 ratings)
- Vehicle info
- Route map
- [Join Ride] button
- [Message Driver] option

## Data Retrieval Methods

### Fetch Single Offer
```kotlin
fun getTripOfferById(offerId: String): TripOffer?
suspend fun fetchTripOfferFromFirestore(offerId: String): Result<TripOffer>
```

### Fetch User's Hosted Rides
```kotlin
suspend fun fetchMyTripsFromFirestore(): Result<Pair<List<TripOffer>, List<TripOffer>>>
// Returns: (hostedRides, joinedRides)

fun getHostedRides(userId: String): List<TripOffer>
```

### Fetch Active Rides (Feed)
```kotlin
fun getActiveRides(): List<TripOffer>
// Returns: All active rides sorted by departure time
```

Filtered by `updateFeeds()`:
- Excludes expired rides (departureTime < now)
- Excludes blocked users
- Applies women-only filter
- Sorts by driver rating (high first)

## Validation & Error Handling

### Pre-Creation Validation
```kotlin
fun validateTripOffer(offer: TripOffer): Result<Unit>
```

**Checks:**
```
✓ Origin not empty
✓ Destination not empty
✓ Departure time in future
✓ Total seats 1-8
✓ Cost >= 0
```

### Join Validation
```
✓ Seats available
✓ Not already joined
✓ Not your own ride
✓ User logged in
✓ Trip not completed/cancelled
```

### Status Update Validation
```
✓ Only driver can update
✓ Valid status transition
✓ Trip exists
```

## Error Messages

| Error | Cause | Fix |
|-------|-------|-----|
| "Please log in to post a ride." | Not authenticated | Login first |
| "Origin and destination are required." | Missing location | Enter both locations |
| "Departure time must be in the future." | Time in past | Select future time |
| "Total seats must be between 1 and 8." | Invalid seat count | Choose 1-8 seats |
| "Cost per rider cannot be negative." | Negative cost | Enter cost ≥ $0 |
| "This trip has no seats left!" | Ride full | Choose different ride |
| "You have already reserved a seat on this trip." | Already joined | Join different ride |
| "You cannot join your own Sawaari." | Can't join own ride | Only drivers/co-drivers join |
| "Only the host can modify this trip's status." | Wrong user | Host only can update |

## Testing Scenarios

### Scenario 1: Post & Join Ride
```
1. Driver logs in
2. Click "Create Ride"
3. Enter: NEU → Logan, 2 hrs from now, 4 seats, $8.50
4. [Post Ride] → Offer created with status "active"
5. Passenger logs in (different user)
6. See offer in Explore feed
7. [Join Ride]
8. Result: 
   - passengers array = [rider_user_id]
   - seatsLeft = 3
9. Driver sees 1/4 passengers joined
```

### Scenario 2: Fill Ride
```
1. Same ride, 3 more passengers join
2. After 4th join:
   - seatsLeft = 0
   - status = "full"
3. Ride disappears from "Available" feed
4. Still visible to joined passengers
5. New riders get "No seats available" error
```

### Scenario 3: Cancel Ride
```
1. Driver opens ride details
2. [Cancel Ride]
3. status = "cancelled"
4. Ride disappears from all feeds
5. Passengers notified: "Ride cancelled"
6. No refunds (payment system future)
```

### Scenario 4: Complete Trip
```
1. Ride time arrives
2. Driver [Start Trip]
3. en route: Driver can message/track
4. Driver [Complete Trip]
5. status = "completed"
6. Passengers prompted to rate driver
7. Driver sees completed ride in history
```

### Scenario 5: Women-Only Ride
```
1. Driver creates ride
2. Toggle [Women Only] = ON
3. Ride only visible to women with filter enabled
4. Passenger without filter won't see it
5. Passenger enables filter → sees women-only rides
```

## Performance Optimization

### Firestore Queries
```
Expensive (full collection scan):
  - Search by driver name
  - Search by cost range
  - Search by vehicle type

Optimized (indexed queries):
  - By status: "active"
  - By departureTime: > now
  - By geohash: proximity search
```

### Caching Strategy
- **Local Cache:** All offers stored in `_tripOffers` StateFlow
- **Refresh:** On app launch, every 30 seconds, on user action
- **Invalidation:** New offer posted, passenger joined, status changed

### Large Result Sets
If many active rides (thousands):
- Paginate by time window (next 7 days)
- Limit results to 50-100 per page
- Add geohash filtering for proximity

## Integration Points

### Matches (Booking)
When offer fully booked:
- Driver manually accepts passengers
- Creates `trip_matches` document
- Confirmed booking with messaging

### Notifications
- Driver rating updates → offer not visible to low-rated drivers
- New passenger joins → driver notified
- Rider cancels → seat reopens

### Rating System
- After completed trip → riders/driver rate each other
- Ratings affect visibility (low-rated drivers hidden)
- no_show_count affects trust score

## Firestore Security Rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Anyone can read active offers
    match /trip_offers/{offerId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null && request.auth.uid == request.resource.data.hostId;
      allow update: if request.auth.uid == resource.data.hostId;
      allow delete: if false; // Never delete, only mark cancelled
    }
  }
}
```

## Future Enhancements

### Phase 2: Advanced Routing
- [ ] Route optimization (best stops order)
- [ ] Real-time navigation sync
- [ ] ETA updates to passengers
- [ ] Detour capability (minor route changes)

### Phase 3: Smart Matching
- [ ] ML-based passenger matching (preferences, ratings)
- [ ] Preferred rider lists
- [ ] Repeat trip suggestions
- [ ] Route recommendation (AI)

### Phase 4: Advanced Features
- [ ] Scheduled recurring trips (weekly commutes)
- [ ] Trip groups (commute pods)
- [ ] Ride pooling optimization
- [ ] Carbon offset tracking
- [ ] Referral rewards

---

**Last Updated**: 2026-07-23
**Status**: Core trip offer system complete and enhanced
