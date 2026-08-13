# Split Cruiser design system

Three things live here: the tokens both apps read, the voice their copy is written in, and the
parity checklist that stops the two platforms drifting apart. All three came out of the
2026-07 UI/UX audit.

Unlike most of `.claude/`, this file describes the code as it currently is. If it disagrees with
the code, the code is right and this file is a bug.

---

## 1. Tokens

**`shared/src/commonMain/.../ui/theme/Color.kt` is the only place a colour is written down.**
It is plain `0xAARRGGBB` longs, so both platforms can read it:

- Android wraps them in `app/.../ui/theme/Color.kt` (`SplitCruiserPrimary = Color(Tokens.Primary)`)
  and builds a real `ColorScheme` from them in `Theme.kt`.
- iOS wraps them in `iosApp/iosApp/Theme.swift` (`Brand.primary`).

Adding a colour means adding it there, not inline at the call site.

| Token | Value | What it's for |
|---|---|---|
| `Surface` | `#F8F9FF` | App background |
| `SurfaceCard` | `#FFFFFF` | Cards, sheets, dialogs |
| `SurfaceMuted` | `#EEF1FF` | Inset rows, tinted chips |
| `SurfaceTrack` | `#E1E2EC` | The track behind a segmented control |
| `Primary` | `#0061A4` | Buttons, active tabs, links |
| `PrimaryContainer` | `#D1E4FF` | The tonal container paired with `Primary` |
| `OnPrimary` / `OnPrimaryContainer` | `#FFFFFF` / `#001D36` | Text drawn on each of those |
| `Success` | `#10B981` | Active rides, confirmations |
| `Danger` | `#EF4444` | Cancel, decline, log out |
| `Info` | `#3B82F6` | Completed / matched |
| `Warning` | `#EAB308` | Star ratings, soft warnings |
| `Accent` | `#E91E63` | The women-only safety filter |
| `TextPrimary` / `TextSecondary` | `#0F172A` / `#64748B` | Body and supporting text |
| `Outline` | `#E2E8F0` | Hairlines, card borders, dividers |

### The names used to lie

Until 2026-07 these were called `SplitCruiserSaffron` (a blue), `SplitCruiserIndigo` (a pale
near-white blue) and `SplitCruiserDarkBg` (a near-white). A Material Theme Builder blue palette had
been pasted into variable names left over from an earlier brand.

That is not a cosmetic problem. `ProfileScreen` set its identity card to `SplitCruiserCardBg` —
white — and then drew the display name in `Color.White`. Invisible text, on the one screen every
user visits. Eleven other `Color.White` calls on the same screen had the same problem, as did the
Explore search field, and three leftover dark-theme surfaces (`#252D3C`, `#202634`, `#1E2430`)
were being drawn under near-black text.

**If you add a token, name it for what it renders as.** If the brand ever genuinely moves to
saffron and indigo, change the hex values — do not reintroduce a name that argues with its pixel.

### Scale

`SplitCruiserScale` in the same shared file, wrapped as `SplitCruiserSpacing` / `SplitCruiserRadius`
/ `SplitCruiserTextSize` on Android and `BrandScale` on iOS.

- **Spacing**: a 4-point grid — 4, 8, 12, 16, 24, 32.
- **Radius**: one per family of control. Chips and badges `8`, buttons *and* text fields `12`,
  cards and sheets `16`, pills/FABs/avatars fully rounded. Text fields used to be 14 while the
  button beneath them was 12, for no reason anyone could name.
- **Type**: eyebrow 11, caption 12, body 14, title 16, headline 20.

New code uses the scale. Existing magic numbers get replaced as screens are touched, not in a
separate sweep.

### Shared components

Extract before you copy. These already exist:

| Android | iOS | Was duplicated in |
|---|---|---|
| `RouteIndicator` | `RouteIndicator` | 7 places, each with its own dot size and rail height |
| `StatusBadge` / `statusColor` | `StatusBadge` | 5 places, each with its own `when (status)` |
| `CardEyebrow`, `CardStat` | `DetailRow` | every ride card |
| `SplitCruiserEmptyState` | `BrandEmptyState` | — (already good) |
| `FormSection` | SwiftUI `Section` | — |
| `RideSchedule` (in `:shared`) | same object | 3 places, and two of them disagreed |
| `SplitCruiserAvatars` (in `:shared`) | same object | 3 places — the key list was written out twice on Android alone |
| `PickupDetailRow` | `detailRow` | proposal and confirmation cards, which described the same terms differently |
| `PlaceRanking` (in `:shared`) | same object | — the two platforms would otherwise sort suggestions differently |

`RideSchedule` answers "is this ride still on someone's schedule, or is it history?" — and `"full"`
is the case that catches people out. A ride whose last seat has gone is still very much happening,
but Android's trips tab filtered on `status == "active"` alone, so a fully-booked ride dropped out
of "Rides you're hosting" and reappeared under "Past rides", while the host dashboard used its own
copy of the rule and got it right. Every ride a driver accepts directly is `"full"` from the moment
they accept it, so both platforms now read the one object.

`statusColor` matters more than it looks: with five independent `when (status)` blocks, a status
one card handled fell through another's `else`. One function means they cannot disagree.

---

## 2. Voice

**Plain, warm, specific.** Say what the button does. Assume the reader is busy and slightly
anxious about getting into a stranger's car.

Three adjectives: **direct**, **warm**, **concrete**.

| Do | Don't |
|---|---|
| "Post ride offer" | "Broadcast Ride Offer" |
| "Finish setup" | "Launch Split Cruiser" |
| "Past rides" | "Past Rides & Reference History" |
| "You're in! Chat here to sort out the pickup spot." | "Trip request accepted by the host. You can now chat and coordinate the cash split in person." |
| "Please enter a contact number so riders can reach you" | "This field is required" |
| "Hidden from your feed and can't message you" | "User ID: aQ3xR9…" |
| "Accept and confirm" | "Accept & Confirm" |
| "Use my location" / "Nearest first" | "Use GPS (Nominatim)" / "OPENSTREETMAP PHOTON SUGGESTIONS" |

Three rules that cover most of it:

1. **Say why, not just what.** A required field explains what it unlocks. The cost card on the
   trip detail screen is the model: how much, why that number, and how it's actually paid.
2. **Never show an internal identifier.** Firebase uids are for the backend. The rating form used
   to ask a user to *type one in*; it now picks from their own match history.
3. **Match the register across platforms.** The same action gets the same words on Android and
   iOS. "Post ride offer" both places, not "Broadcast" on one.

**Loading messages name their action.** The global overlay is parameterised
(`MainViewModel.loadingMessage`); "Securing your ride…" is for reserving a seat, not for logging
in or blocking someone. The neutral default is "Just a moment…".

### Is it a fare or a cost split?

It's a cost split. The host's total is "Chipped in", not "Revenue"; a rider's share is a
"suggested contribution", paid in cash in person. Keep that framing everywhere the number appears.

A pickup proposal is where the two sides actually settle on the number: the proposal labels it
"Your share", the confirmation labels it "Agreed share" and adds "Both of you have agreed to this
amount. Pay in cash when you meet." Confirming writes it to `TripMatch.contribution`, so the chat
and the rest of the app cannot disagree about the price.

---

## 3. Cross-platform parity checklist

**Nothing keeps the two apps in sync automatically.** The backend is shared; every screen exists
twice. `.claude/ui-migration-strategy.md` scoped Compose Multiplatform and deliberately deferred
it, so until that changes this checklist is the mechanism.

**Run through this at PR time whenever a PR touches user-facing behaviour.**

### Screens that must exist on both platforms

