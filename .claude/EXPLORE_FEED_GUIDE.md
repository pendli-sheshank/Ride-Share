# Explore Feed UI - Complete Guide

## Overview

The Explore Feed is the primary discovery screen where users browse and interact with trip offers and ride requests. It features a dual-mode interface (Rider/Host) that dynamically shows relevant content based on the user's current role.

## Architecture

### Screen Hierarchy
```
DashboardScreen
├── Tab Navigation (Explore / My Trips)
├── Mode Selector (Rider Mode / Host Mode) [Explore Only]
├── Explore Tab Content
│   ├── Recent Matches Section (collapsed/collapsible)
│   ├── "Trip Offers Near You" (Rider) / "Local Ride Requests" (Host)
│   ├── TripOfferList or RideRequestCards
│   └── Empty State (if no items)
└── Trips Tab Content
    ├── Hosted Split Cruisers
    ├── Joined Split Cruisers
    ├── My Ride Requests
    └── Past Rides & History
```

## Current Implementation

### Explore Tab (activeOffers & activeRequests)
```kotlin
val activeOffers by viewModel.activeOffers.collectAsState()    // Rider mode
val activeRequests by viewModel.activeRequests.collectAsState() // Host mode
```

**Rider Mode (activeMode == "Rider"):**
- Shows TripOfferList: All active driver-posted offers
- Data source: `_tripOffers` StateFlow filtered for:
  - status == "active"
  - hostId != currentUserId
  - departureTime > now
  - Not blocked users
  - Women-only filter applied

**Host Mode (activeMode == "Host"):**
- Shows RideRequestCards: All active passenger-posted requests
- Data source: `_rideRequests` StateFlow filtered for:
  - status == "active"
  - riderId != currentUserId
  - departureTime > now
  - Not blocked users

### Components

#### TripOfferList
```kotlin
@Composable
fun TripOfferList(
    offers: List<TripOffer>,
    currentUserId: String,
    userMatches: List<TripMatch>,
    viewModel: MainViewModel,
    navController: NavController,
    onJoinClick: (TripOffer) -> Unit
)
```

**Features:**
- Displays list of trip offers in LazyColumn
- Each card shows:
  - Host name + rating ("John S." ⭐4.5)
  - Origin → Destination route
  - Departure time/countdown
  - Seats available
  - Cost per rider
  - [Join] or [Already Joined] button

**State:**
- Checks if user already joined offer
- Shows appropriate CTA button
- Navigates to offer details on tap

#### RideRequestCard
```kotlin
@Composable
fun RideRequestCard(
    request: RideRequest,
    onCardClick: () -> Unit
)
```

**Features:**
- Displays single ride request
- Shows:
  - Rider name + rating
  - Origin → Destination
  - Seats needed
  - Departure time
  - Notes/preferences

## UI Screens

### 1. Explore Tab - Rider Mode (Browse Offers)

**Header:**
- Logo + "Namaste, {User}" + Verification badge
- Rating display: ⭐ {avg} ({count})
- Mode selector: Rider Mode [ACTIVE] | Host Mode

**Content Sections:**

**Section 1: Recent Matches (Collapsible)**
- Shows 2-3 most recent active matches
- Cards show driver/passenger name, route, status
- Quick action buttons: [Message] [Track]

**Section 2: Trip Offers Near You**
- Section title: "Trip Offers Near You"
- Refresh button (top right)
- List of offer cards

**Offer Card Components:**
```
┌─────────────────────────────────────────┐
│ ┌─────────────────────────────────────┐ │
│ │ Host Avatar  John S. ⭐4.5          │ │
│ │ Verified Northeastern University    │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ 📍 NEU Snell Library                     │
│  → Logan International Airport          │
│                                         │
│ 🕐 Departs: Today, 2:00 PM (45 mins)   │
│ 💺 2 of 4 seats left                    │
│ 🚗 2020 Silver Toyota Camry              │
│                                         │
│ 💵 $8.50 per person                     │
│                                         │
│ Notes: "Quiet rider, will play chill    │
│ music. Please be on time!"              │
│                                         │
│ ┌───────────────────────────────────────┤
│ │ [JOIN RIDE]  [VIEW DRIVER]            │
│ └───────────────────────────────────────┘
└─────────────────────────────────────────┘
```

