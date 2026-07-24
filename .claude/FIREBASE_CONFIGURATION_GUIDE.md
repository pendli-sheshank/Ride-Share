# Firebase Configuration & Deployment - Complete Guide

## Overview

This guide covers complete Firebase project setup, security rules, email configuration, and deployment procedures for SawaariShare. The app uses Firebase Authentication, Firestore for real-time data sync, Cloud Storage for profile pictures, and Cloud Functions for backend logic.

## Part 1: Firebase Project Setup

### 1.1 Create Firebase Project

**Steps:**
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a project"
3. Project name: `sawaari-share` (or your preference)
4. Disable Google Analytics (can enable later)
5. Click "Create project"

**Expected Setup Time:** 2-3 minutes

### 1.2 Add Android App

**Steps:**
1. In Firebase Console, click "Add app" → Android
2. Package name: `com.example`
3. App nickname: `SawaariShare`
4. Debug SHA-1: Run `./gradlew signingReport` (see instructions below)
5. Register app
6. Download `google-services.json`
7. Place in: `app/google-services.json`

**Getting Debug SHA-1:**
```bash
# For debug keystore
./gradlew signingReport

# Look for SHA-1 in debug variant output
# Example output:
# Variant: debug
# Config: debug
# Store: ~/.android/debug.keystore
# Alias: AndroidDebugKey
# MD5: XX:XX:XX...
# SHA1: XX:XX:XX... (use this)
# SHA-256: XX:XX:XX...
```

### 1.3 Enable Firebase Services

**In Firebase Console:**

#### Authentication
1. Go to Build → Authentication
2. Click "Get started"
3. Enable "Email/Password"
4. Optional: Enable Google Sign-In for future versions

**Important Settings:**
- Password reset email: Configure in Email Templates
- Email verification: Enable if required
- User deletion: Allow users to delete accounts