| Screen | Android | iOS |
|---|---|---|
| Login / sign up | `EmailPasswordLoginScreen` | `LoginScreen` (`AuthScreens.swift`) |
| Onboarding | `ProfileSetupScreen` | `ProfileSetupScreen` (`AuthScreens.swift`) |
| Browse rides | `DashboardScreen` (Explore) | `ExploreFeed` |
| Ride detail | `TripDetailScreen` | `TripDetailScreen` (`DetailScreens.swift`) |
| Post offer / request | `PostOfferScreen` / `PostRequestScreen` | `PostOfferScreen` / `PostRequestScreen` (`PostRideForms.swift`) |
| My rides | `DashboardScreen` (Trips) | `TripsTab` (`ExploreFeed.swift`) |
| Matches | Explore's "Active Trip Coordination" | `MatchesScreen` (`DetailScreens.swift`) |
| Chat | `ChatScreen` | `ChatView` |
| Propose a pickup | `ProposePickupDialog` | `ProposePickupSheet` (`ChatView.swift`) |
| Address search | `LocationAutoCompleteTextField` | `LocationAutocompleteField` |
| Profile | `ProfileScreen` | `ProfileScreen` (`ProfileScreens.swift`) |
| Ratings | `ProfileScreen`'s rating card | `RatingsCard` (`ProfileScreens.swift`) |
| Profile editing | `EditProfileDialog` | `EditProfileSheet` (`ProfileScreens.swift`) |
| Blocked users | `BlockedListScreen` | `BlockedListScreen` (`ProfileScreens.swift`) |
| Host analytics | `HostDashboard` | `HostDashboardScreen` (`ProfileScreens.swift`) |

**One deliberate structural difference**, recorded rather than accidental:

- Android's bottom bar sends "Chats" straight into `userMatches.first()` and has no list behind
  it, so every other conversation is unreachable from the bar. iOS keeps a `MatchesScreen` list.

(Android's dead `host_dashboard` route used to be the second entry here. The Profile screen's
safety section now links to it on both platforms.)

### Questions to answer before merging

- [ ] Does this change a screen in the table above? Then it changes **both** files, or the PR says
      in one sentence why not.
- [ ] Does it add or remove a field collected at onboarding? Every field Android collects must be
      collected on iOS, or something downstream degrades silently. (This is exactly how an Android
      rider ended up looking at a blank phone row for an iOS-onboarded host.)
- [ ] Does it add user-visible copy? Same words on both platforms — see §2.
- [ ] Does it add a colour, spacing value or radius? It goes in the shared tokens, so both
      platforms get it.
- [ ] Does it add developer instrumentation to a user-facing screen? Gate it: `BuildConfig.DEBUG`
      on Android, `#if DEBUG` on iOS.

### Known, accepted gaps

Not everything is at parity, and that is fine as long as it is deliberate:

- **Google sign-in is Android-only.** The token exchange is shared, but only Android acquires a
  Google ID token (Credential Manager). iOS would need `ASWebAuthenticationSession` and a URL
  scheme in the generated Xcode project.
(The fake "use my location" chip used to be the second entry here — Android-only, with
Northeastern's campus hardcoded into it. Both platforms now read a real fix and the chip is at
parity.)

Host analytics, blocked-user management, profile editing and ratings **used to be listed here**
and are now on both platforms.

Photo upload is the loose one: it exists on both platforms but in different places — Android only
during onboarding, iOS only in the edit sheet. Neither offers both. The avatar picker itself is at
parity, drawing the twelve `SplitCruiserAvatars` on each.

**A failed avatar image load looks different on each platform.** Android's `StudentAvatar` passes
Coil an `error` painter and draws the logo; iOS's uses `AsyncImage`'s `placeholder:`, which covers
loading *and* failure alike, so it draws the gradient-plus-initial fallback. Distinguishing them on
iOS means the phase-based `AsyncImage` initialiser. Left as is deliberately — which of the two is
the better failure state is a UX question, and the answer should then be applied to both.
