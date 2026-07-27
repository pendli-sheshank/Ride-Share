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

    private let repository: SawaariRepository

    init() {
        // Initialize repository with app context
        self.repository = SawaariRepository(appContext: nil)
    }

    // MARK: - User Authentication

    func loginUser(phoneNumber: String, password: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // TODO: Implement login logic
        // This would call repository.loginUser(...) when available
        currentUser = User(
            id: "temp_user_\(UUID().uuidString)",
            phoneNumber: phoneNumber,
            email: "",
            name: "iOS User",
            lastInitial: "U",
            avatarUrl: "",
            verifiedTier: "guest"
        )
    }

    // MARK: - Trip Offer Management

    func createTripOffer(_ offer: TripOffer) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // TODO: Call repository.createTripOffer(offer)
        activeOffers.append(offer)
    }

    func fetchActiveOffers() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // TODO: Implement fetching from repository
        // activeOffers = repository.fetchActiveOffers()
    }

    // MARK: - Ride Request Management

    func createRideRequest(_ request: RideRequest) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // TODO: Call repository.createRideRequest(request)
        activeRequests.append(request)
    }

    // MARK: - Match Management

    func acceptMatch(_ match: TripMatch) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // TODO: Call repository.acceptMatch(match)
    }

    // MARK: - Error Handling

    func clearError() {
        errorMessage = nil
    }
}