**Offer Card States:**
- **Normal**: User can join
- **Already Joined**: Shows "Already Joined" + [View Match]
- **Full**: Shows "No Seats Available"
- **Expired**: Grayed out, hidden in filter

### 2. Explore Tab - Host Mode (Browse Requests)

**Header:**
- Logo + "Namaste, {User}"
- Mode selector: Rider Mode | Host Mode [ACTIVE]

**Content Sections:**

**Request Card Components:**
```
┌─────────────────────────────────────────┐
│ ┌─────────────────────────────────────┐ │
│ │ Rider Avatar  Maria G. ⭐4.2        │ │
│ │ Verified - 12 completed rides       │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ 📍 NEU Snell Library                     │
│  → Boston Logan Airport                 │
│                                         │
│ 🕐 Departs: Today, 1:30-3:30 PM        │
│ 💺 2 seats needed                       │
│                                         │
│ 💵 Budget: Up to $20                    │
│ 🎵 Preferences: Quiet rider             │
│                                         │
│ Notes: "Heading to airport, have        │
│ luggage. Will be ready early!"         │
│                                         │
│ ┌───────────────────────────────────────┤
│ │ [ACCEPT] [OFFER PRICE] [VIEW DETAILS] │
│ └───────────────────────────────────────┘
└─────────────────────────────────────────┘
```

### 3. My Trips Tab - Hosted Split Cruisers

Shows actively hosted ride offers with management controls:
```
┌─────────────────────────────────────────┐
│ HOSTED RIDE                          │
│                                         │
│ NEU → Boston Logan Airport              │
│ Today, 2:00 PM                          │
│ 2 of 4 passengers confirmed             │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ Passenger List:                     │ │
│ │ • John D. ⭐4.5 (confirmed)          │ │
│ │ • Maria G. ⭐4.2 (pending)           │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Status: ACTIVE • $34.00 total           │
│                                         │
│ [VIEW DETAILS] [MESSAGE PASSENGERS]    │
│ [START TRIP] [CANCEL]                  │
└─────────────────────────────────────────┘
```

### 4. My Trips Tab - Joined Split Cruisers

Shows actively joined ride offers:
```
┌─────────────────────────────────────────┐
│ JOINED RIDE                          │
│                                         │
│ John S. hosting: NEU → Logan            │
│ Today, 2:00 PM (45 mins away)           │
│                                         │
│ 2/4 seats taken                         │
│ 2020 Silver Toyota Camry                │
│ ⭐4.5 verified host                     │
│                                         │
│ Your cost: $8.50                        │
│ Pickup: NEU Snell Library               │
│ Dropoff: Terminal A                     │
│                                         │
│ [MESSAGE HOST] [VIEW DETAILS]          │
│ [CANCEL RESERVATION] [TRACK]           │
└─────────────────────────────────────────┘
```

## Card Components

### TripOfferCard
**Props:**
- `offer: TripOffer` - Offer data
- `currentUserId: String`
- `isAlreadyJoined: Boolean`
- `onJoinClick: () -> Unit`
- `onViewDetails: () -> Unit`
- `onViewDriver: () -> Unit`

**Behavior:**
- Tap card → Navigate to trip details
- [Join] → Call onJoinClick, show success dialog
- [View Driver] → Navigate to driver profile
- [Message] → Open chat (if already matched)

### RideRequestCard
**Props:**
- `request: RideRequest`
- `onCardClick: () -> Unit`
- `onAcceptClick: (RideRequest) -> Unit` (Host mode)

**Behavior:**
- Tap card → Navigate to request details
- [Accept] → Create match, open chat
- [View Rider] → Navigate to rider profile

