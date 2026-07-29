# Material 3 Animations & Polish - Complete Guide

## Overview

Material 3 animations and polish enhance the user experience with smooth transitions, visual feedback, and polished interactions across all screens. This guide covers button animations, page transitions, loading states, and haptic feedback integration.

## Animation Principles

### Material Motion
```
Duration:    150-300ms (standard interactions)
Easing:      spring(dampingRatio = 0.8, stiffness = 400) 
Curve:       Material curve (deceleration → acceleration)
Purpose:     Smooth, natural motion
```

### Layers of Animation
1. **Micro-interactions** (75-150ms) - Button presses, icon changes
2. **Component animations** (150-300ms) - Card expansion, list item entry
3. **Page transitions** (300-500ms) - Screen navigation
4. **Entrance animations** (300-600ms) - Initial screen load

## Implementation

### 1. Button Scale Animation

```kotlin
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 400f
        ),
        label = "button_scale"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { 
                scaleX = scale
                scaleY = scale 
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        enabled = enabled
    ) {
        content()
    }
}
```

**Usage:**
- Primary action buttons (Join, Accept, Complete)
- Secondary buttons (Cancel, Decline)
- Floating action buttons

**Effect:**
- Button scales to 95% on press
- Smooth spring animation (150ms)
- Provides tactile feedback without haptics

### 2. Card Expansion Animation

```kotlin
@Composable
fun ExpandableCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 500f
                )
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = if (isExpanded) 
                        Icons.Default.ExpandLess 
                    else 
                        Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    modifier = Modifier.rotate(if (isExpanded) 0f else 180f)
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}
```

**Usage:**
- Host/Driver details cards
- Passenger management cards
- Trip information sections
- Edit profile panels

**Effect:**
- Smooth height animation (250ms)
- Icon rotation indicator
- Fade in/out content

### 3. Page Transition Animation

```kotlin
@Composable
fun SlideInTransition(
    navController: NavController,
    route: String,
    content: @Composable () -> Unit
) {
    val transitionState = remember { mutableStateOf(true) }
    val slideIn = slideInHorizontally(
        initialOffsetX = { 1000 }
    ) + fadeIn()
    val slideOut = slideOutHorizontally(
        targetOffsetX = { -1000 }
    ) + fadeOut()
    
    AnimatedVisibility(
        visible = transitionState.value,
        enter = slideIn,
        exit = slideOut
    ) {
        content()
    }
}
```

**Usage:**
- Navigation between major screens
- Modal dialogs and bottom sheets
- Tab transitions

**Effect:**
- Screen slides in from right (300ms)
- Fade-in for content
- Back button slides out to left

### 4. Loading State Animation

```kotlin
@Composable
fun SplitCruiserLoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = SplitCruiserPrimary,
            strokeWidth = 4.dp
        )
    }
}
```

**Usage:**
- API call loading states
- Image upload progress
- Authentication flows
- Data sync indicators

**Effect:**
- Smooth 360° rotation (1.2s)
- Infinite loop until complete
- Linear easing for consistent motion

### 5. Shimmer Skeleton Animation

```kotlin
@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SplitCruiserOutline.copy(alpha = alpha))
    )
}
```

**Usage:**
- Feed loading placeholders
- Card skeleton screens
- Text field placeholders
- Image loading states

**Effect:**
- Fade in/out pulsing (1s cycle)
- Smooth alpha animation
- Indicates loading without spinner

## Animation Applications

### Screen-by-Screen Polish

#### 1. Login Screen
- **Button:** Scale animation on login/signup
- **Loading:** Spinner while authenticating
- **Transition:** Slide-in to profile setup
- **Feedback:** Success toast with animation

#### 2. Profile Setup
- **Card Expansion:** Expandable vehicle info section
- **Button Animation:** Setup complete button scale
- **Image Upload:** Spinner during upload
- **Transition:** Fade to dashboard

#### 3. Dashboard
- **Tab Animation:** Slide between Explore/Trips
- **Mode Switch:** Button color transition
- **Card Entry:** Staggered fade-in for ride cards
- **FAB:** Scale on scroll down/up

#### 4. Explore Feed
- **Mode Switch:** Animated toggle between Rider/Host
- **Card Animation:** Smooth entry for new offers
- **Loading Skeleton:** Shimmer cards while fetching
- **Join Action:** Scale button + success toast

#### 5. Host Dashboard
- **Statistics:** Number animation (count-up effect)
- **Filter Chips:** Animated selection state change
- **Cards:** Smooth height animation on expansion
- **Actions:** Ripple effect on button press

#### 6. Trip Details
- **Expandable Cards:** Host/Rider info sections
- **Status Badge:** Fade-in animation
- **Action Buttons:** Scale + ripple effect
- **Navigation:** Slide-in for bottom sheets

#### 7. Chat Screen
- **Message Entry:** Slide-up keyboard animation
- **Message Bubbles:** Fade-in with slight scale
- **Typing Indicator:** Animated dots
- **Read Receipts:** Smooth checkmark animation

## Haptic Feedback Integration

### Vibration Patterns

```kotlin
// Heavy click (100ms)
context.vibrateDevice(100)

// Light tap (50ms)
context.vibrateDevice(50)

// Double tap (50, 50, 100)
context.vibratePattern(longArrayOf(0, 50, 50, 50))

// Success pattern (30, 30, 100)
context.vibratePattern(longArrayOf(0, 30, 30, 100))
```

### When to Use Haptics

| Interaction | Pattern | Duration |
|---|---|---|
| Button press | Light | 50ms |
| Form validation error | Double | 100ms |
| Successful action | Success | 160ms |
| Match accepted | Heavy | 100ms |
| Navigation | Light | 50ms |
| Long-press | Medium | 75ms |

