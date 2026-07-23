# Ride Details & Coordination - Complete Guide

## Overview

The Ride Details screen is the comprehensive view where drivers and passengers manage trip information, coordinate logistics, and maintain communication. It supports two modes: **Trip Offer Details** (driver-side) and **Ride Request Details** (passenger-side).

## Architecture

### Screen Hierarchy

```
TripDetailScreen
├── Type: "offer" (Driver Posted Ride)
│   ├── Trip Route Card
│   │   ├── Origin (Pickup Point)
│   │   └── Destination (Dropoff Point)
│   ├── Route Visualization (Google Maps Matrix)
│   ├── Status & Seats Card
│   ├── Host Details Card (Expandable)
│   │   ├── Avatar + Name + Rating
│   │   ├── Vehicle Info
│   │   ├── Contact Details
│   │   └── Verification Badge
│   ├── Cost Allocation Card
│   ├── Reserved Passengers List
│   ├── Host Controls (if host is current user)
│   │   ├── Set Active / Set Full
│   │   ├── Complete / Cancel
│   │   └── Message Passengers
│   └── Passenger Actions (if joining)
│       ├── Direct Join Button
│       └── Propose Custom Contribution
│
└── Type: "request" (Passenger Posted Request)
    ├── Trip Route Card
    │   ├── Origin (Rider Pickup)
    │   └── Destination (Rider Dropoff)
    ├── Route Visualization
    ├── Request Details Card
    │   ├── Seats Needed
    │   ├── Budget Range
    │   ├── Time Window
    │   └── Preferences
    ├── Rider Profile Card
    │   ├── Avatar + Name + Rating
    │   ├── Verification Badge
    │   ├── Number of Completed Rides
    │   └── Communication Info
    └── Host Actions
        ├── Accept Request
        ├── Decline Request
        ├── Propose Price
        └── Message Rider
```

## Components

### 1. Trip Route Card

```kotlin
@Composable
fun RouteCard(
    origin: String,
    destination: String,
    departureTime: String? = null,
    modifier: Modifier = Modifier
)
```

**Features:**
- Vertical origin → destination visualization
- Pickup/dropoff icons with connecting line
- Clean layout with proper spacing
- Works for both offers and requests

**Example:**
```
📍 NEU Snell Library
  ↓
🎯 Boston Logan Airport
```

### 2. Google Maps Matrix Card

```kotlin
@Composable
fun GoogleMapsMatrixCard(
    origin: String,
    destination: String,
    modifier: Modifier = Modifier
)
```

**Features:**
- Shows estimated distance and duration
- Powered by Google Maps API
- Displays route overview
- Fallback to static text if API unavailable

### 3. Host/Driver Details Card

```kotlin
@Composable
fun DriverDetailCard(
    driver: User,
    rating: Float,
    vehicle: Vehicle?,
    onMessageClick: () -> Unit,
    onCallClick: () -> Unit,
    onBlockClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Shows:**
- Avatar + Display name
- Rating badge (⭐)
- Verification status (Vouched/Guest)
- University affiliation
- Vehicle info (make, model, color, plate)
- Contact methods (message, call)
- Block option

**States:**
- **Collapsed:** Name + Rating + University
- **Expanded:** Full details + contact card
- **Blocked:** Dimmed state with block notice

### 4. Status & Seats Card

```kotlin
@Composable
fun StatusSeatsCard(
    status: String,        // "active", "full", "completed", "cancelled"
    departureTime: Long,
    seatsTotal: Int,
    seatsOccupied: Int,
    modifier: Modifier = Modifier
)
```

**Displays:**
- Current trip status with color badge
- Departure date/time with countdown
- Seat availability (X of Y taken)
- Estimated duration

### 5. Cost Allocation Card

```kotlin
@Composable
fun CostAllocationCard(
    costPerRider: Double,
    totalCost: Double,
    costCap: Double,
    modifier: Modifier = Modifier
)
```

**Shows:**
- Suggested gas contribution
- Total cost calculation
- Safety cap (2× cost limit)
- Info: "Cash paid in-person"

**Example:**
```
Suggested Contribution: $8.50
Total for 4 riders: $34.00
Safety Cap (2x): $17.00
```

### 6. Reserved Passengers Card

```kotlin
@Composable
fun PassengerListCard(
    passengers: List<Passenger>,
    currentUserId: String,
    onPassengerClick: (String) -> Unit,
    modifier: Modifier = Modifier
)
```

**Shows:**
- List of joined passengers
- Name + Status (confirmed/pending)
- Highlight current user
- Avatar + rating

## UI Screens

### Trip Offer Details (Rider/Passenger View)

**Header:**
```
← Trip Offer Details
```

**Section 1: Route**
```
📍 NEU Snell Library
  ↓
