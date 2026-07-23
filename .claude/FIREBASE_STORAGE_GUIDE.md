# Firebase Storage & Profile Pictures - Complete Guide

## Overview

Firebase Storage handles profile picture uploads for the SawaariShare app, enabling users to add profile photos during onboarding or profile editing. Images are optimized for web delivery with CDN caching and fallback to emoji avatars if no photo is available.

## Architecture

### Storage Structure

```
gs://sawaari-share-bucket/
├── profile_pictures/
│   ├── {userId}.jpg          → User's profile picture (512x512 optimized)
│   ├── {userId}.jpg-metadata → Image metadata (size, upload date)
│   └── ...
```

### Data Flow

```
User selects image
    ↓
Resize image (512x512, JPEG 85% quality)
    ↓
Upload to Firebase Storage
    ↓
Get download URL
    ↓
Update user profile in Firestore
    ↓
Update local StateFlow
    ↓
Display in StudentAvatar component
```

## Implementation

### 1. Firebase Storage Methods (SawaariRepository.kt)

#### Image Resizing
```kotlin
suspend fun resizeImage(
    inputFile: File, 
    maxWidth: Int = 512, 
    maxHeight: Int = 512
): Result<File>
```

**Process:**
1. Decode image file using BitmapFactory
2. Calculate aspect ratio
3. Scale to max dimensions while preserving ratio
4. Compress to JPEG with 85% quality
5. Save to cache directory
6. Return resized file path

**Output:** 512×512 px JPEG (~50-80KB typical)

#### Profile Picture Upload
```kotlin
suspend fun uploadProfilePicture(
    userId: String, 
    imageFile: File
): Result<String>
```

**Process:**
1. Check Firebase availability
2. Create storage reference: `profile_pictures/{userId}.jpg`
3. Upload file using `putFile()`
4. Wait for upload to complete
5. Get download URL via `downloadUrl`
6. Update Firestore user document with `avatarUrl`
7. Update local StateFlow and JSON cache
8. Return download URL string

**Error Handling:**
- If Firebase unavailable: return local file path
- If upload fails: log error, return failure result
- If Firestore update fails: rollback storage upload

#### Profile Picture Deletion
```kotlin
suspend fun deleteProfilePicture(userId: String): Result<Unit>
```

**Process:**
1. Delete file from Storage
2. Clear `avatarUrl` from Firestore user document
3. Update local cache
4. Return success/failure result

### 2. Profile Setup UI (ProfileSetupScreen)

**UI Elements:**
- Profile Picture Card (optional section)
- Image preview (StudentAvatar component)
- Icon: Image + label
- Optional note: "(Optional - can be added later)"

**State Variables:**
```kotlin
var selectedAvatarUrl by remember { mutableStateOf("") }
var uploadingProfilePicture by remember { mutableStateOf(false) }
```

**Future Enhancement:** Add image picker integration
- Android `startActivityForResult` with image picker intent
- Or use third-party image picker library

### 3. Avatar Display (StudentAvatar Component)

**Handles Three Cases:**

1. **HTTP URL (uploaded photo)**
   ```kotlin
   AsyncImage(
       model = avatarUrl,
       contentDescription = "Profile Picture",
       contentScale = ContentScale.Crop,
       error = painterResource(R.drawable.ic_launcher_foreground)
   )
   ```
   - Uses Coil library for lazy loading
   - Shows error drawable if load fails

2. **Preset Emoji Avatar**
   ```kotlin
   when (avatarUrl) {
       "preset_grad" -> "🎓"
       "preset_driver" -> "🚗"
       "preset_tech" -> "💻"
       "preset_explorer" -> "🎒"
       "preset_star" -> "⭐"
       "preset_globe" -> "🌐"
   }
   ```

3. **Default Initial Avatar**
   ```kotlin
   Text(
       text = name.take(1).uppercase(),
       color = Color.White,
       fontWeight = FontWeight.Black,
       fontSize = fontSize
   )
   ```
   - Uses first letter of user's name
   - Displayed on gradient background

