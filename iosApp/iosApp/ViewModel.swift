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
    @Published var communities: [Community] = []
    @Published var isConnected = false
    @Published var isLoading = false
    @Published var errorMessage: String?

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
        subscriptions.append(repository.observeCommunities { [weak self] communities in
            self?.communities = communities
        })
        subscriptions.append(repository.observeConnection { [weak self] connected in
            // A Bool in a generic position arrives as KotlinBoolean.
            self?.isConnected = connected.boolValue
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

    func completeProfile(name: String, lastInitial: String, communityId: String, homeArea: String) async {
        await perform {
            try await self.repository.createUserProfile(
                name: name,
                lastInitial: lastInitial,
                communityId: communityId,
                homeArea: homeArea,
                vehicle: nil
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
        vehicleInfo: String
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
                vehicleInfo: vehicleInfo
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
        womenOnly: Bool
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
                womenOnly: womenOnly
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
