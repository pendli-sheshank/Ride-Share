# Host Dashboard UI - Complete Guide

## Overview

The Host Dashboard is where drivers manage their hosted trips, monitor passengers, and track ride performance. It provides comprehensive analytics, passenger management, and trip coordination tools.

## Architecture

### Screen Hierarchy
```
HostDashboard (Main Screen)
├── Top Bar (title, back button)
├── Statistics Overview
│   ├── Active Rides card
│   ├── Total Passengers card
│   └── Revenue card
├── Filter Chips
│   ├── All Rides
│   ├── Active
│   ├── Completed
│   └── Cancelled
└── Hosted Rides List
    ├── HostedRideScheduleCard
    ├── PassengerManagementCard (expanded)
    └── Trip Actions
```

## Components

### 1. HostDashboard (Main Screen)

```kotlin
@Composable
fun HostDashboard(viewModel: MainViewModel, navController: NavController)
```

**Features:**
- Real-time statistics (active rides, passengers, revenue)
- Filter by ride status (all, active, completed, cancelled)
- List of hosted rides with management options
- Navigation to detailed trip views

**Data Sources:**
```kotlin
val hostedRides by viewModel.hostedRides.collectAsState()
val currentUser by viewModel.currentUser.collectAsState()

val activeRides = hostedRides.filter { it.status == "active" }
val totalPassengers = hostedRides.sumOf { it.passengers.size }
val totalRevenue = hostedRides.sumOf { it.costPerRider * (it.totalSeats - it.seatsLeft) }
```

**State Management:**
```kotlin
var filterStatus by remember { mutableStateOf("all") }

val filteredRides = remember(hostedRides, filterStatus) {
    when (filterStatus) {
        "active" -> hostedRides.filter { it.status == "active" }
        "completed" -> hostedRides.filter { it.status == "completed" }
        "cancelled" -> hostedRides.filter { it.status == "cancelled" }
        else -> hostedRides
    }.sortedByDescending { it.departureTime }
}
```

### 2. HostStatCard (Statistics Display)

```kotlin
@Composable
fun HostStatCard(
    label: String,
    value: String,
    icon: Icons,
    modifier: Modifier = Modifier
)
```

**Displays:**
- Icon + Value + Label
- Card with subtle border
- Used for: Active Rides, Total Passengers, Revenue

**Example:**
```
┌──────────────────┐
│   🚗             │
│   5              │
│   Active Rides   │
└──────────────────┘
```

### 3. PassengerManagementCard (Passenger Details)

```kotlin
@Composable
fun PassengerManagementCard(
    passengerName: String,
    passengerRating: Float,
    passengerId: String,
    offerRoute: String,
    onMessageClick: () -> Unit,
    onMarkNoShowClick: () -> Unit,
    onViewProfileClick: () -> Unit
)
```

**Shows:**
- Passenger avatar + name
- Passenger rating (⭐)
- Two action buttons:
  - [Message] - Open chat
  - [No Show] - Mark as no-show

**Example:**
```
┌─────────────────────────────────┐
│ 👤 Maria G.     ⭐ 4.2          │
│                                 │
│ [💬 Message] [⚠️ No Show]      │
└─────────────────────────────────┘
```

### 4. HostedRideScheduleCard (Enhanced)

**Existing features:**
- Route visualization (origin → destination)
- Departure time
- Seats occupied / total
- Passenger list (name badges)
- Status badge
- Complete/Cancel buttons

**Enhanced features (in this update):**
- Expanded passenger management
- Quick message action
- No-show tracking
- Passenger rating display

## UI Screens

### Main Host Dashboard Screen

**Header:**
- Title: "Host Dashboard"
- Subtitle: "Manage your hosted rides"
- Back button

**Section 1: Statistics Overview**
```
┌─────────────────────────────────────────┐
│  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
│  │   🚗    │  │   👥    │  │   💵    │ │
│  │    5    │  │   12    │  │ $127.50 │ │
│  │ Active  │  │ Total   │  │ Revenue │ │
│  │ Rides   │  │Passengers│ │        │ │
│  └─────────┘  └─────────┘  └─────────┘ │
└─────────────────────────────────────────┘
```

**Statistics Calculations:**
```kotlin
activeRides = hostedRides.filter { it.status == "active" }.size
totalPassengers = hostedRides.sumOf { it.passengers.size }
totalRevenue = hostedRides.sumOf { it.costPerRider * (it.totalSeats - it.seatsLeft) }
```

**Section 2: Filter Chips**
```
[All Rides] [Active] [Completed] [Cancelled]
```

