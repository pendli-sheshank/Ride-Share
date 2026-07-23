# User Profiles & Verification - Complete Guide

## Overview

SawaariShare maintains detailed user profiles with multi-tier verification system for trust and safety in ride-sharing.

## Firestore Collection: `users`

### Document Structure
```
users/{userId}
├── id: String                          // Firebase UID
├── email: String                       // Auth email (lowercase)
├── name: String                        // Full name for display
├── lastInitial: String                 // Last initial only (privacy)
├── avatarUrl: String                   // Firebase Storage URL to profile pic
├── verifiedTier: String                // "vouched" | "guest"
├── invitedBy: String                   // Referrer's user ID
├── ratingAvg: Float                    // Aggregate rating (0.0 - 5.0)
├── ratingCount: Int                    // Number of ratings received
├── noShowCount: Int                    // Times user no-showed/cancelled
├── communityId: String                 // University ID (e.g., "neu_boston")
├── homeArea: String                    // Neighborhood (e.g., "Mission Hill")
├── isWomenOnlyFilterEnabled: Boolean   // Rider preference filter
├── fcmToken: String                    // Firebase Cloud Messaging token
├── emailNotificationsEnabled: Boolean  // Email alerts preference
├── pushNotificationsEnabled: Boolean   // Push alerts preference
├── collegeName: String                 // Detected from college email domain
└── verifiedEmail: String               // College email for vouched tier
```

### Document Metadata
```
created: Timestamp                      // Auto-generated on creation
modified: Timestamp                     // Auto-generated on update
```

## User Tiers

### Tier 1: Vouched ✅
**Requirements:**
- ✅ Valid email/password authentication
- ✅ Redeemed invite code OR verified college email
- ✅ Completed profile setup

**Benefits:**
- Post rides as driver
- Join rides as passenger
- Access to full SawaariShare community
- Appear in trusted rider/driver listings

**Invite Codes (Pre-populated):**
```
SAWAARISHARE
INDIANSTUDENTS
WELCOME2026
VOUCHEDCODE
```

### Tier 2: Guest (Deprecated)
Legacy tier - all new users start as vouched.

## Profile Lifecycle

### 1. Sign Up
```kotlin
fun signUpWithEmail(email: String, password: String)
```
- Firebase creates auth account
- User document created with minimal data (id, email)
- Status: Unverified, must redeem invite code

### 2. Invite Code Redemption
```kotlin
suspend fun redeemInviteCode(code: String): Result<Unit>
```
- User enters valid invite code
- Code marked as used
- User tier upgraded to "vouched"
- Profile becomes active

### 3. Profile Setup (Onboarding)
```kotlin
suspend fun createUserProfile(
    name: String,
    lastInitial: String,
    communityId: String,
    homeArea: String,
    vehicle: Vehicle?
): Result<Unit>
```

**Profile Fields:**
| Field | Type | Validation | Example |
|-------|------|-----------|---------|
| name | String | 1-50 chars, not empty | "John Smith" |
| lastInitial | String | 1 char | "S" |
| communityId | String | Must exist in communities | "neu_boston" |
| homeArea | String | 1-100 chars | "Mission Hill, Boston" |
| vehicle (optional) | Vehicle | Make/model/plate | See below |

**Vehicle Structure:**
```kotlin
data class Vehicle(
    val ownerId: String,        // User ID
    val make: String,           // e.g., "Toyota"
    val model: String,          // e.g., "Camry"
    val year: String,           // e.g., "2020"
    val color: String,          // e.g., "Silver"
    val licensePlate: String    // e.g., "ABC1234"
)
```

Stored separately in `vehicle_{userId}.json` for privacy.

### 4. College Email Verification
```kotlin
suspend fun verifyCollegeEmail(collegeEmail: String): Result<Unit>
```

**Known College Domains:**
- northeastern.edu, asu.edu, utdallas.edu, usc.edu, indiana.edu
- mit.edu, harvard.edu, stanford.edu, caltech.edu, yale.edu
- columbia.edu, upenn.edu, dartmouth.edu, brown.edu, cornell.edu
- emory.edu, michigan.edu, northwestern.edu, duke.edu, chicago.edu

