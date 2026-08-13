import SwiftUI
import Shared

// MARK: - Routes

/// Which trip-detail branch to render. Android encodes this as the `{type}` path segment of
/// `trip_detail/{id}/{type}`.
enum TripDetailKind: String, Hashable {
    case offer
    case request
}

/// The destinations pushed on top of the dashboard.
///
/// Android registers these as string routes on a `NavHost` and parses `{id}`/`{type}` back out
/// with `?: ""` fallbacks. A `Hashable` enum with associated values carries the same information
/// and makes the arguments the compiler's problem instead.
///
/// Deliberately absent: Android's `host_dashboard` route, which nothing navigates to. Host
/// analytics gets a real entry point here rather than a dead one.
enum Route: Hashable {
    case postOffer
    case postRequest
    case tripDetail(id: String, kind: TripDetailKind)
    case chat(matchId: String)
    case matches
    case profile
    case blockedList
    case hostDashboard
}

/// The three mutually exclusive states of the app.
///
/// Android drives these with `LaunchedEffect(currentUser)` and `navigate(...) { popUpTo(0) }`,
/// which fires on *every* user emission — including a background poll that merely refreshes the
/// profile. Deriving a phase instead means the stack is only reset when signed-in-ness or
/// `needsProfileSetup` actually changes, so a poll can never eject someone out of a chat.
enum AppPhase {
    case login
    case profileSetup
    case dashboard
}

/// Which side of the marketplace the Explore tab is showing. Android's `currentMode`.
enum RideMode: String, CaseIterable {
    case rider
    case host

    /// The label on the segmented control.
    var label: String {
        switch self {
        case .rider: return "Find a ride"
        case .host: return "Give a ride"
        }
    }

    /// The label on the extended FAB.
    ///
    /// Spelled out, because which of the two things it posts is the whole confusion: in
    /// "Find a ride" it creates a *request*, which hosts answer from "Give a ride" — it does not
    /// add anything to the offers list directly above it.
    var actionLabel: String {
        switch self {
        case .rider: return "Post a ride request"
        case .host: return "Post a ride offer"
        }
    }
}

/// The two real tabs on the dashboard's bottom bar.
enum DashboardTab {
    case explore
    case trips
}

// MARK: - Router

/// Owns the navigation stack for the dashboard.
@MainActor
final class AppRouter: ObservableObject {
    @Published var path: [Route] = []

    func push(_ route: Route) {
        path.append(route)
    }

    func pop() {
        if !path.isEmpty { path.removeLast() }
    }

    /// Android's `popUpTo(0) { inclusive = true }`.
    func popToRoot() {
        path.removeAll()
    }
}

// MARK: - Formatting

/// Date and money formatting, shared so eight screens do not each build a `DateFormatter`.
///
/// `RideDetailView.formatTime` used to construct one on every call — once per visible row in the
/// feed, on every scroll frame. `DateFormatter` is expensive enough that this shows up.
enum TripFormat {

    private static func formatter(_ pattern: String) -> DateFormatter {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US")
        df.dateFormat = pattern
        return df
    }

    /// `EEE, d MMM • h:mm a` — the feed and schedule cards.
    private static let cardFormatter = formatter("EEE, d MMM • h:mm a")
    /// `EEEE, d MMMM • h:mm a` — the detail screens and the join dialog.
    private static let detailFormatter = formatter("EEEE, d MMMM • h:mm a")
    /// `EEE, d MMM yyyy • h:mm a` — past rides, which need the year.
    private static let pastFormatter = formatter("EEE, d MMM yyyy • h:mm a")
    /// `EEE, MMM dd 'at' hh:mm a` — the chat accordion.
    private static let chatFormatter = formatter("EEE, MMM dd 'at' hh:mm a")

    /// Android renders a missing or zero departure time as "Time TBD" rather than as the epoch.
    private static func render(_ epochMillis: Int64, with df: DateFormatter) -> String {
        guard epochMillis > 0 else { return "Time TBD" }
        return df.string(from: Date(epochMillis: epochMillis))
    }

    static func card(_ epochMillis: Int64) -> String { render(epochMillis, with: cardFormatter) }
    static func detail(_ epochMillis: Int64) -> String { render(epochMillis, with: detailFormatter) }
    static func past(_ epochMillis: Int64) -> String { render(epochMillis, with: pastFormatter) }
    static func chat(_ epochMillis: Int64) -> String { render(epochMillis, with: chatFormatter) }

    /// `$12.34`, matching Android's `"$%.2f"`.
    static func money(_ amount: Double) -> String {
        String(format: "$%.2f", amount)
    }

    /// `$12`, matching the feed card's whole-dollar price.
    static func moneyShort(_ amount: Double) -> String {
        String(format: "$%.0f", amount)
    }

    /// `4.8`, matching Android's `"%.1f"`.
    static func rating(_ value: Float) -> String {
        String(format: "%.1f", value)
    }
}