### MatchCard (Recent Matches Section)
**Props:**
- `match: TripMatch`
- `offer: TripOffer?`
- `request: RideRequest?`
- `onMessageClick: () -> Unit`
- `onTrackClick: () -> Unit`

**Shows:**
- Counter-party (driver/rider) name + avatar + rating
- Route (simplified)
- Time until departure
- Status badge: pending/accepted/completed

## UI Patterns

### Loading States
```kotlin
if (isLoading && activeOffers.isEmpty()) {
    SplitCruiserFeedLoadingSkeleton()
}
```

Shows shimmer skeleton cards while data loads.

### Empty States
```kotlin
@Composable
fun SplitCruiserEmptyState(
    title: String,
    description: String,
    icon: androidx.compose.material.icons.Icons,
    actionLabel: String = "",
    onActionClick: () -> Unit = {},
    illustrationType: String = ""
)
```

Shows:
- Illustration/Icon
- Title + description
- Optional [Action] button

**Example:**
- Rider: "No Active Offers Yet" → [Post Ride Request]
- Host: "No Open Requests" → [Post Trip Offer]

### Filter & Sort

**Current Implementation:**
- Filter applied in repository (updateFeeds)
- Client-side filtering by:
  - Status (active only)
  - User ID (exclude own)
  - Expiration time
  - Blocked users
  - Women-only filter

**Future Enhancements:**
- Sort options: time, rating, price, distance
- Filter chips: price range, seats, time window
- Search by location
- Save favorite routes

### Mode Switching

```kotlin
// In header when selectedTab != "trips"
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFFE1E2EC))
        .padding(4.dp)
) {
    listOf("Rider", "Host").forEach { mode ->
        val active = (activeMode == mode)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) Color.White else Color.Transparent)
                .clickable { viewModel.switchMode(mode) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (mode == "Rider") "Rider Mode (Find Ride)" else "Host Mode (Give Ride)",
                color = if (active) SplitCruiserTextPrimary else Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
```

## Interactive Features

### Join Ride (Rider Mode)
1. User taps [JOIN] on offer card
2. Show bottom sheet with confirmation:
   - Route summary
   - Cost breakdown
   - Driver info
   - [CONFIRM] [CANCEL]
3. On confirm:
   - Create match (pending status)
   - Open chat with driver
   - Show "Waiting for driver to confirm"
   - Button changes to "Awaiting Confirmation"

### Accept Request (Host Mode)
1. User taps [ACCEPT] on request card
2. Show bottom sheet with:
   - Route summary
   - Rider info + rating
   - Proposed cost
   - [CONFIRM] [CANCEL]
3. On confirm:
   - Create match
   - Open chat with rider
   - Rider notified
   - Status: "Pending Rider Confirmation"

### Message from Card
- Quick action [Message] on match cards
- Opens chat immediately
- Auto-navigates to chat screen

### View Details
- Full screen with:
  - Complete route info
  - Driver/rider full profile
  - Passenger list (if applicable)
  - Message history (if matched)
  - Action buttons

## Performance Considerations

### LazyColumn Efficiency
```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp)
) {
    item { /* header */ }
    
    if (offers.isEmpty()) {
        item { /* empty state */ }
    } else {
        items(offers) { offer ->
            TripOfferList(...)
        }
    }
}
```

**Optimizations:**
- Lazy rendering (only visible items)
- `key` lambda for list stability
- Compose recomposition optimization

### State Management
```kotlin
val activeOffers by viewModel.activeOffers.collectAsState()
val activeRequests by viewModel.activeRequests.collectAsState()
val userMatches by viewModel.userMatches.collectAsState()
```

**Flow:**
- Repository emits StateFlow updates
- Compose automatically recomposes
- Only affected composables update
- No unnecessary re-renders

## Accessibility

### Color Contrast
- Text: 4.5:1 minimum (WCAG AA)
- Icons: High contrast with background

### Touch Targets
- Minimum 48dp × 48dp for buttons
- Cards have 16dp padding