**Process:**
1. User enters college email (e.g., jsmith@northeastern.edu)
2. System validates domain is known college
3. Extracts college name from domain
4. Updates user.verifiedEmail and user.collegeName
5. Profile shows verified badge in ride listings

### 5. Profile Updates
```kotlin
suspend fun updateUserProfileDetails(
    name: String,
    lastInitial: String,
    collegeName: String,
    avatarUrl: String,
    verifiedEmail: String
): Result<Unit>
```

Allows users to update profile after initial setup (edit dialog).

## Privacy by Design

### Display Name Strategy
Instead of full name, SawaariShare displays: **"FirstName LastInitial."**

**Example:**
- Stored: `name = "John Smith"`, `lastInitial = "S"`
- Displayed: **"John S."**

**Benefit:** Protects user privacy while enabling identification

### Profile Picture Storage
- Uploaded to Firebase Storage: `gs://bucket/avatars/{userId}/profile.jpg`
- URL stored in `user.avatarUrl`
- Served with CDN caching
- Users can update anytime

### Hidden Information
- ❌ Full last name (only initial)
- ❌ Email address (only verified domain)
- ❌ Phone number (future: only for matched rides)
- ❌ Address (only neighborhood/"home area")

## Rating System Integration

### Rating Workflow
```
After completed trip:
1. Driver rates passenger: rating, comment
2. Passenger rates driver: rating, comment
3. Ratings stored in Firestore ratings collection
4. onRatingWrite trigger recalculates user averages
5. Updated ratings visible on next profile view
```

### Rating Calculation
```kotlin
ratingAvg = sum(all ratings) / count(ratings)
ratingCount = count(ratings received)
```

**Display:**
- ⭐ 4.5 stars (12 ratings)
- Shown on driver/passenger cards in ride listings

### No-Show Tracking
```
noShowCount incremented when:
- User cancels accepted ride < 30 mins before departure
- User doesn't show up for pickup
- Host marks passenger as no-show
```

**Impact:**
- Affects rider/driver trust score
- Visible to other users in profile
- After 3 no-shows, account flagged for review

## Communities

### Pre-loaded Communities
```json
[
  { "id": "neu_boston", "name": "Northeastern University", "location": "Boston, MA" },
  { "id": "asu_tempe", "name": "Arizona State University", "location": "Tempe, AZ" },
  { "id": "utd_dallas", "name": "University of Texas at Dallas", "location": "Richardson, TX" },
  { "id": "usc_la", "name": "University of Southern California", "location": "Los Angeles, CA" },
  { "id": "iub_bloom", "name": "Indiana University Bloomington", "location": "Bloomington, IN" }
]
```

Users select home community during profile setup.

## Firestore Security Rules

### Recommended Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read all profiles (public) but only update own
    match /users/{userId} {
      allow read: if request.auth != null;
      allow create: if request.auth.uid == userId;
      allow update: if request.auth.uid == userId;
      allow delete: if false; // Never delete, mark inactive
    }
  }
}
```

### Profile Visibility
- **Public:** Name, last initial, avatar, college, ratings, verified status
- **Semi-private:** Home area, community, vehicle info (drivers only)
- **Private:** Email, phone, address details

## Real-time Profile Updates

### Local Cache Flow
1. User updates profile in app
2. Immediate update to local `_users` StateFlow
3. UI refreshes instantly (optimistic update)
4. Async sync to Firestore in background
5. If sync fails, logged and retried

### Firestore Listeners
Currently: Manual fetch via `fetchUserProfileFromFirestore()`

Future enhancement: Add snapshot listener for real-time updates
```kotlin
firebaseFirestore?.collection("users")?.document(userId)
    ?.addSnapshotListener { snapshot, error ->
        if (snapshot != null) {
            val user = snapshot.toObject(User::class.java)
            // Update local cache
        }
    }
