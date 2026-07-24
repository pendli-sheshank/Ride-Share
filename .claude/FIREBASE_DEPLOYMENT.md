# Firebase Deployment Instructions

## Quick Start Deployment (15 minutes)

### Prerequisites

1. **Firebase CLI installed**
   ```bash
   npm install -g firebase-tools
   ```

2. **Firebase project created** (see FIREBASE_CONFIGURATION_GUIDE.md)

3. **google-services.json in place**
   ```
   app/google-services.json
   ```

4. **Authenticated with Firebase**
   ```bash
   firebase login
   ```

### Step 1: Initialize Firebase in Project

```bash
cd /home/user/Ride-Share

# If not already initialized
firebase init --project=your-project-id

# Select options:
# ✓ Firestore
# ✓ Storage
# ✓ Authentication
# ✓ Functions (optional, for future)
```

### Step 2: Deploy Security Rules

```bash
# Deploy Firestore rules
firebase deploy --only firestore:rules

# Deploy Storage rules
firebase deploy --only storage:rules

# Deploy both
firebase deploy --only firestore:rules,storage:rules
```

**Expected Output:**
```
✔  Deploy complete!

Project Console: https://console.firebase.google.com/project/your-project-id/overview
```

### Step 3: Verify Deployment

1. **In Firebase Console:**
   - Go to Firestore → Rules
   - Verify rules are deployed (timestamp shows recent)
   - Go to Storage → Rules
   - Verify storage rules are deployed

2. **Test in app:**
   ```bash
   # Run app in emulator or device
   ./gradlew installDebug
   
   # Try authentication flow
   # Should work normally with new rules
   ```

---

## Deployment Checklist

### Pre-Deployment Tasks

- [ ] Firebase project created and configured
- [ ] `google-services.json` added to `app/`
- [ ] Firebase CLI installed locally
- [ ] Authenticated with Firebase (`firebase login`)
- [ ] No uncommitted changes in rules files
- [ ] Rules have been reviewed for security

### Rule Files to Deploy

1. **firestore.rules** - Firestore security rules
   - Protects user privacy (matches/messages participant-only)
   - Allows public read of offers/requests
   - Enforces user ownership

2. **storage.rules** - Cloud Storage security rules
   - Profile pictures: anyone can read, owner can write
   - Enforces file size limits (5MB)
   - Enforces JPEG format only

### Deployment Commands

```bash
# Deploy everything
firebase deploy

# Deploy only rules (faster)
firebase deploy --only firestore:rules,storage:rules

# Deploy specific service
firebase deploy --only firestore:rules
firebase deploy --only storage:rules

# Deploy with verbose output
firebase deploy --debug

# Dry run (see what would be deployed)
firebase deploy --dry-run
```

---

## Rollback Procedures

### If Something Goes Wrong

```bash
# Check deployment history
firebase firestore:indexes

# Revert to previous rules (Firebase keeps history)
# In Console: Firestore → Rules → Revisions tab
# Select previous version and restore

# Or manually redeploy safe rules
firebase deploy --only firestore:rules
```

---

## Testing Rules Locally

### Using Firebase Emulator

**Step 1: Install Emulator**
```bash
firebase init emulators

# Select:
# ✓ Firestore Emulator
# ✓ Storage Emulator
# ✓ Authentication Emulator
```

**Step 2: Start Emulator**
```bash
firebase emulators:start
```

**Expected Output:**
```
i  Starting emulators...
✔  Authentication Emulator started at http://localhost:9099
✔  Firestore Emulator started at http://localhost:8080
✔  Storage Emulator started at http://localhost:9199
```

**Step 3: Connect App to Emulator**

In `SawaariRepository.kt`, add emulator configuration:

```kotlin
fun initializeFirebase(context: Context, useEmulator: Boolean = BuildConfig.DEBUG) {
    if (useEmulator) {
        try {
            // Firestore
            FirebaseFirestore.getInstance().useEmulator("localhost", 8080)
            
            // Authentication
            FirebaseAuth.getInstance().useEmulator("localhost", 9099)
            
            // Storage
            FirebaseStorage.getInstance().useEmulator("localhost", 9199)
            
            Log.d("Firebase", "Connected to emulators")
        } catch (e: IllegalStateException) {
            // Already connected
        }
    }
}
```

**Step 4: Test Rules**

```bash
# Run app connected to emulator
./gradlew installDebug

# Test authentication - should work
# Test data writes - should respect rules
# Test unauthorized reads - should be denied
```

---

## Manual Security Testing Checklist

### Anonymous User Tests
```
Test: Read public offers
Expected: ✅ Success

Test: Read private match details
Expected: ❌ Permission denied

Test: Write to trip_offers
Expected: ❌ Permission denied
```

### Authenticated User Tests (as Rider)
```
Test: Read own profile
Expected: ✅ Success

Test: Read other user profile
Expected: ✅ Success (public profile)

Test: Write to own profile
Expected: ✅ Success

Test: Write to other user profile
Expected: ❌ Permission denied

Test: Read match data (participant)
Expected: ✅ Success

Test: Read match data (not participant)
Expected: ❌ Permission denied

Test: Read message (participant)
Expected: ✅ Success

Test: Read message (not participant)
Expected: ❌ Permission denied
```