### Semantic Labels
```kotlin
Icon(
    imageVector = Icons.Default.Person,
    contentDescription = "Driver Profile"
)
```

## Theming

### Color Palette
- **Primary:** SplitCruiserPrimary (0xFF0061A4) — blue
- **Secondary:** SplitCruiserPrimaryContainer (0xFFD1E4FF) — pale blue
- **Success:** SplitCruiserSuccess (0xFF10B981) — green
- **Background:** SplitCruiserSurface (0xFFF8F9FF) — near-white, the app background
- **Card:** SplitCruiserSurfaceCard (0xFFFFFFFF) — white

### Typography
- **Headline:** 20sp, Black weight
- **Title:** 16sp, Bold weight
- **Body:** 14sp, Regular weight
- **Caption:** 11sp, Regular weight

### Spacing
- Padding: 16dp, 12dp, 8dp, 4dp
- Gap: 8dp standard between elements

## Testing Scenarios

### Scenario 1: Browse Trip Offers (Rider)
```
1. User in Rider Mode
2. See list of 5+ active trip offers
3. Each card shows:
   - Driver name + avatar + rating
   - Origin → Destination route
   - Departure time countdown
   - Seats/cost info
4. Tap [JOIN] on offer
5. Show join confirmation dialog
6. [CONFIRM] → Match created
7. Match added to "Recent Matches"
8. Button changes to "Already Joined"
```

### Scenario 2: Browse Ride Requests (Host)
```
1. User in Host Mode
2. See list of 3+ active ride requests
3. Each card shows:
   - Rider name + avatar + rating
   - Origin → Destination
   - Time window + seats needed
   - Rider notes
4. Tap [ACCEPT] on request
5. Show accept confirmation
6. [CONFIRM] → Match created
7. Chat opens
8. Rider receives notification
```

### Scenario 3: Recent Match Card
```
1. User has active match
2. "Recent Matches" section shows at top
3. Card shows counter-party info
4. [MESSAGE] → Open chat
5. [TRACK] → Show live location (future)
```

### Scenario 4: Switch Modes
```
1. User in Rider Mode viewing offers
2. Tap "Host Mode" selector
3. Content switches to ride requests
4. Mode toggles instantly
5. Offers disappear, requests appear
6. "Local Ride Requests" section visible
```

### Scenario 5: Empty State
```
1. No offers available (Rider)
2. Show empty state illustration
3. "No Active Offers Yet"
4. Description text
5. [POST RIDE REQUEST] button
6. Tap → Navigate to post request screen
```

## Future Enhancements

### Phase 2: Advanced Filtering
- [ ] Sort by: distance, rating, price, time
- [ ] Filter chips: price range, seats, time window
- [ ] Search by location/route
- [ ] Save favorite routes

### Phase 3: Rich Media
- [ ] Driver/passenger photos
- [ ] Vehicle photos
- [ ] Route visualization on map
- [ ] Real-time location during trip

### Phase 4: Smart Matching
- [ ] Recommended matches (ML)
- [ ] Ride ratings preview
- [ ] Preferred riders/drivers list
- [ ] Auto-accept low-risk matches

### Phase 5: Social Features
- [ ] Share ride with friends
- [ ] Create ride groups
- [ ] Community highlights
- [ ] Leaderboards (eco-friendly, reliable, etc.)

## Related Screens

- **Login Screen** (`login`) - Authentication
- **Profile Setup** (`profile_setup`) - Onboarding
- **Post Offer** (`post_offer`) - Create new ride
- **Post Request** (`post_request`) - Request new ride
- **Trip Detail** (`trip_detail/{id}/{type}`) - Full ride info
- **Chat** (`chat/{matchId}`) - Message thread
- **Profile** (`profile`) - User profile view
- **Blocked List** (`blocked_list`) - Manage blocked users

---

**Last Updated**: 2026-07-23
**Status**: Explore Feed UI architecture documented, cards implemented, ready for enhancement

