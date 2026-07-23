# SawaariShare 🚗💨

**SawaariShare** is a modern, cost-sharing carpool and ride-sharing application designed specifically to empower students and communities to travel safely, affordably, and sustainably.

With a highly polished Material 3 visual interface, robust local offline support, and flexible user registration flows, SawaariShare makes coordinating rides a seamless experience.

---

## 🌟 Key Features

### 🔐 1. Seamless Authentication & Onboarding
*   **Flexible Access:** Supports standard email and password authentication (with strict validation checks ensuring a clean, well-formatted email address structure).
*   **Vouched Verification:** Allows users to link and verify alternate/official email addresses to unlock **vouched tier** benefits, elevating their trust score on the platform.
*   **Guest Mode Support:** Seamlessly transitions unverified or guest users to standard profile privileges upon verification.

### 🚗 2. Host a Sawaari (Offer a Ride)
*   **Comprehensive Details:** Drivers can set up a ride offer by specifying pick-up points, destinations, exact date/time (with elegant native pickers), available seats, and split-cost parameters.
*   **Vehicle Logging:** Link exact vehicle make, model, license details, and status so passengers know exactly what car to expect.
*   **Rider Dashboard:** Manage hosted rides, view joined passengers, and coordinate departure times.

### 👥 3. Join a Sawaari (Find a Ride)
*   **Explore Tab:** Browse through active rides on an intuitive card-based feed styled with clean typography and crisp icons.
*   **Propose Pick-up Dialog:** Coordinate pick-up locations dynamically before booking.
*   **Direct Communication:** Quick action triggers to call or email the host driver directly from their public profile.

### 🎨 4. Aesthetic & Visual Design (Material 3)
*   **Sawaari Visual Palette:** Employs Sawaari Saffron, Deep Indigo, Emerald Status accents, and clean Light Gray contrasts on a gorgeous, accessible slate layout.
*   **Accessible Components:** Fully compliant touch targets ($\ge$ 48dp), clear vector iconography, and high-contrast dialogue states to prevent color blending and maximize readability.
*   **Adaptive Transitions:** Designed with edge-to-edge system navigation and dynamic layouts suited for compact mobile devices.

---

## 🛠️ Tech Stack

*   **Platform:** Android (Kotlin)
*   **UI Framework:** Jetpack Compose (Material Design 3)
*   **Architecture:** Clean Architecture with **MVVM** (Model-View-ViewModel) pattern
*   **Concurrency:** Kotlin Coroutines & Kotlin StateFlow for asynchronous, reactive UI updates
*   **Database & Local Cache:** SQLite via **Room Database** for high-performance offline caching, user profile persistence, and state tracking
*   **Authentication:** Firebase Authentication (with robust, simulated fallback architectures when Firebase services are offline or not configured)
*   **Asset Management:** Coil & Android Vector Drawables for crisp, scalable visual illustrations

---

## 🔥 Firebase Integration & Setup

SawaariShare includes built-in support for **Firebase Authentication**, **Cloud Firestore** real-time snapshot synchronization, and **Firebase Storage**:

*   **Real-time Firestore Sync:** Automatically synchronizes `users`, `trip_offers`, `ride_requests`, `trip_matches`, and `messages` in real time when connected.
*   **Firebase Authentication:** Handles secure user sign-up, email/password login, and verification flows.
*   **Automatic Fallback Engine:** If Firebase credentials are not provided, SawaariShare automatically runs in **Sandbox Mode** using local persistent state without crashing.
*   **Firebase Setup Options:**
    1.  **Option A (google-services.json):** Place your `google-services.json` file inside the `/app` directory.
    2.  **Option B (Secrets Panel / .env):** Configure `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, and `FIREBASE_STORAGE_BUCKET` in the **Secrets Panel** in AI Studio.

---

## 📈 User Journeys & Uses

### Scenario A: The Commuting Student (Host)
1. **Sign Up / Log In** to SawaariShare.
2. Complete your **Profile Setup** including your vehicle's make/model and profile picture.
3. Tap **Create Ride**, specify your starting point, destination, date/time, and select available passenger seats.
4. Save the ride, allowing passengers to see and join your commute.

### Scenario B: The Ride-Seeker (Passenger)
1. Log in and browse the **Explore feed** of active trips.
2. Filter or select a ride to view the driver's public profile, vehicle information, and verified tier rating.
3. Tap **Join Ride** or **Propose Pick-up** to reserve your seat and coordinate directly with the driver.

---

## ⚙️ Development & Verification

To compile and verify the Android application successfully:

```bash
# Run unit tests and JVM checks
gradle :app:testDebugUnitTest

# Compile the application project
compile_applet
```