## Data Models

### User Profile (Updated)
```kotlin
data class User(
    val id: String,
    val name: String,
    val lastInitial: String,
    val email: String,
    val verifiedEmail: String? = null,
    val collegeName: String,
    val collegeEmail: String,
    val homeArea: String,
    val verifiedTier: String = "guest",  // "vouched" or "guest"
    val avatarUrl: String = "",         // NEW: URL to profile picture
    val rating: Float = 5.0f,
    val ratingCount: Int = 0,
    val noShowCount: Int = 0,
    val completedTrips: Int = 0,
    val vehicle: Vehicle? = null,
    val blockedUsers: List<String> = emptyList(),
    val phoneNumber: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
```

### Storage URL Format

**Example Download URL:**
```
https://firebasestorage.googleapis.com/v0/b/
sawaari-share-bucket.appspot.com/o/
profile_pictures%2Fuser_123.jpg?alt=media&token=abc123xyz
```

## Upload Process Flow

### Step 1: User Selects Image
- User taps profile picture card
- Image picker dialog opens (future enhancement)
- User selects image from gallery
- Image path stored in `selectedAvatarUrl`

### Step 2: Resize Image
```kotlin
val resizeResult = repository.resizeImage(imageFile)
when (resizeResult) {
    is Result.Success -> {
        val resizedFile = resizeResult.value
        // Proceed to upload
    }
    is Result.Failure -> {
        // Show error toast
    }
}
```

### Step 3: Upload to Storage
```kotlin
viewModel.uploadProfilePicture(
    userId = currentUser?.id ?: "",
    imageFile = resizedFile,
    callback = { success ->
        if (success) {
            showToast("Profile picture updated!")
            navController.navigate("dashboard")
        } else {
            showToast("Upload failed - try again")
        }
    }
)
```

### Step 4: Update UI
- Profile picture appears in all screens
- Reflected in Explore feed (user avatar)
- Shown in Host Dashboard
- Visible in chat messages
- Displayed in trip details

## Image Optimization

### Resize Specifications
- **Max Dimensions:** 512×512 pixels
- **Format:** JPEG
- **Quality:** 85% (balanced quality/size)
- **Typical Size:** 50-80 KB per image

### Aspect Ratio Preservation
```
Original: 1920×1080 (16:9 landscape)
Resized: 512×288 (16:9 maintained)

Original: 1080×1080 (1:1 square)
Resized: 512×512 (1:1 maintained)
```

### CDN Caching
Firebase Storage serves through CDN:
- **Cache Duration:** 3600 seconds (1 hour) default
- **Global Edge Locations:** Cached worldwide
- **Benefits:** Fast loads, reduced bandwidth costs

## Error Handling

### Upload Failures
```
Scenario: Network disconnection during upload
→ Upload task fails
→ User sees error toast
→ Can retry immediately
→ Old profile picture unchanged

Scenario: File too large (>10MB)
→ Resize fails
→ Error logged
→ User notified
→ Can try different image

Scenario: Firebase Auth expired
→ Upload fails with auth error
→ User logged out
→ Redirected to login
```

### Fallback Behaviors
- **No Firebase:** Use local file path
- **Upload fails:** Keep existing avatar
- **Download URL fails:** Use emoji/initial avatar
- **Storage unavailable:** Show placeholder

## Testing Scenarios

### Scenario 1: Upload Profile Picture During Setup
```
1. User in ProfileSetupScreen
2. Fills name: "Amit"
3. Selects profile picture: amit_photo.jpg (2MB)
4. [Continue] button pressed
5. App resizes to 512×512 (75KB JPEG)
6. Uploads to gs://bucket/profile_pictures/user_123.jpg
7. Gets download URL from Firebase
8. Updates Firestore user document
9. Navigation to dashboard
10. Profile picture visible in header
```

