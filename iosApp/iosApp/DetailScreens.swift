import SwiftUI
import Shared

// MARK: - Trip detail

/// Resolves a route's id into a model and renders the right branch.
///
/// Android's `TripDetailScreen` does the same three-step lookup — cache, then a network fetch, then
/// an unavailable state — because a cold cache or a deep link has neither the offer nor the
/// request in memory.
struct TripDetailScreen: View {
    let id: String
    let kind: TripDetailKind

    @EnvironmentObject private var viewModel: AppViewModel

    @State private var offer: TripOffer?
    @State private var request: RideRequest?
    @State private var isResolving = true

    var body: some View {
        Group {
            if isResolving {
                ProgressView()
                    .tint(Brand.primary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let offer {
                RideDetailView(viewModel: viewModel, offer: offer)
            } else if let request {
                RideRequestDetailView(request: request)
            } else {
                BrandEmptyState(
                    icon: "exclamationmark.triangle.fill",
                    title: kind == .offer ? "Offer Unavailable" : "Request Unavailable",
                    description: "This ride is no longer available. It may have been cancelled or completed."
                )
            }
        }
        .background(Brand.surface)
        .navigationTitle(kind == .offer ? "Trip Offer Details" : "Ride Request Details")
        .navigationBarTitleDisplayMode(.inline)
        .task { await resolve() }
    }

    private func resolve() async {
        switch kind {
        case .offer:
            if let cached = viewModel.repository.getTripOfferById(offerId: id) {
                offer = cached
            } else {
                offer = try? await viewModel.repository.fetchTripOffer(offerId: id)
            }
        case .request:
            if let cached = viewModel.repository.getRideRequestById(requestId: id) {
                request = cached
            } else {
                request = try? await viewModel.repository.fetchRideRequest(requestId: id)
            }
        }
        isResolving = false
    }
}

/// The host's view of a rider's request.
struct RideRequestDetailView: View {
    let request: RideRequest

    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    @State private var showsOfferPicker = false
    @State private var showsAcceptSheet = false

    private var existingMatch: TripMatch? {
        viewModel.userMatches.first { $0.requestId == request.id }
    }

    /// The current user's own upcoming rides that could actually take this rider.
    private var offerableRides: [TripOffer] {
        viewModel.hostedRides.filter {
            $0.status == "active"
                && $0.departureTime > Date().epochMillis
                && $0.seatsLeft >= request.seatsNeeded
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BrandScale.spaceLg) {
                BrandCard(title: "Route", tint: Brand.primary) {
                    RouteIndicator(
                        origin: request.origin,
                        destination: request.destination,
                        originLabel: "RIDER PICKUP",
                        destinationLabel: "RIDER DROPOFF",
                        scale: .detail,
                        pins: true
                    )
                    if !request.exitLocation.isEmpty {
                        DetailRow(label: "Meeting spot", value: request.exitLocation)
                    }
                    Divider().background(Brand.outline)
                    DetailRow(label: "Departs", value: TripFormat.detail(request.departureTime))
                }

                riderCard

                if !request.notes.isEmpty {
                    BrandCard(title: "Rider notes", tint: Brand.primary) {
                        Text("“\(request.notes)”")
                            .font(BrandFont.caption())
                            .foregroundColor(Brand.textPrimary)
                    }
                }

                actionZone

                Spacer().frame(height: BrandScale.spaceXl)
            }
            .padding(BrandScale.spaceLg)
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
        .confirmationDialog(
            "Which ride are you offering?",
            isPresented: $showsOfferPicker,
            titleVisibility: .visible
        ) {
            ForEach(offerableRides, id: \.id) { ride in
                Button("\(ride.origin) → \(ride.destination) (\(ride.seatsLeft) seats left)") {
                    offerSeat(on: ride)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showsAcceptSheet) {
            AcceptRequestSheet(request: request) { match in
                showsAcceptSheet = false
                viewModel.notify("You're giving this ride. Opening chat…")
                router.push(.chat(matchId: match.id))
            }
        }
    }

    private var riderCard: some View {
        BrandCard(title: "Your rider", tint: Brand.primary) {
            HStack(spacing: BrandScale.spaceMd) {
                StudentAvatar(avatarUrl: "", name: request.riderName, size: 48, fontSize: 20)
                VStack(alignment: .leading, spacing: 2) {
                    Text(request.riderName.isEmpty ? "Rider" : request.riderName)
                        .font(BrandFont.fixed(15, .bold))
                        .foregroundColor(Brand.textPrimary)
                    HStack(spacing: 3) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 12))
                            .foregroundColor(Brand.warning)
                        Text("Rider rating: \(TripFormat.rating(request.riderRating))")
                            .font(BrandFont.caption())
                            .foregroundColor(Brand.textSecondary)
                    }
                }
                Spacer(minLength: 0)
                Button {
                    block()
                } label: {
                    Image(systemName: "nosign")
                        .foregroundColor(Brand.danger.opacity(0.7))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Block this rider")
            }
        }
    }

    @ViewBuilder
    private var actionZone: some View {
        if let match = existingMatch, match.status == "accepted" {
            BrandCard {
                VStack(spacing: BrandScale.spaceMd) {
                    Label("You accepted this ride request!", systemImage: "checkmark.circle.fill")
                        .font(BrandFont.fixed(15, .bold))
                        .foregroundColor(Brand.success)
                    Button("Open Chat Room") { router.push(.chat(matchId: match.id)) }
                        .buttonStyle(BrandButtonStyle(background: Brand.success))
                }
            }
        } else if let match = existingMatch, match.status == "pending" {
            BrandCard(title: "Pending cost-split match", tint: Brand.primary) {
                Text("Rider offered gas contribution split:")
                    .font(BrandFont.caption())
                    .foregroundColor(Brand.textSecondary)
                Text(TripFormat.money(match.contribution))
                    .font(.system(size: 24, weight: .black))
                    .foregroundColor(Brand.primary)
                HStack(spacing: BrandScale.spaceSm) {
                    Button("Decline") {
                        Task { await viewModel.declineMatch(matchId: match.id) }
                    }
                    .buttonStyle(BrandOutlineButtonStyle(tint: Brand.danger))

                    Button("Accept & Chat") {
                        Task {
                            await viewModel.acceptMatch(matchId: match.id)
                            router.push(.chat(matchId: match.id))
                        }
                    }
                    .buttonStyle(BrandButtonStyle(background: Brand.success, height: 44))
                }
            }
        } else {
            VStack(spacing: BrandScale.spaceSm) {
                Button("Accept & give this ride") { showsAcceptSheet = true }
                    .buttonStyle(BrandButtonStyle())
                    .accessibilityIdentifier("accept_request_button")

                Text("You don't need a posted ride. Accepting creates one for this trip.")
                    .font(BrandFont.eyebrow(.regular))
                    .foregroundColor(Brand.textSecondary)
                    .multilineTextAlignment(.center)

                // Only worth offering when there is actually something to attach it to.
                if !offerableRides.isEmpty {
                    Button("Add to one of my posted rides") { showsOfferPicker = true }
                        .buttonStyle(BrandOutlineButtonStyle())
                }
            }
        }
    }

    private func offerSeat(on ride: TripOffer) {
        Task {
            if let match = await viewModel.offerSeat(
                requestId: request.id,
                offerId: ride.id,
                contribution: ride.costPerRider
            ) {
                viewModel.notify("Ride offered! Opening chat…")
                router.push(.chat(matchId: match.id))
            }
        }
    }

    private func block() {
        Task {
            if await viewModel.blockUser(request.riderId) {
                router.pop()
            }
        }
    }
}

// MARK: - Accepting a request

/// Confirms a driver taking a rider's request, and collects the one thing the request cannot
/// carry: what each rider should chip in.
///
/// A `RideRequest` records where and when, never a price — so this is the only point in the flow
/// where the cost split can be set. It is prefilled from `suggestedContribution` (distance-based,
/// shared so both platforms propose the same number) rather than left blank, because a driver
/// staring at an empty box has nothing to anchor on.
struct AcceptRequestSheet: View {
    let request: RideRequest
    let onAccepted: (TripMatch) -> Void

    @EnvironmentObject private var viewModel: AppViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var contribution = ""
    @State private var isSuggesting = true
    @State private var isSubmitting = false
    @State private var formError: String?

    private var parsedContribution: Double? {
        Double(contribution.trimmingCharacters(in: .whitespaces))
    }

    private var canSubmit: Bool {
        if isSubmitting { return false }
        guard let value = parsedContribution else { return false }
        return value >= 0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: BrandScale.spaceXl) {
                    BrandCard(title: "The trip", tint: Brand.primary) {
                        RouteIndicator(
                            origin: request.origin,
                            destination: request.destination,
                            originLabel: "PICKUP",
                            destinationLabel: "DROPOFF",
                            scale: .card,
                            pins: true
                        )
                        Divider().background(Brand.outline)
                        DetailRow(label: "Departs", value: TripFormat.detail(request.departureTime))
                        DetailRow(
                            label: "Seats",
                            value: "\(request.seatsNeeded) seat\(request.seatsNeeded == 1 ? "" : "s")"
                        )
                        if request.womenOnly {
                            DetailRow(label: "Women only", value: "Yes", valueColor: Brand.accent)
                        }
                    }

                    FormSection(title: "Cost split") {
                        HStack(spacing: BrandScale.spaceSm) {
                            BrandTextField(
                                title: "Each rider chips in ($)",
                                placeholder: "0.00",
                                text: $contribution,
                                icon: "dollarsign.circle.fill",
                                iconTint: Brand.warning,
                                keyboard: .decimalPad,
                                accessibilityID: "accept_contribution_input"
                            )
                            if isSuggesting {
                                ProgressView().scaleEffect(0.7)
                            }
                        }
                        // Say why, not just what: the number is a suggestion, and the money never
                        // moves through the app.
                        Text("Suggested from the distance. Cash is settled in person — you can agree something else in chat.")
                            .font(BrandFont.eyebrow(.regular))
                            .foregroundColor(Brand.textSecondary)
                    }

                    if let formError {
                        Text(formError)
                            .font(BrandFont.caption())
                            .foregroundColor(Brand.danger)
                    }

                    Button("Accept & open chat") { submit() }
                        .buttonStyle(BrandButtonStyle(isEnabled: canSubmit))
                        .disabled(!canSubmit)
                        .accessibilityIdentifier("confirm_accept_button")

                    Spacer().frame(height: BrandScale.spaceXl)
                }
                .padding(BrandScale.spaceXl)
            }
            .background(Brand.surface)
            .scrollContentBackground(.hidden)
            .navigationTitle("Give this ride")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .task { await prefillSuggestion() }
    }

