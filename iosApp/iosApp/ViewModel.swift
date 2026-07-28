import Foundation
import Shared

// MARK: - iOS ViewModel Bridge to Shared Models

@MainActor
class AppViewModel: ObservableObject {
    // Published properties for SwiftUI
    @Published var currentUser: User? = nil
    @Published var activeOffers: [TripOffer] = []
    @Published var activeRequests: [RideRequest] = []
    @Published var userMatches: [TripMatch] = []
    @Published var isLoading = false
    @Published var errorMessage: String? = nil

    // No repository here. `SawaariRepository` lives in `:app` (Android-only — it takes an
    // android.content.Context and talks to Firebase), so it is not part of `Shared` and cannot
    // be imported on iOS. Giving iOS real persistence means writing a repository in
    // `shared/commonMain` first; until then every method below works on local state only.

    // MARK: - User Authentication

    func loginUser(phoneNumber: String, password: String) async {
        isLoading = true
        errorMessage = nil

        // TODO: replace with a real call once a multiplatform repository exists in :shared.
        // Every parameter is supplied explicitly: Kotlin default arguments do not survive into
        // the generated Swift initializer, so the memberwise init requires all 19.
        currentUser = User(
            id: "temp_user_\(UUID().uuidString)",
            phoneNumber: phoneNumber,
            email: "",
            name: "iOS User",
            lastInitial: "U",
            avatarUrl: "",
            verifiedTier: "guest",
            invitedBy: "",
            ratingAvg: 0.0,
            ratingCount: 0,
            noShowCount: 0,
            communityId: "",
            homeArea: "",
            isWomenOnlyFilterEnabled: false,
            fcmToken: "",
            emailNotificationsEnabled: false,
            pushNotificationsEnabled: false,
            collegeName: "",
            verifiedEmail: ""
        )

        isLoading = false
    }

    // MARK: - Trip Offer Management

    func createTripOffer(_ offer: TripOffer) async {
        isLoading = true
        errorMessage = nil

        // TODO: Call a multiplatform repository once :shared has one.
        activeOffers.append(offer)
        isLoading = false
    }

    func fetchActiveOffers() async {
        isLoading = true
        errorMessage = nil

        // TODO: Fetch from a multiplatform repository once :shared has one.
        isLoading = false
    }

    // MARK: - Ride Request Management

    func createRideRequest(_ request: RideRequest) async {
        isLoading = true
        errorMessage = nil

        // TODO: Call a multiplatform repository once :shared has one.
        activeRequests.append(request)
        isLoading = false
    }

    // MARK: - Match Management

    func acceptMatch(_ match: TripMatch) async {
        isLoading = true
        errorMessage = nil

        // TODO: Call a multiplatform repository once :shared has one.
        isLoading = false
    }

    // MARK: - Error Handling

    func clearError() {
        errorMessage = nil
    }
}