**Section 3: Hosted Rides List**
```
For each offer in filteredRides:
  ┌──────────────────────────────────┐
  │ HOSTED RIDE    [ACTIVE]       │
  │                                  │
  │ 📍 NEU Snell Library             │
  │  → Boston Logan Airport          │
  │                                  │
  │ 🕐 Today, 2:00 PM               │
  │ 💺 2 of 4 seats occupied        │
  │                                  │
  │ Passengers:                      │
  │ [John D.] [Maria G.]            │
  │                                  │
  │ [Complete Ride] [Cancel Ride]   │
  └──────────────────────────────────┘
```

## Detailed Trip Management

### Ride Details View (Expanded)

When user taps on a hosted ride:
```
Navigate to: trip_detail/{offerId}/offer
Shows:
├── Full Trip Information
│   ├── Route (origin → destination)
│   ├── Date/Time
│   ├── Vehicle info
│   ├── Total cost
│   └── Passenger limit
├── Passenger Management Section
│   ├── List of passengers with:
│   │   ├── Avatar + Name
│   │   ├── Rating
│   │   ├── [Message] button
│   │   ├── [Call] button
│   │   └── [No Show] button
│   └── Add Passenger (if seats available)
├── Chat Integration
│   └── Recent messages from passengers
└── Trip Actions
    ├── [Start Trip] - Begin ride
    ├── [Complete Trip] - End ride
    ├── [Cancel Trip] - Cancel ride
    └── [Edit] - Modify details (if not started)
```

### Passenger Management Actions

#### 1. Message Passenger
- Click [Message] on passenger card
- Opens ChatScreen with match ID
- Pre-fills conversation for this ride

#### 2. Mark No-Show
- Click [No Show] on passenger card
- Show confirmation dialog
- Record no-show in passenger profile
- Increment noShowCount
- Potentially revert seat (if payment system exists)
- Send notification to passenger

#### 3. Call Passenger
- Click [Call] on passenger card
- Open phone dialer (if phone available)
- Future: In-app calling integration

#### 4. View Passenger Profile
- Click passenger name/avatar
- Navigate to passenger's public profile
- Show: name, rating, verification, bio, past rides
- Option to block user if issues

## Analytics & Insights

### Host Performance Metrics

**Displayed on Dashboard:**
```
├── Active Rides (count)
├── Total Passengers (sum)
├── Total Revenue ($)
├── Completion Rate (%)
├── Average Rating (⭐)
├── No-Show Rate (%)
└── Repeat Passengers (count)
```

**Calculations (Future Enhancement):**
```kotlin
val completionRate = completedRides.size.toDouble() / allRides.size * 100
val avgRating = hostedRides.map { offer ->
    ratings.filter { it.toUserId == currentUserId }.map { it.rating }
}.flatten().average()
val noShowRate = hostedRides.sumOf { /* count riders who no-showed */ }.toDouble() / totalPassengers
val repeatPassengers = passengers.groupingBy { it }.eachCount().count { it.value > 1 }
```

## Status Transitions

### Ride Status Flow
```
┌───────────────┐
│     ACTIVE    │
└───────────────┘
    ↙       ↖
[Complete] [Cancel]
   ↓         ↓
COMPLETED  CANCELLED
```

### Passenger Status Flow
```
┌──────────────────┐
│   PENDING JOIN   │
└──────────────────┘
        ↓
┌──────────────────┐
│   CONFIRMED      │
└──────────────────┘
    ↙        ↖
[Complete] [No-Show]
   ↓         ↓
COMPLETED  NO_SHOW
```

## Features

### 1. Real-time Updates
- Statistics update as passengers join
- Seats occupied count updates instantly
- Status changes reflected immediately
- Revenue calculation updates live

### 2. Filtering & Sorting
- Filter by status: All, Active, Completed, Cancelled
- Sort by: Departure time (newest first)
- Quick view of active vs past rides

### 3. Passenger Engagement
- Direct messaging to passengers
- One-click no-show tracking
- Profile view with verification badges
- Rating visibility

### 4. Trip Management
- Start/Complete/Cancel actions
- Seat management (view availability)
- Passenger list with quick actions
- Revenue tracking per ride

## Material 3 Design

### Colors
- **Primary Action:** Split CruiserSaffron (Blue)
- **Success:** Split CruiserEmerald (Green)
- **Warning:** Color(0xFFEF4444) (Red)
- **Text:** Split CruiserTextPrimary (Dark)
- **Secondary Text:** Split CruiserLightGray

### Components
- **Cards:** RoundedCornerShape(12-16dp), subtle borders
- **Buttons:** Rounded corners, clear hierarchy
- **Icons:** Material Icons, sized appropriately
- **Spacing:** 16dp base padding, 8dp gaps

### Typography
- **Headline:** 20sp, Bold
- **Title:** 14-16sp, Bold
- **Body:** 12-14sp, Regular
- **Caption:** 10-11sp, Regular

## Navigation

### Routes
```
host_dashboard          → Main dashboard
trip_detail/{id}/offer  → Full trip details
chat/{matchId}          → Message passenger
profile                 → View passenger profile
```

