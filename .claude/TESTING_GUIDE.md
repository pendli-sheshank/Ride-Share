# SawaariShare Testing Guide

## Overview

This document provides comprehensive testing procedures for the SawaariShare application, covering unit tests, UI tests, integration tests, and manual testing strategies.

## Test Coverage

### 1. Unit Tests (Repository & ViewModel)

#### Repository Tests (`SawaariRepositoryTest.kt`)

**Trip Offer Tests:**
- ✅ Create valid trip offer
- ✅ Reject offer with missing origin
- ✅ Reject offer with past departure time
- ✅ Get hosted rides filtered by user

**Ride Request Tests:**
- ✅ Create valid ride request
- ✅ Get rider requests filtered by user

**Cost Calculation Tests:**
- ✅ Calculate correct cost split (20 ÷ 4 = 5)
- ✅ Handle division by zero
- ✅ Handle single rider

**User Tests:**
- ✅ Create user with verification
- ✅ Get user by email
- ✅ Update user profile

**Trip Match Tests:**
- ✅ Create trip match
- ✅ Update match status
- ✅ Match lifecycle transitions

**Message Tests:**
- ✅ Create message in match
- ✅ Mark message as read
- ✅ Retrieve match messages

**Rating Tests:**
- ✅ Submit rating with score
- ✅ Calculate average rating
- ✅ Retrieve user ratings

**Community Tests:**
- ✅ Load all communities
- ✅ Verify community exists

#### ViewModel Tests (`MainViewModelTest.kt`)

**Authentication:**
- ✅ Set current user
- ✅ Logout clears user

**Trip Management:**
- ✅ Post trip offer updates state
- ✅ Get multiple trip offers
- ✅ Post ride request updates state

**Message Management:**
- ✅ Send message creates record
- ✅ Get match messages returns correct subset
- ✅ Message ordering by timestamp

**Match Management:**
- ✅ Accept match updates status
- ✅ Cancel match reverts seats
- ✅ Match lifecycle transitions

**Error Handling:**
- ✅ Set error message
- ✅ Clear error state

**Filters:**
- ✅ Toggle women-only filter
- ✅ Filter offers correctly
- ✅ Select community

### 2. UI Tests (Compose Testing)

#### Authentication Screens (`AuthenticationScreensTest.kt`)

**Login Screen:**
- ✅ Display email input field
- ✅ Display password input field
- ✅ Display login button
- ✅ Email validation shows error
- ✅ Invalid input handling

**Signup Screen:**
- ✅ Password mismatch validation
- ✅ Minimum length validation (6 chars)
- ✅ Required field validation

**Invite Redemption:**
- ✅ Valid code enables redeem button
- ✅ Processing state disables button
- ✅ Error message on invalid code

**Profile Setup:**
- ✅ Profile picture upload UI
- ✅ Progress indicator during upload
- ✅ Filled form enables completion

### 3. Integration Tests

#### Ride Flow Tests (`RideFlowIntegrationTest.kt`)

**Complete User Journey:**
- ✅ Host posts ride
- ✅ Rider posts matching request
- ✅ System creates match
- ✅ Participants message each other
- ✅ Match accepted
- ✅ Ratings submitted

**Cost Calculation:**
- ✅ Cost split calculated correctly
- ✅ Multiple rider scenarios

**Filter Application:**
- ✅ Women-only filter works
- ✅ Community filter works
- ✅ Combined filters work

**State Management:**
- ✅ Match cancellation reverts seats
- ✅ State consistency across operations

---

## Running Tests

### Build and Run Unit Tests

```bash
cd /home/user/Ride-Share

# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests com.example.data.SawaariRepositoryTest

# Run with coverage report
./gradlew testDebugUnitTest --coverage

# Run tests with verbose output
./gradlew test --info
```

### Build and Run UI Tests

```bash
# Build debug APK for testing
./gradlew assembleDebugAndroidTest

# Run UI tests on emulator
./gradlew connectedAndroidTest

# Run specific test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.AuthenticationScreensTest

# Run with device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.example.test/androidx.test.runner.AndroidJUnitRunner
```

### Build and Run Integration Tests

```bash
# Run on emulator/device
./gradlew connectedAndroidTest

# Run specific integration test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ui.RideFlowIntegrationTest

# Filter by test method
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.testFile=com.example.ui.RideFlowIntegrationTest#testHostPostsRideAndRiderMatches
```