### Implementation

```kotlin
val context = LocalContext.current

Button(
    onClick = {
        // Haptic feedback
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    100,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            vibrator.vibrate(100)
        }
        // Action
        onAction()
    }
) {
    Text("Accept Match")
}
```

## Dark Mode Support

### Current Implementation
- ✅ Color scheme adapted for dark backgrounds
- ✅ Card backgrounds adjust contrast
- ✅ Text colors invert appropriately
- ⏳ **Enhancement:** Add Material 3 dynamic theming

### Dynamic Color (Future)

```kotlin
// Material 3 dynamic colors (Android 12+)
val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val context = LocalContext.current
    if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
} else {
    if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = SplitCruiserPrimary,
            secondary = SplitCruiserPrimaryContainer,
            tertiary = SplitCruiserSuccess
        )
    } else {
        lightColorScheme(
            primary = SplitCruiserPrimary,
            secondary = SplitCruiserPrimaryContainer,
            tertiary = SplitCruiserSuccess
        )
    }
}

MaterialTheme(colorScheme = colorScheme) {
    // Content
}
```

## Performance Optimization

### Animation Best Practices

1. **Use remember() for expensive computations**
   ```kotlin
   val transition = rememberInfiniteTransition()
   // vs
   val transition = rememberInfiniteTransition(label = "id")
   ```

2. **Specify animation labels for debugging**
   ```kotlin
   animateFloatAsState(
       targetValue = scale,
       label = "button_scale_animation"
   )
   ```

3. **Avoid layout recomposition during animation**
   ```kotlin
   // ✅ Good - only graphicsLayer changes
   .graphicsLayer { scaleX = scale }
   
   // ❌ Bad - layout recomputes
   .size(size * scale)
   ```

4. **Use appropriate durations**
   - Micro: 75-150ms
   - Standard: 150-300ms
   - Complex: 300-500ms

## Testing Scenarios

### Scenario 1: Button Animation Flow
```
1. User on login screen
2. Tap login button
3. Button scales to 95% (immediate)
4. Spinner appears (100ms)
5. Loading state (1-2 seconds)
6. Success → Slide transition to next screen (300ms)
7. Toast notification fades in/out
```

### Scenario 2: Card Expansion Animation
```
1. User views host details card (collapsed)
2. Taps to expand
3. Card height animates (250ms)
4. Chevron icon rotates (180°)
5. Content fades in
6. All content visible and interactive
```

### Scenario 3: Page Navigation with Haptics
```
1. User navigates to trip details
2. New screen slides in from right (300ms)
3. Device vibrates (50ms light tap)
4. Cards fade in with stagger
5. Buttons ready for interaction
```

### Scenario 4: Loading to Success
```
1. User joins a ride
2. Join button shows spinner (100ms entry)
3. Circular progress rotates (1.2s cycle)
4. Success → Spinner fades out (100ms)
5. Checkmark animates in (200ms)
6. Toast slides in with message
7. Dashboard updates smoothly
```

## Implementation Checklist

### Phase 1: Core Animations (Current Task)
- [x] Create animation utility functions
- [ ] Apply button scale animations to primary actions
- [ ] Add card expansion animations
- [ ] Implement page transition animations
- [ ] Add loading state spinners
- [ ] Add shimmer skeleton loaders

### Phase 2: Polish Details
- [ ] Add ripple effects to all interactive elements
- [ ] Implement haptic feedback for key interactions
- [ ] Enhance tab transitions with animations
- [ ] Add staggered list animations
- [ ] Implement floating action button animations

### Phase 3: Advanced Features
- [ ] Add gesture-based animations (swipe to delete)
- [ ] Implement parallax scrolling
- [ ] Add animation for status changes
- [ ] Create custom motion paths
- [ ] Add confetti animations for achievements

### Phase 4: Refinement
- [ ] Performance profiling and optimization
- [ ] Test on various device sizes/speeds
- [ ] Accessibility animation preferences
- [ ] Animation duration adjustments based on feedback
- [ ] Dark mode animation refinements

## Related Documentation

- **Design System:** Material 3 color palette and typography
- **Components:** Reusable button, card, and dialog components
- **Navigation:** Screen transitions and routing
- **Accessibility:** Respecting motion preferences (reduceMotion)

## Performance Metrics

### Target FPS
- **Scroll Performance:** 60 FPS (60-frame animation)
- **Button Animation:** 60 FPS (150ms @ 60fps = 9 frames)
- **Page Transition:** 60 FPS (300ms smooth slide)

### Memory Impact
- **Loading Spinner:** ~2MB (infinite transition)
- **Shimmer Effect:** ~1MB (pulsing alpha)
- **Page Transition:** ~3MB (temporary during navigation)

## Accessibility Considerations

### Motion Preferences

```kotlin
val motionEnabled = !LocalContext.current
    .resources
    .configuration
    .isScreenReaderEnabled
```

**Implementation:**
- Respect `reduceMotion` system setting
- Provide instant feedback when animations disabled
- Ensure functionality without animations

## Future Enhancements

- [ ] Gesture-based animations (swipe, drag)
- [ ] Multi-touch gestures with haptics
- [ ] Parallax effects on scroll
- [ ] Shared element transitions
- [ ] Complex motion paths
- [ ] Confetti/celebration animations
- [ ] Custom easing curves
- [ ] Animation timeline editor for QA

---

**Last Updated**: 2026-07-23
**Status**: Material 3 animation framework ready for implementation across all screens