🎯 Boston Logan Airport

[Google Maps Preview with distance/time]
```

**Section 2: Trip Information**
```
STATUS & SEATS
Status: ACTIVE ✓
Departure: Today, 2:00 PM (45 mins away)
Available: 2 of 4 seats left
```

**Section 3: Host Profile**
```
┌─────────────────────────────┐
│ 👤 John S. ⭐4.5           │
│ Verified • Northeastern     │
│ [⌄ Show Details]            │
│                             │
│ [When Expanded]             │
│ Vehicle: 2020 Silver Camry  │
│ Plate: STU-1829            │
│ Phone: (617) 555-0123      │
│ [View Full Driver Card]     │
└─────────────────────────────┘
```

**Section 4: Cost Breakdown**
```
COST ALLOCATION
Suggested Contribution: $8.50
Total for 4 riders: $34.00
Safety Cap: $17.00
💡 Cash paid in-person, no app fees
```

**Section 5: Passengers**
```
RESERVED PASSENGERS
✓ John D. (confirmed)
⧗ Maria G. (pending)
👤 You
```

**Section 6: Actions**

If already joined:
```
✓ Seat Reserved
You have successfully joined.
Coordinate with John S.
[MESSAGE DRIVER]
```

If can join:
```
[JOIN SAWAARI - RESERVE SEAT] (Large button, blue)

OR PROPOSE CUSTOM CONTRIBUTION:
[Input field: $] [PROPOSE]
```

If full/completed/cancelled:
```
[SAWAARI IS FULL] (Disabled gray button)
```

### Ride Request Details (Host/Driver View)

**Header:**
```
← Ride Request Details
```

**Section 1: Route**
```
📍 NEU Snell Library
  ↓
🎯 Boston Logan Airport

[Google Maps Preview]
```

**Section 2: Request Details**
```
REQUEST INFORMATION
Seats Needed: 2
Budget: Up to $20
Time Window: Today, 1:30 PM - 3:30 PM
Preferences: Quiet rider, no music
```

**Section 3: Rider Profile**
```
┌─────────────────────────────┐
│ 👤 Maria G. ⭐4.2          │
│ Verified • 12 Rides        │
│ Northeastern University    │
│ [⌄ Show Details]            │
│                             │
│ [When Expanded]             │
│ Reviews: Reliable, on-time │
│ No-show rate: 0%           │
│ [MESSAGE RIDER]             │
│ [CALL RIDER]                │
└─────────────────────────────┘
```

**Section 4: Actions**

If no match yet:
```
[ACCEPT & OFFER SAWAARI SHARE] (Large button, blue)
[PROPOSE PRICE] (Secondary button)
[DECLINE REQUEST]
```

If match pending:
```
⧗ Request is Pending Your Approval
[DECLINE] [ACCEPT & CHAT]
```

If match accepted:
```
✓ You Accepted This Request
[OPEN COORDINATOR CHAT]
[MARK AS PICKED UP] (When trip starts)
[MARK AS COMPLETED]
```

## Interaction Flows

### Flow 1: Passenger Joining Offer

```
1. Passenger browses Explore Feed
2. Taps offer card → Navigate to trip_detail/{offerId}/offer
3. Views full trip details
4. Sees: Route, Driver, Passengers, Cost
5. [JOIN SAWAARI] → Creates match in pending status
6. Driver gets notification
7. Status shows: ⧗ Waiting for Host Confirmation
8. Driver [ACCEPT] → Match accepted
9. Passenger can now [MESSAGE HOST] or [VIEW DETAILS]
10. Day of trip: [MARK AS PICKED UP]
11. End of trip: Driver [COMPLETE TRIP]
12. Both can rate each other
```

### Flow 2: Driver Accepting Request

```
1. Driver browses requests in Host Mode
2. Taps request card → Navigate to trip_detail/{requestId}/request
3. Views full request details
4. Sees: Route, Rider, Budget, Preferences
5. [ACCEPT & OFFER] → Creates match in accepted status
6. Chat opens automatically
7. Coordinate pickup location and time
8. Day of trip:
   - Driver confirms pickup
   - Rider gets notification
   - Status: DRIVING