```

## Profile Display Scenarios

### Scenario 1: Ride Listing Card
Shows on trip offer card:
- **"John S."** (display name)
- ⭐ 4.5 (12 ratings) (rating badge)
- ✅ Verified (college email) (trust badge)
- Vehicle: 2020 Silver Toyota Camry (driver cards only)

### Scenario 2: Driver Profile View
User taps driver name on ride:
- Full profile card:
  - Avatar (if set)
  - Name: "John S."
  - Rating: ⭐ 4.5/5.0 (12 ratings)
  - Verification: ✅ Northeastern University
  - Home Area: "Mission Hill, Boston"
  - Vehicle: "2020 Silver Toyota Camry"
  - No-Show Count: 0
  - Action: [Call] [Email]

### Scenario 3: User Settings > Edit Profile
Full editable profile:
- Name field
- Last initial field
- Community selector
- Home area input
- Avatar picker
- College email field
- Vehicle details (if driver)

## Validation & Error Handling

### Profile Creation Validation
```kotlin
if (name.trim().isEmpty()) {
    return Result.failure(Exception("Name is required"))
}
if (lastInitial.trim().isEmpty()) {
    return Result.failure(Exception("Last initial is required"))
}
if (communityId.isEmpty()) {
    return Result.failure(Exception("Please select your community"))
}
if (homeArea.isEmpty()) {
    return Result.failure(Exception("Home area is required"))
}
```

### College Email Validation
```kotlin
if (!email.contains("@") || !email.contains(".")) {
    return Result.failure(Exception("Please enter a valid email"))
}
if (!isValidCollegeEmail(email)) {
    return Result.failure(Exception("Email domain not recognized as college"))
}
```

## Testing Scenarios

### Scenario 1: Complete Onboarding
```
1. SignUp: test@college.edu / password123
2. Redeem: SAWAARISHARE
3. Profile: John | S | NEU Boston | Mission Hill
4. Verify: john@northeastern.edu
5. Set Vehicle: 2020 Silver Camry
Expected: Profile complete, can post/join rides
```

### Scenario 2: Update Profile
```
1. Login: existing user
2. Edit: Change name, last initial, avatar
3. Expected: Changes sync to Firestore, visible to others
```

### Scenario 3: View Other Profile
```
1. Login: User A
2. Browse offers
3. Tap "Ride by John S."
4. Expected: See public profile info
```

### Scenario 4: College Email Verification
```
1. User with personal email
2. Verify: jsmith@northeastern.edu
3. Expected: "Northeastern University" label appears
4. Future: Priority in ride matching
```

## Database Operations

### Create User Profile
```javascript
// Firestore
POST /users/{userId}
{
  "id": "user_123",
  "email": "john@college.edu",
  "name": "John Smith",
  "lastInitial": "S",
  "communityId": "neu_boston",
  "homeArea": "Mission Hill",
  "verifiedTier": "vouched",
  "verifiedEmail": "john@northeastern.edu",
  "collegeName": "Northeastern University"
}
```

### Update Profile
```javascript
PATCH /users/{userId}
{
  "name": "Jonathan Smith",
  "avatarUrl": "gs://bucket/avatars/user_123/profile.jpg"
}
```

### Fetch Profile
```javascript
GET /users/{userId}
Response: Full User document
```

## Performance Considerations

### Firestore Query Efficiency
- **Profile fetch by ID:** O(1) - Direct document read
- **Profile search by email:** O(n) - Requires collection scan
  - Future: Add Firestore index on `email` field

### Caching Strategy
- **Local:** All profiles cached in `_users` StateFlow
- **TTL:** Profiles refreshed when ride listing refreshed
- **Invalidation:** Manual call to `fetchUserProfileFromFirestore()`

## Migration & Backwards Compatibility

### Local to Firestore Migration
Existing local `users.json` profiles auto-sync on:
1. First app launch (if Firebase enabled)
2. User profile update
3. Manual refresh

### Schema Evolution
Future fields can be added without breaking existing profiles:
```kotlin
val user = doc.toObject(User::class.java)
// Missing fields default to empty/default values
```

## Future Enhancements

### Phase 2: Enhanced Verification
- [ ] Identity verification (photo ID scan)
- [ ] Background check integration
- [ ] Phone number verification
- [ ] Social media linking

### Phase 3: Reputation System
- [ ] Detailed rating categories (safety, reliability, communication)
- [ ] Response time metrics
- [ ] Verified badge levels
- [ ] Trust score calculation

### Phase 4: Advanced Features
- [ ] Preferred driver/rider lists
- [ ] Referral rewards tracking
- [ ] Badge system (eco-friendly, reliabel, etc.)
- [ ] Profile completion percentage

---

**Last Updated**: 2026-07-23
**Status**: Core profile system complete, Firestore integration enhanced