---

## Firebase Emulator Testing

### Setup

```bash
# Install emulator suite
npm install -g firebase-tools

# Start Firebase emulator suite
firebase emulators:start

# Expected output:
# ✔  Authentication Emulator started at http://localhost:9099
# ✔  Firestore Emulator started at http://localhost:8080
# ✔  Storage Emulator started at http://localhost:9199
```

### Connect App to Emulator

In `SawaariRepository.kt`:

```kotlin
fun setupEmulators() {
    if (BuildConfig.DEBUG) {
        try {
            // Connect to local emulators
            FirebaseFirestore.getInstance().useEmulator("localhost", 8080)
            FirebaseAuth.getInstance().useEmulator("localhost", 9099)
            FirebaseStorage.getInstance().useEmulator("localhost", 9199)
            
            Log.d("Firebase", "Connected to emulators")
        } catch (e: IllegalStateException) {
            Log.e("Firebase", "Already connected to emulators")
        }
    }
}
```

### Emulator Testing Checklist

```
□ Authentication
  □ Sign up with email
  □ Login with email
  □ Logout
  □ Password reset
  
□ Firestore Operations
  □ Create trip offer
  □ Read trip offers (public)
  □ Update own profile
  □ Read private match data
  □ Create message
  □ Read message (participant only)
  
□ Cloud Storage
  □ Upload profile picture
  □ Read profile picture
  □ Reject non-JPEG files
  □ Reject files >5MB
  
□ Real-time Listeners
  □ Receive offer updates
  □ Receive message updates
  □ Receive match notifications
  □ Listeners cleanup on logout
```

---

## Manual Testing Scenarios

### Scenario 1: Complete Ride Booking Flow

**Setup:**
- Host user (Alice) and Rider user (Bob)
- Alice's home: Cambridge, MA; Bob's home: Boston, MA

**Test Steps:**
1. Alice logs in → Mode selector → Selects "Host Mode"
2. Alice creates trip offer:
   - Origin: "Cambridge, MA"
   - Destination: "Boston, MA"
   - Departure: Tomorrow 10:00 AM
   - Seats: 4
   - Cost: $12/person
3. Verify offer appears in explore feed
4. Bob logs in → Selects "Rider Mode"
5. Bob posts ride request:
   - Origin: "Cambridge, MA"
   - Destination: "Boston, MA"
   - Departure: Tomorrow 10:00 AM
   - Seats needed: 2
   - Max cost: $15/person
6. System automatically creates match (TripMatch)
7. Verify both users see match in "Trips" tab
8. Alice clicks match → Chat screen
9. Alice sends message: "Hi Bob! I'll pick you up at 10 AM"
10. Bob receives message in real-time
11. Bob replies: "Perfect! I'll be ready"
12. Alice accepts match
13. Verify match status changes to "accepted"
14. Both complete ride
15. Alice rates Bob: 5 stars, "Great passenger!"
16. Bob rates Alice: 5 stars, "Safe driver!"
17. Verify ratings appear on profiles

**Expected Outcomes:**
- ✅ Offer visible in feed
- ✅ Request created successfully
- ✅ Match created automatically
- ✅ Messages sent/received in real-time
- ✅ Match acceptance works
- ✅ Ratings persist and appear on profiles

### Scenario 2: Women-Only Ride

**Setup:**
- Female driver (Carol) posts women-only ride
- Female rider (Diana) searches with filter

**Test Steps:**
1. Carol logs in → Creates trip offer
2. Carol enables "Women-only" toggle
3. Carol posts offer
4. Diana logs in → Mode selector
5. Diana enables "Women-only filter" toggle
6. Carol's offer appears in Diana's feed
7. Diana clicks offer → Match created
8. Verify match appears for both

**Expected Outcomes:**
- ✅ Women-only filter hides male-hosted offers
- ✅ Male drivers don't see women-only offers

### Scenario 3: Offline Functionality

**Setup:**
- User logged in with data loaded

**Test Steps:**
1. User has active matches and messages
2. Turn off device network
3. User can still view:
   - Trip offers (cached)
   - Ride requests (cached)
   - Own profile
   - Match messages (cached)
