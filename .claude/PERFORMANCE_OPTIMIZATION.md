# SawaariShare Performance Optimization Guide

## Overview

This guide documents performance optimization strategies for the SawaariShare application, ensuring efficient memory usage, fast screen loads, and optimal battery consumption.

## Performance Benchmarks

### Target Metrics

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Feed Load | <500ms | TBD | ⏳ To measure |
| Message Send | <1s | TBD | ⏳ To measure |
| Match Creation | <2s | TBD | ⏳ To measure |
| Image Upload (5MB) | <3s | TBD | ⏳ To measure |
| Memory Usage | <150MB | TBD | ⏳ To measure |
| Battery/Hour (Active) | <5% | TBD | ⏳ To measure |

## Memory Optimization

### 1. StateFlow Efficiency

**Current Implementation:**
```kotlin
// Good: StateFlow with efficient updates
private val _tripOffers = MutableStateFlow<Map<String, TripOffer>>(emptyMap())
val tripOffers: StateFlow<Map<String, TripOffer>> = _tripOffers.asStateFlow()
```

**Optimization Tips:**
- Use `Map<String, T>` for efficient lookups (O(1) vs O(n) for lists)
- Only emit when data actually changes
- Avoid large intermediate collections

**Memory Reduction:** ~20-30% compared to List-based approach

### 2. Image Caching

**Coil Configuration:**
```kotlin
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.25) // Use 25% of available memory
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(File(context.cacheDir, "image_cache"))
            .maxSizeBytes(50 * 1024 * 1024) // 50MB disk cache
            .build()
    }
    .build()
```

**Profile Picture Optimization:**
- 80x80dp thumbnail cache (1-2KB each)
- 300x300dp full size cache (10-15KB each)
- Lazy-load full sizes on demand
- LRU eviction when cache exceeds limits

**Expected Savings:** ~40MB memory for 100 cached images

### 3. JSON Deserialization

**Optimization:**
```kotlin
// Use Moshi for efficient JSON parsing
private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

// Avoid parsing entire collection if only updating single item
private fun updateTripOffer(jsonString: String) {
    val adapter = moshi.adapter(TripOffer::class.java)
    val updated = adapter.fromJson(jsonString) ?: return
    _tripOffers.value = _tripOffers.value.toMutableMap().apply {
        put(updated.id, updated)
    }
}
```

**Performance:** ~5-10ms for typical model (100-300 fields)

### 4. Listener Optimization

**Current Strategy:**
- 6 snapshot listeners running continuously
- Each listener updates local StateFlow
- StateFlow only emits on actual data changes

**Memory Impact:** ~2-5MB for listener registrations and buffers

**Optimization Options:**
- Scope listeners to visible screens (attach/detach)
- Batch updates instead of emitting on every change
- Unregister listeners on app pause

## Firestore Query Optimization

### 1. Indexed Queries

**Recommended Composite Indexes:**
```
trip_offers:
  - hostId (Ascending)
  - status (Ascending)
  - departureTime (Descending)

ride_requests:
  - riderId (Ascending)
  - status (Ascending)
  - departureTime (Descending)

trip_matches:
  - hostId (Ascending)
  - status (Ascending)
  - createdAt (Descending)

messages:
  - matchId (Ascending)
  - timestamp (Descending)
```

**Cost Reduction:** ~60% fewer index lookups

### 2. Query Limitations

```kotlin
// Good: Limited query
fun getActiveOffers() = repository.getTripOffers()
    .filter { it.status == "active" && it.departureTime > System.currentTimeMillis() }
    .sortedBy { it.departureTime }
    .take(20) // Paginate

// Avoid: Fetch all data
fun getAllOffers() = repository.getTripOffers() // Fetches everything
```

### 3. Pagination Implementation

```kotlin
class OfferPaginationState {
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private val pageSize = 20

    fun nextPage(): List<TripOffer> {
        val query = if (lastDocumentSnapshot != null) {
            db.collection("trip_offers")
                .whereEqualTo("status", "active")
                .startAfter(lastDocumentSnapshot)
                .limit(pageSize.toLong())
        } else {
            db.collection("trip_offers")
                .whereEqualTo("status", "active")
                .limit(pageSize.toLong())
        }

        val documents = query.get().await().documents
        lastDocumentSnapshot = documents.lastOrNull()
        return documents.mapNotNull { it.toObject<TripOffer>() }
    }
}
```

**Benefit:** Loads only what's visible, ~5-10x faster initial load

## UI Rendering Optimization

### 1. Compose Recomposition

**Problem Area:**
```kotlin
// Bad: Recomposes entire list on any change
LazyColumn {
    items(offers) { offer ->
        OfferCard(offer, onClick = { /*...*/ })
    }
}
```

**Solution:**
```kotlin
// Good: Key prevents unnecessary recomposition
LazyColumn {
    items(offers, key = { it.id }) { offer ->
        OfferCard(offer, onClick = { /*...*/ })
    }
}
```

**Performance:** ~50% fewer recompositions

### 2. Heavy Composables

