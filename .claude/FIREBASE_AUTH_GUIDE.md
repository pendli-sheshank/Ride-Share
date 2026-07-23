# Firebase Authentication - Complete Guide

## Overview

SawaariShare uses **Firebase Authentication** with email/password for secure user registration and login. Supports both Firebase and local fallback modes.

## Authentication Flow

### User Registration (Sign Up)
```
1. User enters email + password + confirm password
2. Validation:
   - Email must contain @ and .
   - Password must be 6+ characters
   - Passwords must match
3. Firebase creates auth account → User UID generated
4. User profile created in Firestore (users collection)
5. Email verification sent (optional)
6. User enters onboarding (profile setup)
```

### User Login
```
1. User enters email + password
2. Firebase verifies credentials
3. User object loaded from cache or Firestore
4. User marked as currentUser in repository
5. App navigates to main ride-sharing screens
```

### User Logout
```
1. Firebase.Auth.currentUser set to null
2. Repository clears currentUser
3. App navigates back to login
4. All local session data cleared
```

## Implementation Details

### Repository Layer (`SawaariRepository.kt`)

#### Sign Up
```kotlin
fun signUpWithEmail(
    email: String, 
    password: String, 
    onSuccess: (isNewUser: Boolean) -> Unit, 
    onFailure: (String) -> Unit
)
```

**Process:**
1. Validates email format and password length
2. Calls `FirebaseAuth.createUserWithEmailAndPassword()`
3. Creates new User record in `_users` flow
4. Saves to `users.json` locally
5. Syncs to Firestore `users` collection
6. Sends email verification (Firebase built-in)

**Error Handling:**
- ALREADY_IN_USE → "An account with this email already exists"
- Other Firebase errors passed through

**Fallback Mode:**
- When Firebase unavailable, creates local credential in `credentials.json`
- Generates UUID-based user ID

#### Login
```kotlin
fun logInWithEmail(
    email: String, 
    password: String, 
    onSuccess: (isNewUser: Boolean) -> Unit, 
    onFailure: (String) -> Unit
)
```

**Process:**
1. Calls `FirebaseAuth.signInWithEmailAndPassword()`
2. Retrieves user by UID or email from local cache
3. Sets as `currentUser`
4. Reports `isNewUser` flag for onboarding check

**Fallback Mode:**
- Matches email against `credentials.json`
- Compares password hash
- Authenticates locally

### UI Layer (`SawaariApp.kt`)

#### EmailPasswordLoginScreen
Location: Lines 213-461

**Features:**
- Toggle between Login and Sign Up modes
- Email field with validation
- Password field with show/hide toggle
- Confirm password field (signup only)
- Real-time Firebase status indicator
- Error display with Material 3 design

**Validation:**
```kotlin
// Email validation
if (!email.contains("@") || !email.contains(".")) {
    setError("Please enter a valid email address.")
}

// Password validation
if (password.length < 6) {
    setError("Password must be at least 6 characters")
}

// Confirm password (signup)
if (password != confirmPassword) {
    setError("Passwords do not match")
}
```

### ViewModel Layer (`MainViewModel.kt`)

#### Authentication Methods
```kotlin
// Sign up with email
fun signUpWithEmail(email: String, password: String, onFinished: (isNewUser: Boolean) -> Unit)

// Login with email
fun loginWithEmail(email: String, password: String, onFinished: (isNewUser: Boolean) -> Unit)

// Redeem invite code (verification)
fun redeemInviteCode(code: String, onSuccess: () -> Unit)

// Complete user profile after signup
fun completeProfile(
    name: String,
    lastInitial: String,
    communityId: String,
    homeArea: String,
    vehicle: Vehicle?,
    onSuccess: () -> Unit
)

// Verify college email (vouched tier)
fun verifyCollegeEmail(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit)

// Logout
fun logout()
```

## Data Models

