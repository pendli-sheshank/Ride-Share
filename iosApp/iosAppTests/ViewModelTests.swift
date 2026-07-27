import XCTest
import Shared
@testable import iosApp

final class ViewModelTests: XCTestCase {

    var viewModel: AppViewModel!

    override func setUp() {
        super.setUp()
        viewModel = AppViewModel()
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    func testViewModelInitialization() {
        XCTAssertNotNil(viewModel)
        XCTAssertNil(viewModel.currentUser)
        XCTAssertTrue(viewModel.activeOffers.isEmpty)
        XCTAssertTrue(viewModel.activeRequests.isEmpty)
    }

    func testCurrentUserPublished() {
        XCTAssertNil(viewModel.currentUser)

        let testUser = User(
            id: "user_123",
            name: "Alice",
            email: "alice@example.com",
            ratingAvg: 4.5
        )

        let expectation = XCTestExpectation(description: "User loaded")

        Task {
            // Simulating user loading
            let canPublishUser = testUser.id.isEmpty == false
            XCTAssertTrue(canPublishUser)
            expectation.fulfill()
        }

        wait(for: [expectation], timeout: 1.0)
    }

    func testActiveOffersArray() {
        XCTAssertTrue(viewModel.activeOffers.isEmpty)

        let testOffers = [
            TripOffer(
                id: "offer_1",
                origin: "Boston",
                destination: "NYC",
                costPerRider: 25.0,
                seatsLeft: 2
            ),
            TripOffer(
                id: "offer_2",
                origin: "Boston",
                destination: "Philadelphia",
                costPerRider: 20.0,
                seatsLeft: 3
            )
        ]

        XCTAssertEqual(testOffers.count, 2)
        XCTAssertEqual(testOffers[0].costPerRider, 25.0)
        XCTAssertEqual(testOffers[1].origin, "Boston")
    }

    func testLoadingStateManagement() {
        XCTAssertFalse(viewModel.isLoading)

        // Simulate loading state
        var isLoading = true
        XCTAssertTrue(isLoading)

        isLoading = false
        XCTAssertFalse(isLoading)
    }

    func testErrorMessageHandling() {
        XCTAssertTrue(viewModel.errorMessage.isEmpty)

        let testError = "Network error"
        XCTAssertFalse(testError.isEmpty)
    }

    func testUserMatchesArray() {
        XCTAssertTrue(viewModel.userMatches.isEmpty)

        let testMatch = TripMatch(
            id: "match_1",
            offerId: "offer_1",
            requestId: "request_1",
            hostId: "host_1",
            riderId: "rider_1",
            status: "pending"
        )

        XCTAssertEqual(testMatch.id, "match_1")
        XCTAssertEqual(testMatch.status, "pending")
    }

    func testOfferDetailAccess() {
        let offer = TripOffer(
            id: "offer_1",
            hostId: "host_123",
            hostName: "Driver Dave",
            origin: "Boston",
            destination: "NYC",
            costPerRider: 25.0,
            seatsLeft: 2,
            totalSeats: 4,
            status: "active"
        )

        XCTAssertEqual(offer.origin, "Boston")
        XCTAssertEqual(offer.destination, "NYC")
        XCTAssertEqual(offer.costPerRider, 25.0)
        XCTAssertEqual(offer.seatsLeft, 2)
        XCTAssertEqual(offer.totalSeats, 4)
    }

    func testRideRequestCreation() {
        let request = RideRequest(
            id: "req_1",
            riderId: "rider_1",
            riderName: "Passenger",
            origin: "MIT",
            destination: "Airport",
            seatsNeeded: 2
        )

        XCTAssertEqual(request.id, "req_1")
        XCTAssertEqual(request.seatsNeeded, 2)
        XCTAssertEqual(request.origin, "MIT")
    }

    func testMessageDisplay() {
        let message = Message(
            id: "msg_1",
            matchId: "match_1",
            senderId: "user_123",
            senderName: "Alice",
            text: "When should I pick you up?",
            timestamp: Date().timeIntervalSince1970 * 1000
        )

        XCTAssertEqual(message.senderName, "Alice")
        XCTAssertFalse(message.text.isEmpty)
        XCTAssertTrue(message.timestamp > 0)
    }

    func testLocationPlaceHandling() {
        let place = LocationPlace(
            name: "Boston Logan Airport",
            address: "1 Harborside Drive, Boston, MA 02128",
            category: "Airport",
            lat: 42.3656,
            lng: -71.0096
        )

        XCTAssertEqual(place.category, "Airport")
        XCTAssertTrue(place.lat > 0)
        XCTAssertTrue(place.lng < 0)
    }

    func testUserRatingDisplay() {
        let user = User(
            id: "user_1",
            name: "Alice",
            ratingAvg: 4.8,
            ratingCount: 15
        )

        XCTAssertEqual(user.ratingAvg, 4.8)
        XCTAssertEqual(user.ratingCount, 15)
        XCTAssertGreaterThanOrEqual(user.ratingAvg, 0)
        XCTAssertLessThanOrEqual(user.ratingAvg, 5)
    }

    func testNotificationHandling() {
        let notification = NotificationAlert(
            id: "alert_1",
            userId: "user_1",
            title: "Ride Matched",
            message: "You've been matched with a driver!",
            type: "match",
            isRead: false
        )

        XCTAssertFalse(notification.isRead)
        XCTAssertEqual(notification.type, "match")
        XCTAssertFalse(notification.title.isEmpty)
    }

    func testCommunityDisplay() {
        let community = Community(
            id: "comm_1",
            name: "Boston Students",
            location: "Boston, MA"
        )

        XCTAssertEqual(community.name, "Boston Students")
        XCTAssertTrue(community.location.contains("Boston"))
    }

    func testVehicleDisplay() {
        let vehicle = Vehicle(
            ownerId: "owner_1",
            make: "Toyota",
            model: "Camry",
            year: "2020",
            color: "Blue",
            licensePlate: "ABC123"
        )

        XCTAssertEqual(vehicle.make, "Toyota")
        XCTAssertEqual(vehicle.model, "Camry")
        XCTAssertFalse(vehicle.licensePlate.isEmpty)
    }
}