9. End of trip: [MARK AS COMPLETED]
10. Rating exchange
```

### Flow 3: Trip Coordination

```
During Trip:
├── Pre-Trip (T-0 to 15 mins before)
│   ├── Confirm pickup location
│   ├── Exchange contact details
│   ├── Estimate arrival time
│   └── Final communication
│
├── Pickup Phase
│   ├── Driver calls passenger
│   ├── Passenger confirms location
│   ├── Driver: "I'm here" message
│   ├── Passenger boards
│   └── [MARK AS PICKED UP]
│
├── Enroute
│   ├── Driver navigates
│   ├── Passenger adjusts if needed
│   ├── Estimate time updates
│   └── Communication as needed
│
└── Dropoff
    ├── Driver: "Arriving now" message
    ├── Passenger exits
    ├── Cost settlement (cash)
    └── [MARK AS COMPLETED]
```

## Data Models

### Trip Offer (for "offer" type)
```kotlin
data class TripOffer(
    val id: String,
    val hostId: String,
    val hostName: String,
    val hostRating: Float,
    val origin: String,
    val destination: String,
    val departureTime: Long,
    val totalSeats: Int,
    val seatsLeft: Int,
    val costPerRider: Double,
    val status: String,  // "active", "full", "completed", "cancelled"
    val passengers: List<String>,
    val passengerNames: List<String>,
    val vehicleInfo: String,
    val notes: String,
    val womenOnly: Boolean,
    val createdAt: Long
)
```

### Ride Request (for "request" type)
```kotlin
data class RideRequest(
    val id: String,
    val riderId: String,
    val riderName: String,
    val riderRating: Float,
    val origin: String,
    val destination: String,
    val departureTime: Long,
    val seatsNeeded: Int,
    val maxBudget: Double,
    val status: String,  // "active", "matched", "completed", "cancelled"
    val preferences: String,
    val notes: String,
    val createdAt: Long
)
```

### Trip Match
```kotlin
data class TripMatch(
    val id: String,
    val offerId: String,
    val requestId: String,
    val hostId: String,
    val riderId: String,
    val status: String,  // "pending", "accepted", "completed", "cancelled"
    val agreedCost: Double,
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val completedAt: Long? = null
)
```

## Status Badges

### Offer Status
```
ACTIVE      → Blue badge, green checkmark
FULL        → Amber badge, warning icon
COMPLETED   → Green badge, check icon
CANCELLED   → Red badge, X icon
```

### Match Status
```
PENDING     → Yellow badge, hourglass icon
ACCEPTED    → Green badge, check icon
COMPLETED   → Green badge with checkmark
CANCELLED   → Red badge with X
```

## Message Integration

### Show Recent Messages
```kotlin
val recentMessages = matches
    .find { it.id == matchId }
    ?.let { match -> repository.getMatchConversation(match.id) }
    ?.takeLast(3)  // Last 3 messages
```

**Display in Coordination Card:**
```
Recent Messages:
"Hi! I'm heading out now" - Driver
"Perfect, I'm ready!" - Passenger
"See you in 10 mins" - Driver
```

### Message Context
- Show last 3 messages inline
- [VIEW FULL CHAT] button to navigate to ChatScreen
- Real-time updates via snapshot listener

## Navigation

### Routes
```kotlin
// View trip offer (rider viewing driver's ride)
trip_detail/{offerId}/offer