### User (Authentication Profile)
```kotlin
data class User(
    val id: String = "",                          // Firebase UID
    val email: String = "",                       // Auth email
    val name: String = "",                        // Display name (set during onboarding)
    val lastInitial: String = "",                 // Privacy: show as "John D."
    val avatarUrl: String = "",                   // Profile picture (Firebase Storage)
    val verifiedTier: String = "vouched",         // "vouched" or "guest"
    val invitedBy: String = "",                   // Who referred them
    val verifiedEmail: String = "",               // College email for vouched tier
    val collegeName: String = "",                 // Auto-detected from email domain
    val ratingAvg: Float = 0.0f,                  // Aggregate rating
    val ratingCount: Int = 0,                     // Number of ratings received
    val noShowCount: Int = 0,                     // Cancelled rides count
    // ... other fields
)
```

### LocalCredential (Fallback Storage)
```kotlin
data class LocalCredential(
    val email: String = "",                       // Login email
    val password: String = "",                    // Plain text (fallback only)
    val userId: String = ""                       // Generated user ID
)
```

Stored in `credentials.json` when Firebase unavailable.

## Security Considerations

### ✅ Implemented
- Firebase handles password hashing (bcrypt)
- Email validation prevents typos
- Password minimum length enforced (6 chars)
- Email confirmation optional (Firebase sends)
- Secure session management via Firebase
- User UID as primary identifier (not email)
- Local credentials only in fallback mode