    private func prefillSuggestion() async {
        defer { isSuggesting = false }
        guard contribution.isEmpty else { return }
        let suggested = await viewModel.suggestedContribution(for: request)
        // Zero means the route could not be resolved; an empty field is better than a wrong number.
        if suggested > 0 {
            contribution = String(format: "%.2f", suggested)
        }
    }

    private func submit() {
        guard let value = parsedContribution else { return }
        formError = nil
        isSubmitting = true
        Task {
            let match = await viewModel.acceptRequestDirect(
                requestId: request.id,
                contribution: value
            )
            isSubmitting = false
            if let match {
                onAccepted(match)
            } else {
                formError = viewModel.errorMessage
            }
        }
    }
}

// MARK: - Matches

/// The list of active coordinations.
///
/// Android's bottom bar has no list at all — its "Chats" item jumps straight into
/// `userMatches.first()`, which leaves every other conversation unreachable from the bar. This
/// keeps the list.
struct MatchesScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    var body: some View {
        Group {
            if viewModel.userMatches.isEmpty {
                BrandEmptyState(
                    icon: "bubble.left.and.bubble.right.fill",
                    title: "No matches yet",
                    description: "Reserve a seat or accept a rider, and the conversation will show up here."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: BrandScale.spaceMd) {
                        ForEach(viewModel.userMatches, id: \.id) { match in
                            MatchRow(match: match)
                        }
                    }
                    .padding(BrandScale.spaceLg)
                }
                .refreshable { await viewModel.refresh() }
            }
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
        .navigationTitle("Matches")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct MatchRow: View {
    let match: TripMatch

    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    private var isHost: Bool { viewModel.currentUser?.id == match.hostId }

    /// The other party's name. Android shows the literal string "Your host" to a rider even when
    /// the host's name is a lookup away.
    private var counterpartName: String {
        if isHost {
            return match.riderName.isEmpty ? "Rider" : match.riderName
        }
        let hostName = viewModel.offer(for: match)?.hostName ?? ""
        return hostName.isEmpty ? "Your host" : hostName
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
            HStack {
                Text("Ride with \(counterpartName)")
                    .font(BrandFont.body(.bold))
                    .foregroundColor(Brand.textPrimary)
                Spacer()
                StatusBadge(status: match.status)
            }

            if isHost && match.status == "pending" {
                HStack(spacing: BrandScale.spaceSm) {
                    Button("Decline") {
                        Task { await viewModel.declineMatch(matchId: match.id) }
                    }
                    .buttonStyle(BrandOutlineButtonStyle(tint: Brand.danger, height: 40))

                    Button("Accept") {
                        Task { await viewModel.acceptMatch(matchId: match.id) }
                    }
                    .buttonStyle(BrandButtonStyle(background: Brand.success, height: 40))
                }
            } else if match.status == "accepted" || match.status == "completed" {
                Button {
                    router.push(.chat(matchId: match.id))
                } label: {
                    Label("Open chat", systemImage: "bubble.left.and.bubble.right.fill")
                }
                .buttonStyle(BrandButtonStyle(height: 40))
            }
        }
        .padding(BrandScale.spaceLg)
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusLg)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                .stroke(Brand.outline, lineWidth: 1)
        )
    }
}

// MARK: - Chat entry

/// Resolves a match id for `ChatView`, which needs the whole `TripMatch`.
struct ChatScreen: View {
    let matchId: String

    @EnvironmentObject private var viewModel: AppViewModel

    private var match: TripMatch? {
        viewModel.userMatches.first { $0.id == matchId }
            ?? viewModel.repository.getTripMatchById(matchId: matchId)
    }

    var body: some View {
        Group {
            if let match {
                ChatView(viewModel: viewModel, match: match)
            } else {
                BrandEmptyState(
                    icon: "bubble.left",
                    title: "Conversation unavailable",
                    description: "This match is no longer available."
                )
            }
        }
        .background(Brand.surface)
    }
}