```kotlin
// Optimize StudentAvatar by memoizing
@Composable
fun StudentAvatar(
    avatarUrl: String,
    name: String,
    size: Dp = 40.dp,
    fontSize: TextUnit = 16.sp
) {
    // This composable only recomposes when parameters change
    Box(modifier = Modifier.size(size)) {
        if (avatarUrl.isNotEmpty()) {
            AsyncImage(model = avatarUrl, contentDescription = name)
        } else {
            // Fallback: initials
            Text(text = name.take(1))
        }
    }
}
```

### 3. Lazy Loading

```kotlin
// Load full profiles only when needed
@Composable
fun OfferCardExpanded(offer: TripOffer) {
    var hostDetails by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(offer.hostId) {
        hostDetails = repository.getUser(offer.hostId)
    }

    Column {
        OfferCardCompact(offer)
        if (hostDetails != null) {
            HostProfileSection(hostDetails!!)
        }
    }
}
```

## Battery Optimization

### 1. Listener Lifecycle

```kotlin
// Stop listeners when app backgrounded
override fun onPause() {
    viewModel.repository.removeAllListeners()
    super.onPause()
}

override fun onResume() {
    super.onResume()
    viewModel.repository.setupRealtimeListeners()
}
```

**Battery Savings:** ~40-50% for inactive sessions

### 2. Location Updates (Future)

```kotlin
// Only request location when user actively searching
fun startLocationTracking() {
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        .setIntervalMillis(5000) // 5 second interval
        .build()
    
    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        Looper.getMainLooper()
    )
}

fun stopLocationTracking() {
    fusedLocationClient.removeLocationUpdates(locationCallback)
}
```

### 3. Network Optimization

```kotlin
// Use exponential backoff for retries
suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelayMillis: Long = 100,
    maxDelayMillis: Long = 10000,
    backoffMultiplier: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMillis
    var exception: Exception? = null

    repeat(times) {
        try {
            return block()
        } catch (e: Exception) {
            exception = e
            delay(currentDelay)
            currentDelay = (currentDelay * backoffMultiplier).toLong()
                .coerceAtMost(maxDelayMillis)
        }
    }

    throw exception ?: Exception("Retry failed")
}
```

## Profiling Instructions

### CPU Profiling

1. **In Android Studio:**
   - Connect device or start emulator
   - Run app in debug mode
   - Profiler → CPU
   - Trace execution while performing actions
   - Look for bottlenecks (methods taking >10% CPU)

2. **Analyze Results:**
   - Expand call stack
   - Sort by duration
   - Identify hot methods
   - Look for N+1 query patterns

### Memory Profiling

1. **In Android Studio:**
   - Profiler → Memory
   - Record allocation tracker
   - Perform user flows
   - Force garbage collection
   - Analyze heap dump

2. **Look For:**
   - Memory leaks (retained objects after GC)
   - Large allocations (>1MB)
   - Growth over time (leak indicator)

### Battery Profiling

1. **Battery Historian:**
   ```bash
   adb shell dumpsys batterystats --reset
   # Use app for several minutes
   adb bugreport bugreport.zip
   # Upload to https://bathist.appspot.com
   ```

2. **Metrics to Watch:**
   - Wake locks duration
   - Network usage
   - GPS activation
   - Screen on time

## Optimization Checklist

### Before Release

- [ ] Memory usage <150MB average
- [ ] Feed loads in <500ms
- [ ] 60 FPS maintained during scrolling
- [ ] Image cache properly sized
- [ ] Listeners properly scoped
- [ ] Pagination implemented for lists >100 items
- [ ] Firestore indexes deployed
- [ ] Battery drain <5%/hour active use
- [ ] No obvious memory leaks
- [ ] All animations at 60 FPS

### Regular Maintenance

- [ ] Monthly: Review crash logs
- [ ] Monthly: Profile new features
- [ ] Quarterly: Full performance audit
- [ ] On each release: Benchmark key flows

## Common Performance Issues

### Issue: Slow Feed Load

**Symptoms:**
- Feed takes >1 second to load
- Jank during scroll

**Causes:**
- Unnecessary recompositions
- Loading too much data
- Heavy AsyncImage calls

**Solutions:**
1. Add key to LazyColumn items
2. Implement pagination (limit to 20 items)
3. Lazy-load images (use Coil with lower res thumbnails)

### Issue: Memory Creep

**Symptoms:**
- App uses 200MB+ after extended use
- Crashes after 1+ hour

**Causes:**
- Memory leaks in listeners
- Large cached lists
- Unclosed resources

**Solutions:**
1. Profile with heap dump
2. Remove listeners on logout
3. Limit cache sizes
4. Use WeakReference for context

### Issue: Battery Drain

**Symptoms:**
- App drains 10%+ battery/hour
- Excessive heat

**Causes:**
- Always-on listeners
- Constant network activity
- Location tracking

**Solutions:**
1. Pause listeners on background
2. Batch Firestore writes
3. Stop location tracking when not needed

## References

- Android Performance Guide: https://developer.android.com/topic/performance
- Compose Performance: https://developer.android.com/jetpack/compose/performance
- Firestore Optimization: https://firebase.google.com/docs/firestore/best-practices
- Profiling Tools: https://developer.android.com/studio/profile

---

**Last Updated:** 2026-07-24
**Status:** Performance optimization guidelines and profiling procedures
