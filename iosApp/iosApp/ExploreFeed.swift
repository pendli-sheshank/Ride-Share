import SwiftUI
import Shared

/// Carries a `TripOffer` into `.sheet(item:)`, which needs an `Identifiable` payload.
///
/// The obvious move is `extension TripOffer: Identifiable {}` — the Kotlin models all carry a
/// `String id`. The compiler warns against it, and is right to: declaring a conformance of an
/// imported type to an imported protocol silently changes meaning if `:shared` ever declares the
/// same conformance itself. Every list keys off `id:` explicitly for the same reason.
struct PresentedOffer: Identifiable {
    let offer: TripOffer
    var id: String { offer.id }
}

// MARK: - Explore

/// The browse feed: active coordination, search, filters and the cards.
struct ExploreFeed: View {
    let onJoined: (TripOffer) -> Void
    /// Switches the dashboard to My trips. The tab selection lives in `ContentView`, and the empty
    /// states point there rather than repeating the post button already on screen.
    let onShowTrips: () -> Void

    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    @State private var search = ""
    @State private var filter: FeedFilter = .all

    enum FeedFilter: String, CaseIterable {
        case all = "All"
        case cheap = "Under $15"
        case womenOnly = "Women Only"
        case withSeats = "With Seats"
    }

    var body: some View {
        GeometryReader { proxy in
            let isWide = proxy.size.width >= 500

            ScrollView {
                LazyVStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                    activeCoordination
                    Text(viewModel.mode == .rider ? "Trip Offers Near You" : "Local Ride Requests")
                        .font(BrandFont.title(.black))
                        .foregroundColor(Brand.textPrimary)
                        .padding(.top, BrandScale.spaceSm)

                    if viewModel.mode == .rider {
                        riderFeed(isWide: isWide)
                    } else {
                        hostFeed
                    }

                    Spacer().frame(height: 100)
                }
                .padding(.horizontal, BrandScale.spaceLg)
            }
            .refreshable { await viewModel.refresh() }
            .background(Brand.surface)
            .scrollContentBackground(.hidden)
        }
    }

    // MARK: Active coordination

    @ViewBuilder
    private var activeCoordination: some View {
        let live = viewModel.userMatches.filter {
            $0.status == "pending" || $0.status == "accepted"
        }
        if !live.isEmpty {
            Text("Active Trip Coordination")
                .font(BrandFont.fixed(15, .bold))
                .foregroundColor(Brand.textPrimary)
                .padding(.top, BrandScale.spaceSm)

            ForEach(live, id: \.id) { match in
                Button {
                    if match.status == "accepted" {
                        router.push(.chat(matchId: match.id))
                    } else {
                        router.push(.tripDetail(id: match.offerId, kind: .offer))
                    }
                } label: {
                    HStack(spacing: BrandScale.spaceMd) {
                        ZStack {
                            Circle().fill(
                                (match.status == "accepted" ? Brand.success : Brand.primary)
                                    .opacity(0.15)
                            )
                            Image(systemName: match.status == "accepted"
                                  ? "bubble.left.and.bubble.right.fill" : "hourglass")
                                .foregroundColor(match.status == "accepted" ? Brand.success : Brand.primary)
                        }
                        .frame(width: 40, height: 40)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(matchTitle(match))
                                .font(BrandFont.body(.bold))
                                .foregroundColor(Brand.textPrimary)
                            Text("Status: \(match.status) • Contribution: \(TripFormat.money(match.contribution))")
                                .font(BrandFont.eyebrow(.regular))
                                .foregroundColor(Brand.textSecondary)
                        }

                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").foregroundColor(Brand.textSecondary)
                    }
                    .padding(BrandScale.spaceLg)
                    .background(Brand.surfaceCard)
                    .cornerRadius(BrandScale.radiusMd)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func matchTitle(_ match: TripMatch) -> String {
        let me = viewModel.currentUser?.id ?? ""
        if match.hostId == me {
            return "Ride with \(match.riderName.isEmpty ? "rider" : match.riderName)"
        }
        return "Ride with Host"
    }

    // MARK: Rider feed

    @ViewBuilder
    private func riderFeed(isWide: Bool) -> some View {
        searchField
        filterChips

        let offers = filteredOffers

        if viewModel.activeOffers.isEmpty && viewModel.isRefreshing {
            FeedLoadingSkeleton()
        } else if viewModel.activeOffers.isEmpty {
            // Naming the two reasons this list can be empty right after posting a ride: your own
            // rides are filtered out of the browse feed (you cannot book your own seat), and the
            // post button on this tab creates a *request*, which hosts answer from "Give a ride".
            // Unexplained, posting a ride and then reading "no offers" looks like a broken app.
            BrandEmptyState(
                icon: "car.fill",
                title: "No rides posted yet",
                description: "Nobody else has offered a ride yet. Rides you post yourself don't show up here — they're under My trips.",
                actionLabel: "Go to My trips",
                action: { onShowTrips() }
            )
        } else if offers.isEmpty {
            BrandEmptyState(
                icon: "magnifyingglass",
                title: "No rides match those filters",
                description: "There are rides on offer, but none matching your search. Clear the filters to see all of them.",
                actionLabel: "Clear filters",
                action: {
                    search = ""
                    filter = .all
                }
            )
        } else {
            ForEach(offers, id: \.id) { offer in
                TripOfferCard(
                    offer: offer,
                    cta: cta(for: offer),
                    isWide: isWide,
                    onJoin: { join(offer) },
                    onTap: { router.push(.tripDetail(id: offer.id, kind: .offer)) }
                )
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: BrandScale.spaceSm) {
            Image(systemName: "magnifyingglass").foregroundColor(Brand.textSecondary)
            TextField("Search by origin, destination or host...", text: $search)
                .textFieldStyle(.plain)
                .accessibilityIdentifier("trip_list_search_input")
            if !search.isEmpty {
                Button {
                    search = ""
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundColor(Brand.textSecondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(BrandScale.spaceMd)
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusMd)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                .stroke(Brand.outline, lineWidth: 1)
        )
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: BrandScale.spaceSm) {
                ForEach(FeedFilter.allCases, id: \.self) { candidate in
                    BrandFilterChip(
                        label: candidate.rawValue,
                        isSelected: filter == candidate
                    ) {
                        filter = candidate
                    }
                }
            }
        }
    }

    // The quick-place chips that sat here — Snell, Ruggles, Mission Hill, Harvard, South Station —
    // are gone. They hardcoded one university's neighbourhood into a product open to anyone, and
    // all they did was type a word into the search field above.

    private var filteredOffers: [TripOffer] {
        viewModel.activeOffers.filter { offer in
            let matchesSearch = search.isEmpty
                || offer.origin.localizedCaseInsensitiveContains(search)
                || offer.destination.localizedCaseInsensitiveContains(search)
                || offer.hostName.localizedCaseInsensitiveContains(search)

            let matchesFilter: Bool
            switch filter {
            case .all: matchesFilter = true
            case .cheap: matchesFilter = offer.costPerRider < 15
            case .womenOnly: matchesFilter = offer.womenOnly
            case .withSeats: matchesFilter = offer.seatsLeft > 0
            }
            return matchesSearch && matchesFilter
        }
    }

    /// Decided once, here, so the feed and the detail screen cannot disagree about a ride's state.
    private func cta(for offer: TripOffer) -> OfferCTA {
        let me = viewModel.currentUser?.id ?? ""
        if offer.hostId == me { return .ownTrip }
        if offer.passengers.contains(me) { return .joined }
        if viewModel.userMatches.contains(where: { $0.offerId == offer.id && $0.status == "pending" }) {
            return .pending
        }
        if offer.seatsLeft <= 0 { return .unavailable }
        return .join
    }

    private func join(_ offer: TripOffer) {
        Task {
            if await viewModel.joinRide(offerId: offer.id) {
                onJoined(offer)
            }
        }
    }

    // MARK: Host feed

    @ViewBuilder
    private var hostFeed: some View {
        if viewModel.activeRequests.isEmpty && viewModel.isRefreshing {
            FeedLoadingSkeleton()
        } else if viewModel.activeRequests.isEmpty {
            // Same rule as the rider side: no post button here, because the one at the bottom of
            // the screen already does it and two controls for one action is what made this
            // confusing.
            BrandEmptyState(
                icon: "person.2.fill",
                title: "No open requests",
                description: "Nobody has asked for a ride yet. Requests you post yourself don't show up here — they're under My trips.",
                actionLabel: "Go to My trips",
                action: { onShowTrips() }
            )
        } else {
            ForEach(viewModel.activeRequests, id: \.id) { request in
                RideRequestCard(request: request) {
                    router.push(.tripDetail(id: request.id, kind: .request))
                }
            }
        }
    }
}

// MARK: - My trips

/// The schedule: hosting, joined, open requests and history.
struct TripsTab: View {
    let onFindARide: () -> Void

    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    private var upcomingHosted: [TripOffer] {
        viewModel.hostedRides.filter { RideSchedule.shared.isCurrent(status: $0.status) }
    }
    private var upcomingJoined: [TripOffer] {
        viewModel.joinedRides.filter { RideSchedule.shared.isCurrent(status: $0.status) }
    }
    private var pastRides: [TripOffer] {
        var seen = Set<String>()
        return (viewModel.hostedRides + viewModel.joinedRides)
            .filter { RideSchedule.shared.isPast(status: $0.status) }
            .filter { seen.insert($0.id).inserted }
            .sorted { $0.departureTime > $1.departureTime }
    }

    private var hasAnything: Bool {
        !viewModel.hostedRides.isEmpty
            || !viewModel.joinedRides.isEmpty
            || !viewModel.myRideRequests.isEmpty
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                if !hasAnything {
                    BrandEmptyState(
                        icon: "calendar",
                        title: "Nothing on your schedule yet",
                        description: "Rides you host or join will show up here, along with your open requests.",
                        actionLabel: "Find a ride",
                        action: onFindARide,
                        illustrationType: .joined
                    )
                } else {
                    if !viewModel.hostedRides.isEmpty {
                        TripsSectionHeader(title: "Rides you're hosting")
                        if upcomingHosted.isEmpty {
                            BrandEmptyState(
                                icon: "car.fill",
                                title: "Nothing scheduled",
                                description: "You have no upcoming rides to host.",
                                illustrationType: .hosted
                            )
                        } else {
                            ForEach(upcomingHosted, id: \.id) { offer in
                                HostedRideScheduleCard(
                                    offer: offer,
                                    onTap: { router.push(.tripDetail(id: offer.id, kind: .offer)) },
                                    onStatusChange: { status in updateStatus(offer, to: status) }
                                )
                            }
                        }
                    }

                    if !viewModel.joinedRides.isEmpty {
                        TripsSectionHeader(title: "Rides you've joined")
                        if upcomingJoined.isEmpty {
                            BrandEmptyState(
                                icon: "chair.lounge.fill",
                                title: "No upcoming seats",
                                description: "You have not reserved a seat on an upcoming ride.",
                                actionLabel: "Find a ride",
                                action: onFindARide,
                                illustrationType: .joined
                            )
                        } else {
                            ForEach(upcomingJoined, id: \.id) { offer in
                                JoinedRideScheduleCard(offer: offer) {
                                    router.push(.tripDetail(id: offer.id, kind: .offer))
                                }
                            }
                        }
                    }

                    if !viewModel.myRideRequests.isEmpty {
                        TripsSectionHeader(title: "Your ride requests")
                        ForEach(viewModel.myRideRequests, id: \.id) { request in
                            MyRideRequestCard(request: request) {
                                cancelRequest(request)
                            }
                        }
                    }

                    if !pastRides.isEmpty {
                        TripsSectionHeader(title: "Past rides")
                        ForEach(pastRides, id: \.id) { offer in
                            PastRideCard(
                                offer: offer,
                                currentUserId: viewModel.currentUser?.id ?? ""
                            ) {
                                router.push(.tripDetail(id: offer.id, kind: .offer))
                            }
                        }
                    }
                }

                Spacer().frame(height: 100)
            }
            .padding(.horizontal, BrandScale.spaceLg)
        }
        .refreshable {
            await viewModel.refresh()
            await viewModel.refreshMyTrips()
        }
        .task { await viewModel.refreshMyTrips() }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
    }

    private func updateStatus(_ offer: TripOffer, to status: String) {
        Task {
            if await viewModel.updateOfferStatus(offerId: offer.id, newStatus: status) {
                viewModel.notify("Ride status updated")
                await viewModel.refreshMyTrips()
            }
        }
    }

    private func cancelRequest(_ request: RideRequest) {
        Task {
            if await viewModel.updateRequestStatus(requestId: request.id, newStatus: "cancelled") {
                viewModel.notify("Ride request cancelled")
            }
        }
    }
}

// MARK: - Join confirmation

/// Android's `JoinSuccessDialog`, as a sheet.
struct JoinSuccessSheet: View {
    let offer: TripOffer
    let onViewTrips: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: BrandScale.spaceLg) {
            ZStack {
                Circle().fill(Brand.primaryContainer.opacity(0.4)).frame(width: 64, height: 64)
                Circle().fill(Brand.primaryContainer).frame(width: 48, height: 48)
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(Brand.primary)
            }
            .padding(.top, BrandScale.spaceXl)

            Text("Request Submitted!")
                .font(BrandFont.headline())
                .foregroundColor(Brand.textPrimary)

            Text("We've let \(offer.hostName.isEmpty ? "the host" : offer.hostName) know. You'll be able to chat as soon as they accept.")
                .font(BrandFont.caption())
                .foregroundColor(Brand.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, BrandScale.spaceXl)

            VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                HStack {
                    Text(offer.hostName.isEmpty ? "Your host" : offer.hostName)
                        .font(BrandFont.body(.bold))
                        .foregroundColor(Brand.textPrimary)
                    Spacer()
                    Text(TripFormat.money(offer.costPerRider))
                        .font(BrandFont.title(.black))
                        .foregroundColor(Brand.primary)
                }
                Divider().background(Brand.outline)
                RouteIndicator(
                    origin: offer.origin,
                    destination: offer.destination,
                    scale: .compact,
                    pins: true
                )
                Divider().background(Brand.outline)
                HStack(spacing: 4) {
                    Image(systemName: "clock").font(.system(size: 12))
                    Text(TripFormat.detail(offer.departureTime)).font(BrandFont.eyebrow(.regular))
                }
                .foregroundColor(Brand.textSecondary)
            }
            .padding(BrandScale.spaceLg)
            .background(Brand.surface)
            .cornerRadius(BrandScale.radiusLg)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                    .stroke(Brand.outline, lineWidth: 1)
            )
            .padding(.horizontal, BrandScale.spaceLg)

            Spacer(minLength: 0)

            VStack(spacing: BrandScale.spaceSm) {
                Button("View My Trips", action: onViewTrips)
                    .buttonStyle(BrandButtonStyle(height: 46))
                    .accessibilityIdentifier("dialog_view_trips_button")
                Button("Done") { dismiss() }
                    .buttonStyle(BrandOutlineButtonStyle(height: 46))
                    .accessibilityIdentifier("dialog_dismiss_button")
            }
            .padding(.horizontal, BrandScale.spaceLg)
            .padding(.bottom, BrandScale.spaceLg)
        }
        .background(Brand.surfaceCard)
        .presentationDetents([.large])
    }
}