// View ride request (driver viewing passenger's request)
trip_detail/{requestId}/request

// From detail screen, tap on driver/passenger to view profile
profile/{userId}

// From detail screen, [MESSAGE] button
chat/{matchId}

// List of joined trips
dashboard (My Trips tab)

// Host's dashboard
host_dashboard
```

### Back Navigation
- Always show back arrow (←) in header
- Back arrow returns to previous screen (Explore Feed or Dashboard)
- No unsaved data warning (changes sync to Firestore automatically)

## Material 3 Design

### Colors
- **Primary Action:** SawaariSaffron (#0061A4) - Blue
- **Success:** SawaariEmerald (#10B981) - Green  
- **Warning:** Amber (#D97706) - Orange
- **Danger:** Red (#DC2626) - Red
- **Divider:** SawaariDivider (#E2E8F0) - Light Gray
- **Text:** SawaariTextPrimary (#0F172A) - Dark

### Typography
- **Header:** 20sp Bold
- **Section Title:** 11sp Bold, all-caps
- **Card Title:** 15sp Bold
- **Body Text:** 13-14sp Regular
- **Caption:** 11-12sp Regular

### Spacing
- **Card Padding:** 16-20dp
- **Section Gap:** 16-24dp
- **Element Gap:** 8-12dp
- **Border Radius:** 12-16dp

## Accessibility

### Color Contrast
- ✅ Text on background: 4.5:1 (WCAG AA)
- ✅ Icons have high contrast
- ✅ Interactive elements clearly defined

### Touch Targets
- ✅ Buttons: 44-54dp minimum height
- ✅ Cards: Clickable area > 48dp
- ✅ Icon buttons: 40-48dp

### Semantic Labels
```kotlin
Icon(
    imageVector = Icons.Default.DirectionsCar,
    contentDescription = "Vehicle information"
)
Button(
    onClick = { /* */ },
    // semantics { contentDescription = "Join this sawaari" }
) { /* */ }
```

## Performance Optimization

### LazyColumn Structure
```kotlin
LazyColumn {
    item { /* Header + Back button */ }
    item { /* Route card */ }
    item { /* Maps card */ }
    item { /* Status card */ }
    item { /* Driver/Rider card (expandable) */ }
    item { /* Cost card */ }
    item { /* Passengers/Details list */ }
    item { /* Action buttons */ }
}
```

### State Management
```kotlin
// Fetch offer/request once on screen load
val offer = offers.find { it.id == id }

// Fetch user profile separately with caching
val hostUser = remember(offer.hostId) { 
    viewModel.getUserPublicProfile(offer.hostId) 
}

// Fetch vehicle info separately
val hostVehicle = remember(offer.hostId) { 
    viewModel.getVehicleInfo(offer.hostId) 
}

// Track match status for real-time updates
val existingMatch = remember(matches, offer.id) {
    matches.find { it.offerId == offer.id && it.riderId == currentUser?.id }
}
```

### Avoid Recomposition
- Use `remember()` for expensive lookups
- Use `remember(key1, key2)` for dependencies
- Separate state per card to localize recomposition

## Testing Scenarios

### Scenario 1: Browse and Join Offer (Rider)
```
1. Passenger navigates to Explore Feed (Rider Mode)
2. Taps offer: "NEU → Logan, $8.50, 2 seats"
3. Views TripDetailScreen for offer
4. Sees:
   - Route visualization
   - Host: John S. ⭐4.5
   - Cost: $8.50 (under 2x cap of $17)
   - Vehicle: 2020 Silver Camry
   - Passengers: 1 confirmed