### Upload Profile Picture Tests
```
Test: Upload JPEG < 5MB as owner
Expected: ✅ Success

Test: Upload PNG as owner
Expected: ❌ Wrong format

Test: Upload > 5MB as owner
Expected: ❌ Too large

Test: Upload as non-owner
Expected: ❌ Permission denied
```

---

## Post-Deployment Verification

### In Firebase Console

1. **Firestore → Rules tab**
   - Verify latest rules are deployed
   - Check timestamp matches deployment time
   - Review rule violations in logs

2. **Storage → Rules tab**
   - Verify storage rules are deployed
   - Check for upload errors

3. **Authentication tab**
   - Verify users can sign in
   - Check for authentication errors
   - Monitor login attempts

4. **Firestore → Data tab**
   - Verify data structure matches models
   - Check for expected collections
   - Review document count

### In Android App

```bash
# Run test cases
./gradlew connectedAndroidTest

# Monitor logs
adb logcat | grep Firebase

# Expected normal logs:
# Firestore: Successfully authenticated
# Firebase: Setup complete
# Listeners: Registered

# Error logs to watch for:
# Permission denied
# Unauthenticated access
# Invalid query
```

---

## Monitoring & Debugging

### Firebase Console Monitoring

1. **Firestore → Indexes**
   - Check for slow queries
   - Review index usage
   - Delete unused indexes

2. **Firestore → Logs**
   - Filter by error level
   - Search for permission denied
   - Monitor query performance

3. **Storage → Monitoring**
   - Check upload success rate
   - Monitor bandwidth usage
   - Review error rate

### Common Issues & Solutions

**Issue: Permission denied on all writes**
```
Cause: Rules too restrictive or not deployed
Solution: 
  1. Check firebase deploy output
  2. Verify rules in Console
  3. Check user authentication
  4. Ensure request.auth.uid is set
```

**Issue: Reads working but writes fail**
```
Cause: Write permissions not granted
Solution:
  1. Verify write rule includes isOwner() check
  2. Check that authenticated user owns resource
  3. Validate data structure matches rule expectations
```

**Issue: Messages not syncing**
```
Cause: Listeners blocked by rules
Solution:
  1. Verify isMatchParticipant() function
  2. Check that user is in trip_matches collection
  3. Ensure matchId field exists
  4. Review listener error logs
```

**Issue: Profile pictures not uploading**
```
Cause: Storage rules blocking upload
Solution:
  1. Verify contentType is image/jpeg
  2. Check file size < 5MB
  3. Ensure authenticated
  4. Verify path is profile_pictures/{userId}.jpg
```

---

## Continuous Deployment

### GitHub Actions Integration (Future)

**Create `.github/workflows/deploy.yml`:**

```yaml
name: Deploy Firebase

on:
  push:
    branches: [main]
    paths:
      - 'firestore.rules'
      - 'storage.rules'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Deploy to Firebase
        uses: w9jds/firebase-action@master
        with:
          args: deploy --only firestore:rules,storage:rules
        env:
          FIREBASE_TOKEN: ${{ secrets.FIREBASE_TOKEN }}
```

**Setup:**
```bash
# Generate Firebase token
firebase login:ci

# Add to GitHub secrets
# Settings → Secrets → New repository secret
# Name: FIREBASE_TOKEN
# Value: (paste token from above)
```

---

## Performance Tuning

### Query Optimization

Before:
```kotlin
// This requires a complex index
val matches = db.collection("trip_matches")
    .whereEqualTo("hostId", userId)
    .whereEqualTo("status", "active")
    .orderBy("createdAt", Query.Direction.DESCENDING)
    .limit(20)
    .get()
```

After (with index):
```
// Same query - index ensures fast results
// Create composite index:
// - trip_matches
// - hostId (Ascending)
// - status (Ascending)  
// - createdAt (Descending)
```

### Cost Optimization

1. **Batch reads** - 100 documents in 1 read = 1 operation
2. **Use collection groups** - Avoid redundant data
3. **Archive old data** - Move completed trips to archive
4. **Paginate results** - Limit to 20 per page

---

## Maintenance Schedule

### Weekly
- [ ] Check Firebase console for errors
- [ ] Monitor quota usage
- [ ] Review active user count

### Monthly
- [ ] Audit security rules
- [ ] Review Firestore indexes
- [ ] Check storage usage
- [ ] Analyze query patterns

### Quarterly
- [ ] Security audit of rules
- [ ] Performance optimization review
- [ ] Cost analysis
- [ ] Update dependencies

---

## Related Documentation

- **FIREBASE_CONFIGURATION_GUIDE.md** - Complete Firebase setup
- **firestore.rules** - Firestore security rules
- **storage.rules** - Cloud Storage security rules
- **IMPLEMENTATION_STATUS.md** - Project progress

---

**Last Updated**: 2026-07-23  
**Status**: Complete deployment and monitoring guide with testing procedures