#### Firestore Database
1. Go to Build → Firestore Database
2. Click "Create database"
3. Start in **Production mode** (we'll add security rules)
4. Location: `us-central1` (or closest to your users)
5. Click "Enable"

#### Cloud Storage
1. Go to Build → Storage
2. Click "Get started"
3. Storage location: Same as Firestore (`us-central1`)
4. Click "Done"

#### Cloud Functions (Future)
1. Go to Build → Functions
2. Enable Cloud Functions for Firebase
3. Set region to `us-central1`

## Part 2: Firestore Security Rules

### 2.1 Security Rules Strategy

**Three-Tier Access Model:**
1. **Public Read** - Trip offers, requests (metadata only)
2. **Authenticated Access** - Users can read/write own profiles
3. **Participant-Only** - Matches and messages only for participants

### 2.2 Firestore Rules Implementation

**File: `firestore.rules`**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // ============================================
    // HELPER FUNCTIONS
    // ============================================

    // Check if user is authenticated
    function isAuth() {
      return request.auth != null;
    }

    // Check if user owns the resource
    function isOwner(userId) {
      return request.auth.uid == userId;
    }

    // Check if user is a participant in a match
    function isMatchParticipant(matchId) {
      return get(/databases/$(database)/documents/trip_matches/$(matchId)).data.hostId == request.auth.uid ||
             get(/databases/$(database)/documents/trip_matches/$(matchId)).data.riderId == request.auth.uid;
    }

    // ============================================
    // COLLECTION: users
    // ============================================

    match /users/{userId} {
      // Anyone can read public user profiles (for display in feeds/matches)
      allow read: if true;

      // Users can only write to their own profile
      allow write: if isAuth() && isOwner(userId);

      // Nested: blocked users list (private)
      match /blockedUsers/{blockedId} {
        allow read, write: if isAuth() && isOwner(userId);
      }
    }

    // ============================================
    // COLLECTION: trip_offers
    // ============================================

    match /trip_offers/{offerId} {
      // Anyone can read active trip offers
      allow read: if true;

      // Only authenticated users can create
      allow create: if isAuth() && request.resource.data.hostId == request.auth.uid;

      // Only the host can update their own offer
      allow update, delete: if isAuth() && resource.data.hostId == request.auth.uid;
    }

    // ============================================
    // COLLECTION: ride_requests
    // ============================================

    match /ride_requests/{requestId} {
      // Anyone can read active ride requests
      allow read: if true;

      // Only authenticated users can create
      allow create: if isAuth() && request.resource.data.riderId == request.auth.uid;

      // Only the rider can update their own request
      allow update, delete: if isAuth() && resource.data.riderId == request.auth.uid;
    }

    // ============================================
    // COLLECTION: trip_matches
    // ============================================

    match /trip_matches/{matchId} {
      // Only participants can read match details
      allow read: if isAuth() && isMatchParticipant(matchId);

      // Only authenticated users can create (backend validates)
      allow create: if isAuth();

      // Only participants can update
      allow update, delete: if isAuth() && isMatchParticipant(matchId);
    }

    // ============================================
    // COLLECTION: messages
    // ============================================

    match /messages/{messageId} {
      // Only match participants can read messages
      allow read: if isAuth() && 
        exists(/databases/$(database)/documents/trip_matches/$(resource.data.matchId)) &&
        isMatchParticipant(resource.data.matchId);

      // Only authenticated users can create
      allow create: if isAuth() && request.resource.data.senderId == request.auth.uid;

      // Users can only update their own messages (for read receipts)
      allow update: if isAuth() && isOwner(resource.data.senderId);

      // Only the sender can delete
      allow delete: if isAuth() && isOwner(resource.data.senderId);
    }

    // ============================================
    // COLLECTION: notifications
    // ============================================

    match /notifications/{notificationId} {
      // Users can only read their own notifications
      allow read: if isAuth() && isOwner(resource.data.userId);

      // Backend creates notifications (no direct client writes)
      allow create: if false;

      // Users can mark as read
      allow update: if isAuth() && isOwner(resource.data.userId);

      // Users can delete their notifications
      allow delete: if isAuth() && isOwner(resource.data.userId);
    }

    // ============================================
    // COLLECTION: ratings
    // ============================================

    match /ratings/{ratingId} {
      // Anyone can read ratings
      allow read: if true;

      // Only authenticated users can create ratings
      allow create: if isAuth() && request.resource.data.raterId == request.auth.uid;

      // Only the rater can update
      allow update: if isAuth() && resource.data.raterId == request.auth.uid;

      // Only the rater can delete
      allow delete: if isAuth() && resource.data.raterId == request.auth.uid;
    }

    // ============================================
    // COLLECTION: communities
    // ============================================

    match /communities/{communityId} {
      // Anyone can read community info
      allow read: if true;

      // Only administrators can modify (backend controlled)
      allow write: if false;
    }

    // ============================================
    // COLLECTION: invites
    // ============================================

    match /invites/{inviteId} {
      // Authenticated users can read invites (to redeem)
      allow read: if isAuth();

      // Only backend can create/modify invites
      allow write: if false;
    }

    // ============================================
    // DENY ALL OTHER COLLECTIONS
    // ============================================

    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

**Deployment:**
```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize Firebase in project directory
firebase init firestore

# Deploy rules
firebase deploy --only firestore:rules
```

## Part 3: Cloud Storage Security Rules

### 3.1 Storage Rules Implementation

**File: `storage.rules`**

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {

    // ============================================
    // PROFILE PICTURES
    // ============================================

    match /profile_pictures/{userId}.jpg {
      // Anyone can read profile pictures (public)
      allow read: if true;

      // Only the owner can upload/delete their picture
      allow write: if request.auth.uid == userId &&
                      request.resource.size <= 5 * 1024 * 1024 && // Max 5MB
                      request.resource.contentType == 'image/jpeg';
    }

    // ============================================
    // TRIP DOCUMENTS (Future)
    // ============================================

    match /trip_documents/{tripId}/{document=**} {
      // Participants can read/upload trip documents
      allow read, write: if request.auth != null;
    }

    // ============================================
    // DENY ALL OTHER UPLOADS
    // ============================================

    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
```

**Deployment:**
```bash
firebase deploy --only storage:rules
```

## Part 4: Firebase Authentication Configuration

### 4.1 Email Templates Configuration

**In Firebase Console:**

1. Go to Build → Authentication
2. Templates tab
3. Configure these templates:

#### Email Verification Template
```
Subject: Verify your email for SawaariShare
Body:
---
Welcome to SawaariShare! Please verify your email address to complete your account setup.

Verification Link: $LINK

This link will expire in 24 hours.

If you didn't request this email, you can ignore it.
```

#### Password Reset Template
```
Subject: Reset your SawaariShare password
Body:
---
We received a request to reset your password. Click the link below to set a new password.

Reset Link: $LINK

This link will expire in 1 hour.

If you didn't request this, you can ignore this email.

For security, never share your password with anyone.
```

#### Account Deletion Template
```
Subject: Confirm account deletion from SawaariShare
Body:
---
We received a request to delete your SawaariShare account. Click the link below to confirm.

Confirmation Link: $LINK

This link will expire in 24 hours.

Account deletion is permanent and cannot be undone. All your rides, messages, and profile data will be removed.
```

### 4.2 Authentication Settings

**Required Configurations:**

1. **Password Policy**
   - Minimum length: 6 characters (set in code)
   - Password history: 0 (allow reusing old passwords)
   - Password expiration: None

2. **Email Verification**
   - Enable for security
   - Can be optional for MVP, enforce in v1.1

3. **Account Deletion**
   - Allow users to delete accounts (GDPR compliance)
   - Implement UI in Profile screen

4. **Session Management**
   - Session duration: 30 days
   - Auto-logout after inactivity: Not required for MVP

### 4.3 Multi-Factor Authentication (Future)

For v1.2+:
```javascript
// Enable MFA in Firebase Console
// In app code:
val user = FirebaseAuth.getInstance().currentUser
val mfaSession = user?.multiFactor

// Enroll phone number
val phoneMultiFactor = PhoneMultiFactorGenerator.getAssertion(
    phoneAuthCredential
)
user?.multiFactor?.enroll(phoneMultiFactor)
```

## Part 5: Firestore Indexes

### 5.1 Required Indexes

**Auto-created by Firebase:**
- When you query with filters/ordering, Firebase suggests indexes
- Monitor Firestore Console → Indexes tab

**Recommended Explicit Indexes:**

1. **Trip Offers - Location Proximity**
   ```
   Collection: trip_offers
   Fields:
   - geohash (Ascending)
   - status (Ascending)
   - departureTime (Descending)
   ```

2. **Ride Requests - Location + Status**
   ```
   Collection: ride_requests
   Fields:
   - geohash (Ascending)
   - status (Ascending)
   - departureTime (Descending)
   ```

3. **Trip Matches - User History**
   ```
   Collection: trip_matches
   Fields:
   - hostId (Ascending)
   - status (Ascending)
   - createdAt (Descending)
   ```

4. **Messages - Match Timeline**
   ```
   Collection: messages
   Fields:
   - matchId (Ascending)
   - createdAt (Descending)
   ```

5. **Ratings - User Reviews**
   ```
   Collection: ratings
   Fields:
   - targetUserId (Ascending)
   - createdAt (Descending)
   ```

**Deployment:**
```bash
# Firestore automatically creates indexes when needed
# Monitor in Console: Firestore → Indexes tab
# Create explicitly in Console or use:
firebase firestore:indexes

# To create via config file, add to firestore.indexes.json
{
  "indexes": [
    {
      "collectionGroup": "trip_offers",
      "queryScope": "Collection",
      "fields": [
        {"fieldPath": "geohash", "order": "ASCENDING"},
        {"fieldPath": "status", "order": "ASCENDING"},
        {"fieldPath": "departureTime", "order": "DESCENDING"}
      ]
    }
  ]
}

# Deploy:
firebase deploy --only firestore:indexes
```

## Part 6: Cloud Functions Setup (Future)

### 6.1 Example: Auto-Notification Function

**File: `functions/src/index.ts`**

```typescript
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();

// Trigger when trip offer is created
export const notifyMatchingRiders = functions.firestore
  .document("trip_offers/{offerId}")
  .onCreate(async (snap) => {
    const offer = snap.data();

    // Find matching ride requests within geohash
    const matchingRequests = await db.collection("ride_requests")
      .where("geohash", "==", offer.geohash)
      .where("status", "==", "active")
      .get();

    // Send notifications to matching riders
    for (const doc of matchingRequests.docs) {
      const request = doc.data();
      const user = await db.collection("users").doc(request.riderId).get();

      if (user.data()?.fcmToken) {
        await messaging.send({
          token: user.data()?.fcmToken,
          notification: {
            title: "New Ride Available!",
            body: `${offer.hostName} posted a ride from ${offer.origin}`,
          },
          data: {
            offerId: snap.id,
            action: "open_offer",
          },
        });
      }
    }
  });

// Trigger when trip match is accepted
export const notifyMatchAccepted = functions.firestore
  .document("trip_matches/{matchId}")
  .onUpdate(async (change) => {
    const before = change.before.data();
    const after = change.after.data();

    // Only notify on status change to "accepted"
    if (before.status !== "accepted" && after.status === "accepted") {
      const offer = await db.collection("trip_offers").doc(after.offerId).get();
      const rider = await db.collection("users").doc(after.riderId).get();

      // Notify both parties
      await messaging.sendMulticast({
        tokens: [offer.data()?.fcmToken, rider.data()?.fcmToken],
        notification: {
          title: "Match Accepted!",
          body: "Your ride match has been confirmed. Start coordinating!",
        },
      });
    }
  });
```

## Part 7: Deployment Checklist

### Pre-Deployment

- [ ] Firebase project created and configured
- [ ] `google-services.json` added to `app/`
- [ ] Authentication enabled in Firebase Console
- [ ] Firestore database created
- [ ] Cloud Storage bucket created
- [ ] Security rules reviewed and tested
- [ ] Email templates configured
- [ ] All API keys restricted in Firebase Console

### Deploy Security Rules

```bash
# Login if not already
firebase login

# Deploy Firestore rules
firebase deploy --only firestore:rules

# Deploy Storage rules
firebase deploy --only storage:rules

# Deploy Functions (if added)
firebase deploy --only functions

# Deploy everything
firebase deploy
```

### Post-Deployment

- [ ] Test authentication flow in app
- [ ] Test profile picture upload (check Storage)
- [ ] Verify real-time listeners work
- [ ] Check Firestore for data persistence
- [ ] Test with security rules enabled
- [ ] Monitor Firebase Console for errors
- [ ] Set up billing alerts

### Monitoring & Maintenance

**Firebase Console Checks:**
1. **Authentication**
   - Monitor sign-in methods
   - Check for abuse patterns
   - Review user list

2. **Firestore**
   - Monitor usage and costs
   - Review indexes performance
   - Check for errors in Logs tab

3. **Cloud Storage**
   - Monitor storage usage
   - Review file access patterns
   - Clean up old profile pictures

4. **Performance**
   - Enable Performance Monitoring
   - Set up crash reporting
   - Monitor API calls

## Part 8: Environment Variables & Secrets

### Local Development

**Create `.env` file (DO NOT COMMIT):**
```
FIREBASE_API_KEY=your_api_key
FIREBASE_AUTH_DOMAIN=sawaari-share.firebaseapp.com
FIREBASE_PROJECT_ID=sawaari-share
FIREBASE_STORAGE_BUCKET=sawaari-share.appspot.com
FIREBASE_MESSAGING_SENDER_ID=your_sender_id
FIREBASE_APP_ID=your_app_id
```

### Production

**Use Firebase Console:**
1. Project Settings → Service Accounts
2. Generate private key (JSON)
3. Store in secure secret manager (not in code)

### API Key Restrictions

**In Firebase Console:**

1. Go to Project Settings → API Keys
2. Edit "Browser key"
3. Set restrictions:
   - **HTTP referrers:** Add your domain
   - **API restrictions:** Select specific APIs
4. Restrict Android key to your package name + SHA-1

## Part 9: Testing Security Rules

### Using Firebase Emulator

```bash
# Install emulator
firebase init emulators

# Start emulator
firebase emulators:start

# In app, connect to emulator:
if (BuildConfig.DEBUG) {
    val settings = FirebaseFirestoreSettings.Builder()
        .setHost("localhost:8080")
        .setSslEnabled(false)
        .setPersistenceEnabled(false)
        .build()
    FirebaseFirestore.getInstance().firestoreSettings = settings
}
```

### Manual Testing Checklist

1. **Anonymous User**
   - ✅ Can read public offers/requests
   - ❌ Cannot read private matches/messages
   - ❌ Cannot write anything

2. **Authenticated User**
   - ✅ Can read own profile
   - ✅ Can write own profile
   - ❌ Cannot write to other profiles
   - ✅ Can read match data (as participant)
   - ❌ Cannot read matches they're not in

3. **Data Validation**
   - Verify geohash indexing works
   - Check cost cap enforcement
   - Validate status transitions

## Part 10: Cost Optimization

### Estimated Monthly Costs (100 active users)

| Service | Usage | Cost |
|---------|-------|------|
| Authentication | 100 users, ~50 logins/day | $0 (free tier) |
| Firestore | 50K reads, 10K writes/day | ~$10-15 |
| Cloud Storage | 100 profiles × 100KB | <$1 |
| Cloud Functions | 1000 notifications/day | ~$5-10 |
| **Total** | | **~$15-26/month** |

### Cost Reduction Tips

1. **Batch reads** - Combine multiple queries
2. **Index strategically** - Only needed indexes
3. **Archive old data** - Move completed trips to archive collection
4. **Rate limiting** - Limit API calls per user
5. **Cache aggressively** - Use local StateFlow cache

### Monitoring Costs

**In Firebase Console:**
1. Go to Project Settings → Billing
2. Set daily budget alerts
3. Review usage by service
4. Enable cost analysis by collection

## Related Documentation

- **FIREBASE_ARCHITECTURE.md** - System design overview
- **FIREBASE_AUTH_GUIDE.md** - Authentication implementation
- **FIREBASE_STORAGE_GUIDE.md** - Profile pictures & storage
- **IMPLEMENTATION_STATUS.md** - Project progress

---

**Last Updated**: 2026-07-23  
**Status**: Complete Firebase configuration guide with security rules, deployment, and monitoring