4. Verify "Offline" indicator shown
5. Turn on network
6. Verify automatic sync (last sync time updated)
7. Verify new data loaded

**Expected Outcomes:**
- ✅ UI functional offline
- ✅ Cached data visible
- ✅ Write operations queued
- ✅ Auto-sync on reconnect

### Scenario 4: Error Handling

**Setup:**
- User in bad network condition

**Test Steps:**
1. User tries to create offer (slow network)
2. Verify loading indicator shows
3. Network fails midway
4. Verify error message appears
5. User can retry
6. Verify eventual success or clear error

**Expected Outcomes:**
- ✅ Loading states shown
- ✅ Errors communicated clearly
- ✅ Retry mechanism works
- ✅ App doesn't crash

### Scenario 5: Geohashing Proximity

**Setup:**
- Rider looking for ride
- Multiple drivers in different locations

**Test Steps:**
1. Rider posts request from Boston
2. Verify matches from Cambridge (nearby) show
3. Verify matches from NYC (far) don't show
4. Verify accuracy within 1 mile deviation

**Expected Outcomes:**
- ✅ Proximity calculation correct
- ✅ Irrelevant offers filtered out

---

## Performance Testing

### Load Testing

```bash
# Simulate 100 concurrent users creating offers
./gradlew connectedAndroidTest -Pload_test=true

# Monitor metrics:
# - Firestore read/write latency
# - App memory usage
# - Network bandwidth
# - Battery consumption
```

### Profiling

```bash
# CPU profiling
adb shell am start -n com.example/com.example.ui.SawaariApp
# In Android Studio: Profiler → CPU

# Memory profiling
# Profiler → Memory → Record

# Network profiling
# Profiler → Network

# Battery profiling
# Battery Historian or Profiler → Battery
```

### Expected Performance Metrics

- **Feed Load:** <500ms
- **Message Send:** <1s (online), queued (offline)
- **Match Creation:** <2s
- **Image Upload:** <3s for 5MB image
- **Memory:** <150MB average usage
- **Battery:** <5% per hour of active use

---

## CI/CD Test Pipeline

### GitHub Actions

Create `.github/workflows/test.yml`:

```yaml
name: Test Suite

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK
        uses: actions/setup-java@v2
        with:
          java-version: '11'
      
      - name: Run unit tests
        run: ./gradlew test
      
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: app/build/reports/
      
      - name: Build debug APK
        run: ./gradlew assembleDebugAndroidTest
      
      - name: Run Android tests on emulator
        uses: ReactiveCircus/android-emulator-runner@v2
        with:
          api-level: 30
          script: ./gradlew connectedAndroidTest
```

---

## Test Coverage Goals

| Component | Target | Current |
|-----------|--------|---------|
| Repository | 85% | 75% |
| ViewModel | 80% | 70% |
| UI Components | 70% | 50% |
| Utilities | 90% | 85% |
| **Overall** | **80%** | **70%** |

---

## Debugging Tips

### Common Issues

**Test Fails: "Repository is null"**
- Solution: Verify @Before setUp() called
- Ensure TestRunner is correct

**Test Fails: "StateFlow value is null"**
- Solution: Give coroutine time to execute
- Use runTest { } for async operations

**Test Fails: "Activity not found"**
- Solution: Use testTag() for element lookup
- Verify test tag matches composable

**Test Fails: "Network timeout"**
- Solution: Use emulator for predictable conditions
- Mock Firebase when testing offline

### Useful Logging

```kotlin
// Repository logs
Log.d("SawaariShare", "Creating offer: $offer")

// ViewModel logs
Log.d("MainViewModel", "User logged in: ${user.id}")

// UI logs
Log.d("UI", "Screen displayed: $screenName")

// Network logs
Log.d("Network", "Request sent: $url")
```

---

## Maintenance

### Weekly
- [ ] Run full test suite
- [ ] Check test coverage
- [ ] Review flaky tests

### Monthly
- [ ] Update test data
- [ ] Add new test scenarios
- [ ] Remove obsolete tests
- [ ] Performance review

### Before Release
- [ ] 100% unit test pass
- [ ] 95% UI test pass
- [ ] Integration test coverage
- [ ] Performance benchmarks met
- [ ] Security tests passed

---

**Last Updated:** 2026-07-24
**Status:** Complete testing framework and procedures