### 🔒 Firestore Security Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only read/update their own profile
    match /users/{userId} {
      allow read: if request.auth.uid == userId || request.auth != null;
      allow update, delete: if request.auth.uid == userId;
      allow create: if request.auth.uid == userId;
    }
  }
}
```

### ⚠️ Password Policy Recommendations
- Minimum 6 characters (currently enforced)
- Recommend 12+ characters for production
- Consider requiring: uppercase, lowercase, numbers, special chars
- Add password reset flow (email link)
- Add 2FA for high-value accounts

## Firebase Configuration

### .env Setup
```
FIREBASE_API_KEY=your_api_key
FIREBASE_APP_ID=your_app_id
FIREBASE_PROJECT_ID=your_project_id
FIREBASE_STORAGE_BUCKET=your_bucket
```

### BuildConfig Integration
```kotlin
BuildConfig.FIREBASE_API_KEY       // From .env
BuildConfig.FIREBASE_APP_ID        // From .env
BuildConfig.FIREBASE_PROJECT_ID    // From .env
BuildConfig.FIREBASE_STORAGE_BUCKET // From .env
```

### Fallback Initialization
```kotlin
if (apiKey.isNotBlank() && !apiKey.contains("PLACEHOLDER")) {
    // Use Firebase
    isFirebaseEnabled = true
} else {
    // Use local-only mode
    isFirebaseEnabled = false
}
```

## Testing

### Local Testing (No Firebase)
1. Leave `.env` blank or set placeholder values
2. App automatically uses local-only mode
3. Create credentials in `credentials.json`:
   ```json
   [
     {
       "email": "test@example.com",
       "password": "password123",
       "userId": "user_test1"
     }
   ]
   ```
4. Login with test credentials

### Firebase Testing (With Config)
1. Set valid Firebase config in `.env`
2. Run app - should see "Firebase successfully initialized"
3. Sign up with new email - creates Firebase auth account
4. User profile synced to Firestore
5. Login with same credentials next time

### Test Scenarios

**Scenario 1: New User SignUp**
```
1. Go to Sign Up mode
2. Enter: test@college.edu / password123 / password123
3. See success message
4. Auto-navigate to invite code screen
5. User created in Firebase + Firestore
```

**Scenario 2: Existing User Login**
```
1. Stay in Login mode
2. Enter: test@college.edu / password123
3. See success message
4. Auto-navigate to main app
5. User profile loaded from Firestore
```

**Scenario 3: Invalid Credentials**
```
1. Enter wrong password
2. See: "Invalid email or password"
3. Stay on login screen
4. Can retry
```

**Scenario 4: Weak Password**
```
1. SignUp mode
2. Enter password: "short" (5 chars)
3. See: "Password must be at least 6 characters"
4. Cannot proceed until fixed
```

**Scenario 5: Offline Mode**
```
1. Disable WiFi/Mobile
2. Try login with existing local credential
3. Should work (uses credentials.json)
4. When connection restored, syncs with Firestore
```

## Onboarding Flow (After Auth)

### 1. Invite Code Redemption
- User must enter valid invite code
- Codes pre-populated: SAWAARISHARE, INDIANSTUDENTS, WELCOME2026, VOUCHEDCODE
- Marks user as "vouched" tier
- Stored in `invites.json`

### 2. Profile Setup
- **Name** - Full name for display
- **Last Initial** - For privacy (shows as "John D.")
- **Community** - Select university (NEU Boston, ASU Tempe, etc.)
- **Home Area** - Neighborhood/landmark
- **Vehicle Info** - (Optional) Make, model, license plate

### 3. College Email Verification
- User enters college email (e.g., john@northeastern.edu)
- System guesses college name from domain
- Upgrades verification tier if domain matches known colleges
- Stored in `verifiedEmail` and `collegeName` fields

### 4. Profile Picture Upload
- Upload to Firebase Storage
- URL stored in user.avatarUrl
- Displayed on ride listing cards

## Error Messages

| Error | Cause | Fix |
|-------|-------|-----|
| "Email and password cannot be empty" | Missing input | Fill both fields |
| "Please enter a valid email address." | No @ or . in email | Enter valid email |
| "Password must be at least 6 characters" | Too short | Use 6+ chars |
| "Passwords do not match" | Signup password mismatch | Retype correctly |
| "An account with this email already exists." | Email registered | Use different email or login |
| "Invalid email or password." | Wrong credentials | Check email/password |
| "Invalid invite code. Try 'SAWAARISHARE'" | Code doesn't exist | Use valid code |

## Production Checklist

- [ ] Firebase project created at console.firebase.google.com
- [ ] Authentication enabled (Email/Password provider)
- [ ] Firestore database created (US multi-region)
- [ ] Firestore security rules deployed
- [ ] Email verification configured
- [ ] App Check (reCAPTCHA) enabled
- [ ] `.env` configured with Firebase credentials
- [ ] Password reset flow implemented (optional)
- [ ] 2FA optional (stretch goal)
- [ ] User session persistence tested
- [ ] Logout clears all local data
- [ ] Offline mode works without Firebase

## Future Enhancements

### Phase 2
- [ ] Password reset via email link
- [ ] Account deletion with data cleanup
- [ ] Social login (Google, Apple)
- [ ] Phone number verification
- [ ] Biometric authentication (fingerprint)

### Phase 3
- [ ] Two-Factor Authentication (2FA)
- [ ] Device trust / remember this device
- [ ] Session management (active devices)
- [ ] Login history and security log
- [ ] Suspicious activity alerts

## Troubleshooting

**Q: "Firebase initialization bypassed" message on startup**
A: This is normal in local-only mode. Firebase config may be missing. Check `.env` or Secrets Panel.

**Q: Can't create account with valid email**
A: Email already registered. Either:
   - Use different email
   - Clear `credentials.json` and `users.json` to reset local state
   - Check Firestore console if using Firebase

**Q: Can't login with correct password**
A: Ensure email/password match exactly (case-sensitive for email). Spaces in password?

**Q: Password reset link not received**
A: Firebase Email provider may not be configured. Set up in Firebase Console:
   - Go to Authentication → Settings → Authorized Domains
   - Add your app domain to whitelist

**Q: User data not syncing to Firestore**
A: Check Firestore security rules allow writes:
   ```
   allow write: if request.auth.uid == userId;
   ```

---

**Last Updated**: 2026-07-23
**Status**: Email/Password authentication complete, ready for Firebase configuration