### Navigation Flow
```
Dashboard
    ↓
[View Trip] → Trip Detail
    ↓
    ├─ [Message] → Chat
    ├─ [View Profile] → Passenger Profile
    └─ [No Show] → Confirmation Dialog
```

## Interactions

### Tap Behaviors
- **Tap ride card:** Navigate to trip details
- **Tap passenger name:** View passenger profile
- **Tap [Message]:** Open chat
- **Tap [No Show]:** Show confirmation
- **Tap filter chip:** Update list

### Swipe Behaviors (Future)
- Swipe left on ride → Quick actions menu
- Swipe right → Mark complete
- Swipe down → Refresh

## Empty States

### No Hosted Rides
```
Illustration: Car with question mark
Title: "No Hosted Rides"
Description: "You haven't posted any trip offers yet."
CTA: [Post a Ride]
```

### No Completed Rides
```
(When filtering by "Completed")
Illustration: Calendar check
Title: "No Completed Rides"
Description: "Your completed trips will appear here."
```

## Performance Optimization

### LazyColumn Efficiency
```kotlin
LazyColumn {
    item { /* header */ }
    item { /* stats */ }
    item { /* filters */ }
    items(filteredRides) { offer ->
        HostedRideScheduleCard(...)
    }
}
```

### State Management
```kotlin
val filteredRides = remember(hostedRides, filterStatus) { ... }
```
- Only recalculates when dependencies change
- Avoids unnecessary recompositions

## Testing Scenarios

### Scenario 1: View Active Rides
```
1. User navigates to Host Dashboard
2. See 3 active rides in statistics
3. See "Active" filter pre-selected
4. List shows only active rides
5. Rides sorted by departure time (newest first)
```

### Scenario 2: Filter Rides
```
1. Initially showing all rides (5 total)
2. Click [Completed] filter
3. List updates to show only 2 completed rides
4. Statistics cards update counts
5. Click [All Rides] to reset
```

### Scenario 3: Manage Passenger
```
1. Tap on hosted ride
2. See passenger: "Maria G." ⭐4.2
3. Click [Message]
4. Chat opens with Maria
5. Back to dashboard
6. Click [No Show] on Maria
7. Confirmation dialog appears
8. Confirm → no-show recorded
9. Maria's no-show count incremented
```

### Scenario 4: Complete Ride
```
1. Click ride card
2. Click [Complete Trip]
3. Confirmation dialog
4. Confirm → status = "completed"
5. Passengers can now rate driver
6. Ride moves to "Completed" tab
7. Revenue is tallied
```

### Scenario 5: Cancel Ride
```
1. Click ride card
2. Click [Cancel Trip]
3. Confirmation dialog with reason
4. Confirm → status = "cancelled"
5. Passengers notified
6. Ride moves to "Cancelled" tab
7. No revenue recorded
```

## Future Enhancements

### Phase 2: Advanced Analytics
- [ ] Weekly/monthly trip statistics
- [ ] Earnings breakdown
- [ ] Passenger feedback summary
- [ ] Route preferences
- [ ] Time-based insights

### Phase 3: Passenger Insights
- [ ] Repeat passenger tracking
- [ ] Preferred routes
- [ ] Peak hours
- [ ] Passenger demographics

### Phase 4: Automated Features
- [ ] Auto-accept trusted passengers
- [ ] Recurring trip scheduling
- [ ] Smart pricing suggestions
- [ ] Weather-based alerts

### Phase 5: Communication
- [ ] In-app calling
- [ ] Voice messages
- [ ] Broadcasting to all passengers
- [ ] Automated SMS/Email

## API Methods Used

### Repository Methods
```kotlin
viewModel.updateTripOfferStatus(offerId, newStatus)
viewModel.refreshMyTrips()
viewModel.acceptMatch(matchId)
viewModel.declineMatch(matchId)
viewModel.completeTrip(matchId)
repository.sendMessage(matchId, text)
repository.getUserMatches(userId)
```

### ViewModel Properties
```kotlin
val hostedRides: StateFlow<List<TripOffer>>
val currentUser: StateFlow<User?>
val userMatches: StateFlow<List<TripMatch>>
```

## Accessibility

### Color Contrast
- ✅ Text on background: 4.5:1 (WCAG AA)
- ✅ Icons have high contrast
- ✅ Interactive elements clearly defined

### Touch Targets
- ✅ Buttons: 44-48dp minimum
- ✅ Cards: Clickable area > 48dp
- ✅ Spacing: 8dp minimum between targets

### Semantic Labels
```kotlin
Icon(
    imageVector = Icons.Default.Message,
    contentDescription = "Message passenger"
)
```

---

**Last Updated**: 2026-07-23
**Status**: Host Dashboard UI documented and implemented