5. [JOIN SAWAARI] → Match created (pending)
6. Success dialog shows
7. Passenger taken to dashboard
8. Match appears in "Recent Matches"
9. Status: "Waiting for driver confirmation"
```

### Scenario 2: Accept Request (Host/Driver)
```
1. Driver navigates to Explore Feed (Host Mode)
2. Taps request: "NEU → Logan, 2 seats, $20 budget"
3. Views TripDetailScreen for request
4. Sees:
   - Route visualization
   - Rider: Maria G. ⭐4.2
   - Budget: $20 (accept at $8.50)
   - Seats: 2 needed
   - Preferences: "Quiet, on-time"
5. [ACCEPT & OFFER] → Match created (accepted)
6. Chat opens
7. Driver messages: "Pickup at 2:00 PM, Snell Library"
8. Rider responds: "Perfect, I'll be ready"
9. Driver [MARK AS PICKED UP] when passenger boards
10. [MARK AS COMPLETED] at dropoff
```

### Scenario 3: View Accepted Match Details
```
1. Passenger opens accepted match from dashboard
2. Views trip details
3. Sees host accepted confirmation
4. [MESSAGE HOST] or [VIEW DETAILS] options
5. Can see:
   - Real-time location (if enabled)
   - Driver arrival status
   - Contact information
6. When host arrives: "Driver is here" notification
7. Boards vehicle and [MARK AS PICKED UP]
```

### Scenario 4: Cancel Trip from Details
```
1. Host viewing accepted trip
2. Situation changes (car problem, etc.)
3. [CANCEL TRIP]
4. Confirmation dialog: "Reason for cancellation?"
5. Enter: "Engine problem"
6. [CONFIRM CANCEL]
7. Match status → cancelled
8. Passengers notified
9. No revenue recorded
10. Back to dashboard
```

### Scenario 5: View Driver Contact Card
```
1. Passenger opens trip details
2. Taps [View Full Driver & Contact Card]
3. Modal shows:
   - Large avatar
   - Full name (privacy-compliant)
   - Rating with breakdown
   - Vehicle details (make, model, color, plate)
   - Phone number (masked or visible based on match status)
   - Email
   - Verification status
4. [CALL DRIVER] → Opens phone dialer
5. [MESSAGE DRIVER] → Opens chat
6. [BLOCK DRIVER] → Block user
7. Close modal [X]
```

## Future Enhancements

### Phase 2: Real-time Coordination
- [ ] Live location tracking during trip
- [ ] Real-time ETA updates
- [ ] Pickup point confirmation
- [ ] Push notifications for driver arrival

### Phase 3: Enhanced Profiles
- [ ] Driver/passenger reviews inline
- [ ] Vehicle photos
- [ ] Background check status
- [ ] Verification badges (ID verified, etc.)

### Phase 4: Advanced Coordination
- [ ] Recurring trips
- [ ] Round-trip support
- [ ] Multiple stops
- [ ] Group ride management

### Phase 5: Payment Integration
- [ ] In-app payment (future)
- [ ] Venmo/PayPal links
- [ ] Payment history
- [ ] Auto-settlement suggestions

## API Methods Used

```kotlin
// Offer-related
viewModel.activeOffers.collectAsState()
viewModel.getUserPublicProfile(userId)
viewModel.getVehicleInfo(userId)
viewModel.joinTripOfferDirect(offerId, callback)
viewModel.requestJoin(offerId, requestId, cost, callback)
viewModel.updateTripOfferStatus(offerId, status, callback)
viewModel.blockUser(userId, callback)

// Request-related
viewModel.activeRequests.collectAsState()
viewModel.acceptMatch(matchId)
viewModel.declineMatch(matchId)

// Messaging
viewModel.userMatches.collectAsState()
// Navigate to chat/{matchId}
```

## Related Screens

- **Explore Feed** (`dashboard`) - Browse offers/requests
- **Host Dashboard** (`host_dashboard`) - Manage hosted trips
- **Chat** (`chat/{matchId}`) - Real-time messaging
- **User Profile** (`profile`) - View user profile
- **Dashboard** (`dashboard`) - My Trips tab

---

**Last Updated**: 2026-07-23
**Status**: Ride Details & Coordination UI comprehensive, ready for message history integration and pickup confirmation features

