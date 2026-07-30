import Foundation
import Shared

// MARK: - iOS bridge to the shared backend

/// Drives the SwiftUI layer from `SplitCruiserRepository` in `:shared`.
///
/// This used to be a stub over local `@Published` arrays that vanished on relaunch, because the
/// repository was Android-only — it took an `android.content.Context` and drove the native Firebase
/// SDKs. The backend now speaks Firebase's REST APIs from `commonMain`, so this file calls exactly
/// the same code Android does.
///
/// Two interop rules the shared API is shaped around:
/// - Kotlin `suspend` maps to Swift `async`, and the shared functions are annotated `@Throws`, so
///   failures arrive as Swift errors rather than terminating the process.
/// - `Flow` is not usable from Swift, so state arrives through `observe*` callbacks that hand back
///   a `FlowSubscription` to cancel. Those callbacks are delivered on the main queue.
@MainActor
final class AppViewModel: ObservableObject {

    @Published var currentUser: User?
    @Published var activeOffers: [TripOffer] = []
    @Published var activeRequests: [RideRequest] = []
    @Published var myRideRequests: [RideRequest] = []
    @Published var userMatches: [TripMatch] = []
    @Published var isConnected = false
    @Published var isLoading = false
    @Published var errorMessage: String?

    /// Where the user lives, so a ride request can prefill its pickup. iOS never collected this
    /// before, which is why its post-request form started blank while Android's arrived filled in.
    @Published var contactDetails: ContactDetails?

    /// True once a profile exists; the UI routes to profile setup when it does not.
    @Published var needsProfileSetup = false

    let repository: SplitCruiserRepository
    private var subscriptions: [FlowSubscription] = []

    init() {
        repository = SplitCruiserRepository(
            config: FirebaseConfig.companion.fromBuild(),
            store: UserDefaultsStore()
        )

        subscriptions.append(repository.observeCurrentUser { [weak self] user in
            self?.currentUser = user
        })
        subscriptions.append(repository.observeActiveOffers { [weak self] offers in
            self?.activeOffers = offers
        })
        subscriptions.append(repository.observeActiveRequests { [weak self] requests in
            self?.activeRequests = requests
        })
        subscriptions.append(repository.observeMyRideRequests { [weak self] requests in
            self?.myRideRequests = requests
        })
        subscriptions.append(repository.observeUserMatches { [weak self] matches in
            self?.userMatches = matches
        })
        subscriptions.append(repository.observeConnection { [weak self] connected in
            // A Bool in a generic position arrives as KotlinBoolean.
            self?.isConnected = connected.boolValue
        })
        subscriptions.append(repository.observeContactDetails { [weak self] details in
            self?.contactDetails = details
        })

        // Restores a stored session and starts the polling refreshers.
        repository.start()
    }

    deinit {
        subscriptions.forEach { $0.cancel() }
    }

    var isSignedIn: Bool { currentUser != nil }

    /// False when this build has no Firebase configuration, so the UI can say so plainly rather
    /// than looking broken.
    var isBackendConfigured: Bool { repository.isFirebaseEnabled }

    // MARK: - Authentication

    func logIn(email: String, password: String) async {
        await perform {
            let needsProfile = try await self.repository.logInWithEmail(email: email, password: password)
            self.needsProfileSetup = needsProfile.boolValue
        }
    }

    func signUp(email: String, password: String) async {
        await perform {
            let needsProfile = try await self.repository.signUpWithEmail(email: email, password: password)
            self.needsProfileSetup = needsProfile.boolValue
        }
    }

    /// Onboarding, now collecting what Android has always collected: a contact number, a home
    /// address, and optionally a vehicle.
    ///
    /// Without the phone number, an Android rider matched with an iOS-onboarded host opened the
    /// contact card to a blank row; without the address, this app could not prefill a pickup.
    func completeProfile(
        name: String,
        lastInitial: String,
        homeArea: String,
        phoneNumber: String,
        homeAddress: String,
        homeLat: Double,
        homeLng: Double,
        vehicle: Vehicle?
    ) async {
        await perform {
            try await self.repository.createUserProfile(
                name: name,
                lastInitial: lastInitial,
                homeArea: homeArea,
                contact: ContactDetails(
                    phoneNumber: phoneNumber,
                    homeAddress: homeAddress,
                    homeLat: homeLat,
                    homeLng: homeLng
                ),
                vehicle: vehicle
            )
            self.needsProfileSetup = false
        }
    }

    func sendPasswordReset(email: String) async {
        await perform { try await self.repository.sendPasswordReset(email: email) }
    }

    func logOut() {
        repository.logout()
        needsProfileSetup = false
    }

    // MARK: - Posting rides