### Scenario 2: Update Profile Picture from Edit Dialog
```
1. User in dashboard (already has profile picture)
2. [Edit Profile] → EditProfileDialog opens
3. Current avatar shown as StudentAvatar
4. [Change Photo] button
5. Image picker opens
6. Selects new_photo.jpg
7. [Save] → Upload new image
8. Old profile_pictures/user_123.jpg deleted (optional)
9. New image URL set
10. UI updates immediately
11. Old image URL replaced everywhere
```

### Scenario 3: No Profile Picture (Emoji Avatar)
```
1. User completes setup without photo
2. avatarUrl = "" (empty)
3. User profile created
4. StudentAvatar shows first initial: "A"
5. Gradient background (blue-indigo)
6. Shown in all user-facing screens
7. Later: User adds photo
8. Avatar switches to actual photo
```

### Scenario 4: Offline Upload Attempt
```
1. User selects image
2. Network disconnected
3. User taps [Continue]
4. Upload fails (network error)
5. Error dialog shown
6. User reconnects network
7. Retries upload
8. Succeeds and continues
```

## Security Considerations

### Access Control
- **Public Reads:** Download URL includes token
- **Authenticated Writes:** Firebase rules require auth
- **User Privacy:** Only own profile picture readable

### Storage Rules (Future)
```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /profile_pictures/{userId}.jpg {
      allow read: if true;  // Public read
      allow write: if request.auth.uid == userId;  // Own only
    }
  }
}
```

### Image Validation
- Verify file is actual image (JPEG/PNG)
- Check file size < 10MB
- Validate dimensions > 100×100

## Storage Quotas & Pricing

### Free Tier (Firebase Spark Plan)
- **Storage:** 5 GB total
- **Downloads:** 1 GB/day
- **Uploads:** Limited

### Paid Tier (Firebase Blaze Plan)
- **Storage:** Pay per GB (~$0.18/GB)
- **Downloads:** Pay per GB (~$0.12/GB)
- **Uploads:** Free

### Optimization Tips
- Image resizing before upload (saves space)
- JPEG compression (saves ~30% vs PNG)
- CDN caching (reduces downloads)
- Delete old avatars when replacing

## Future Enhancements

### Phase 2: Advanced Features
- [ ] Image cropper UI (before upload)
- [ ] Multiple photos (gallery)
- [ ] Photo validation (not blurry, etc.)
- [ ] Automatic format detection
- [ ] WebP format for better compression

### Phase 3: Advanced Optimization
- [ ] Responsive images (multiple sizes)
- [ ] Thumbnail generation
- [ ] Face detection/verification
- [ ] Blur detection (quality check)

### Phase 4: Social Features
- [ ] Photo album/gallery
- [ ] Trip photo uploads
- [ ] Community highlights
- [ ] Photo rating system

## API Methods Used

```kotlin
// Repository methods
repository.resizeImage(inputFile)
repository.uploadProfilePicture(userId, imageFile)
repository.deleteProfilePicture(userId)

// Firebase Storage API
firebaseStorage?.reference?.child(path)
storageRef.putFile(uri)
storageRef.downloadUrl
storageRef.delete()

// ViewModel integration
viewModel.uploadProfilePicture(userId, imageFile, callback)
viewModel.deleteProfilePicture(userId, callback)

// Firestore integration
firebaseFirestore?.collection("users")?.document(userId)
    ?.update("avatarUrl", url)
```

## Dependencies

- **Firebase Storage:** `com.google.firebase:firebase-storage-ktx`
- **Image Loading:** `io.coil-kt:coil-compose` (already included)
- **Coroutines:** `org.jetbrains.kotlinx:kotlinx-coroutines` (already included)

## Related Screens

- **Profile Setup** (`profile_setup`) - Initial upload
- **Edit Profile** (`profile`) - Update photo
- **Explore Feed** (`dashboard`) - Show avatars
- **Host Dashboard** (`host_dashboard`) - Driver avatars
- **Chat** (`chat/{matchId}`) - Profile pictures in messages
- **Trip Details** (`trip_detail/{id}/{type}`) - Driver/rider photos

---

**Last Updated**: 2026-07-23
**Status**: Firebase Storage integrated with image resizing and CDN delivery ready for profile pictures