    func postRideOffer(
        origin: String,
        destination: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departureTime: Date,
        totalSeats: Int,
        costPerRider: Double,
        womenOnly: Bool,
        vehicleInfo: String,
        exitLocation: String = ""
    ) async -> Bool {
        await perform {
            let offer = RideFactory.shared.newTripOffer(
                origin: origin,
                destination: destination,
                originLat: originLat,
                originLng: originLng,
                destLat: destLat,
                destLng: destLng,
                departureTime: departureTime.epochMillis,
                totalSeats: Int32(totalSeats),
                costPerRider: costPerRider,
                womenOnly: womenOnly,
                vehicleInfo: vehicleInfo,
                exitLocation: exitLocation
            )
            try await self.repository.postTripOffer(offer: offer)
        }
    }

    func postRideRequest(
        origin: String,
        destination: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departureTime: Date,
        seatsNeeded: Int,
        notes: String,
        womenOnly: Bool,
        exitLocation: String = ""
    ) async -> Bool {
        await perform {
            let request = RideFactory.shared.newRideRequest(
                origin: origin,
                destination: destination,
                originLat: originLat,
                originLng: originLng,
                destLat: destLat,
                destLng: destLng,
                departureTime: departureTime.epochMillis,
                seatsNeeded: Int32(seatsNeeded),
                notes: notes,
                womenOnly: womenOnly,
                exitLocation: exitLocation
            )
            try await self.repository.postRideRequest(request: request)
        }
    }

    // MARK: - Joining and matching

    func joinRide(offerId: String) async -> Bool {
        await perform { try await self.repository.joinTripOfferDirect(offerId: offerId) }
    }

    func acceptMatch(matchId: String) async {
        await perform { try await self.repository.acceptMatch(matchId: matchId) }
    }

    func declineMatch(matchId: String) async {
        await perform { try await self.repository.declineMatch(matchId: matchId) }
    }

    func refresh() async {
        await perform { try await self.repository.refreshNow() }
    }

    // MARK: - Ride detail

    /// The host's public profile, for the trust information the detail screen shows before
    /// somebody commits to getting in a stranger's car. Reads the cache first; falls back to a
    /// fetch, which also populates the cache for next time.
    func hostProfile(userId: String) async -> User? {
        if let cached = repository.getUserPublicProfile(userId: userId) { return cached }
        return try? await repository.fetchUserProfile(userId: userId)
    }

    func vehicle(userId: String) async -> Vehicle? {
        if let cached = repository.getVehicleInfo(userId: userId) { return cached }
        return try? await repository.fetchVehicleInfo(userId: userId)
    }

    /// The host's two real decisions — see `HostControlsPolicy` in `:shared` for which statuses
    /// still allow them.
    func updateOfferStatus(offerId: String, newStatus: String) async -> Bool {
        await perform { try await self.repository.updateTripOfferStatus(offerId: offerId, newStatus: newStatus) }
    }

    // MARK: - Chat

    /// Subscribes to one conversation. The returned handle must be cancelled when the screen
    /// goes away, and `openChat` tells the repository to poll this conversation quickly (3s)
    /// while it is on screen.
    func observeChat(matchId: String, onChange: @escaping ([Message]) -> Void) -> FlowSubscription {
        repository.openChat(matchId: matchId)
        return repository.observeChat(matchId: matchId) { messages in
            onChange(messages)
        }
    }

    func closeChat() {
        repository.openChat(matchId: nil)
    }

    func sendMessage(matchId: String, text: String) async {
        await perform { try await self.repository.sendMessage(matchId: matchId, text: text) }
    }

    /// A structured pickup proposal or confirmation — see `MessageType` in `:shared`.
    func sendPickupMessage(matchId: String, type: String, spot: String, time: String) async {
        let summary = type == MessageType.shared.PICKUP_CONFIRMED
            ? "Confirmed: meet at \(spot) at \(time)"
            : "Pickup proposal: \(spot) at \(time)"
        await perform {
            try await self.repository.sendMessage(
                matchId: matchId,
                text: summary,
                type: type,
                pickupSpot: spot,
                pickupTime: time
            )
        }
    }

    /// The offer behind a match, so the chat screen can show what ride is being coordinated.
    func offer(for match: TripMatch) -> TripOffer? {
        repository.getTripOfferById(offerId: match.offerId)
    }

    // MARK: - Ratings

    func submitRating(toUserId: String, rating: Float, comment: String) async -> Bool {
        await perform {
            try await self.repository.submitRating(
                toUserId: toUserId,
                ratingValue: rating,
                comment: comment
            )
        }
    }

    // MARK: - Search

    func searchPlaces(_ query: String) async -> [PhotonPlaceResult] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return await OsmLocationService.companion.autocompletePhoton(query: query, limit: 6)
    }

    // MARK: - Error handling

    func clearError() {
        errorMessage = nil
    }

    /// The load/error dance every action repeated. Returns whether the block succeeded.
    @discardableResult
    private func perform(_ block: @escaping () async throws -> Void) async -> Bool {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            try await block()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}

extension Date {
    /// The models carry epoch milliseconds as Int64 throughout.
    var epochMillis: Int64 { Int64(timeIntervalSince1970 * 1000) }

    init(epochMillis: Int64) {
        self.init(timeIntervalSince1970: Double(epochMillis) / 1000)
    }
}
