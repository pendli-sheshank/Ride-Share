import SwiftUI
import Shared

// Views are kept deliberately small and split into computed sub-views. Swift's type checker gives
// up on large ViewBuilder bodies ("unable to type-check this expression in reasonable time"), and
// that has already cost this project a macOS CI round trip once.

/// The conversation behind an accepted match.
///
/// iOS had nothing here: the Matches tab offered Accept and Decline, and once a match was
/// accepted there was no way to agree on where to actually meet. Android has had a full chat
/// screen the whole time, so an iOS rider and an Android host could not coordinate at all.
///
/// Messages arrive through `observeChat`, which the repository polls every 3 seconds while a
/// conversation is open (Firestore's realtime channel is gRPC-only, so there are no snapshot
/// listeners to use).
struct ChatView: View {
    @ObservedObject var viewModel: AppViewModel
    let match: TripMatch

    @State private var messages: [Message] = []
    @State private var draft = ""
    @State private var subscription: FlowSubscription?
    @State private var showProposeSheet = false

    @State private var confirmingProposalId: String?

    private var isHost: Bool { viewModel.currentUser?.id == match.hostId }
    private var offer: TripOffer? { viewModel.offer(for: match) }

    /// Proposals that already have a confirmation. Without this the Accept button never went away,
    /// so every extra tap posted another confirmation card.
    private var confirmedProposalIds: Set<String> {
        Set(
            messages
                .filter { $0.kind == MessageType.shared.PICKUP_CONFIRMED }
                .map(\.proposalId)
                .filter { !$0.isEmpty }
        )
    }

    private let quickReplies = [
        "I'm here! 📍",
        "A few minutes late ⏳",
        "Leaving now! 🚗",
        "I'm at the entrance 🚪",
        "Suggest a meeting spot? 🤔",
        "No problem! 👍",
    ]

    var body: some View {
        VStack(spacing: 0) {
            if let offer {
                rideSummary(offer)
            }

            messageList
            quickReplyRow
            composer
        }
        .background(Brand.surface.ignoresSafeArea())
        .navigationTitle(isHost ? "Ride with \(match.riderName)" : "Ride coordinator")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showProposeSheet) {
            ProposePickupSheet(
                viewModel: viewModel,
                initialPickup: PlaceSelection(
                    name: offer?.origin ?? "",
                    lat: offer?.originLat ?? 0,
                    lon: offer?.originLng ?? 0
                ),
                initialDropoff: PlaceSelection(
                    name: offer?.destination ?? "",
                    lat: offer?.destLat ?? 0,
                    lon: offer?.destLng ?? 0
                ),
                initialContribution: match.contribution
            ) { pickup, dropoff, time, contribution in
                Task {
                    await viewModel.sendPickupProposal(
                        matchId: match.id,
                        pickupAddress: pickup,
                        dropoffAddress: dropoff,
                        pickupTime: time,
                        contribution: contribution
                    )
                }
            }
        }
        .onAppear {
            subscription = viewModel.observeChat(matchId: match.id) { messages = $0 }
        }
        .onDisappear {
            subscription?.cancel()
            subscription = nil
            viewModel.closeChat()
        }
    }

    // MARK: - Ride summary

    private func rideSummary(_ offer: TripOffer) -> some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
            HStack {
                Image(systemName: "car.fill").foregroundColor(Brand.primary)
                Text("\(offer.origin) → \(offer.destination)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(Brand.textPrimary)
                    .lineLimit(1)
                Spacer()
                Text(String(format: "$%.2f", match.contribution))
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.primary)
            }

            Button {
                showProposeSheet = true
            } label: {
                HStack(spacing: BrandScale.spaceXs) {
                    Image(systemName: "mappin.and.ellipse")
                    Text("Propose a pickup spot")
                }
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Brand.primary)
            }
        }
        .padding(BrandScale.spaceMd)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.primaryContainer.opacity(0.35))
    }

    // MARK: - Messages

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: BrandScale.spaceSm) {
                    ForEach(messages, id: \.id) { message in
                        MessageBubble(
                            message: message,
                            isMine: message.senderId == viewModel.currentUser?.id,
                            isConfirmed: confirmedProposalIds.contains(message.id),
                            isConfirming: confirmingProposalId == message.id,
                            onConfirm: {
                                confirmingProposalId = message.id
                                Task {
                                    await viewModel.confirmPickup(proposalMessageId: message.id)
                                    confirmingProposalId = nil
                                }
                            }
                        )
                        .id(message.id)
                    }
                }
                .padding(BrandScale.spaceMd)
            }
            .onChange(of: messages.count) { _ in
                if let last = messages.last {
                    withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                }
            }
        }
    }

    private var quickReplyRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: BrandScale.spaceSm) {
                ForEach(quickReplies, id: \.self) { reply in
                    Button(reply) {
                        Task { await viewModel.sendMessage(matchId: match.id, text: reply) }
                    }
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(Brand.primary)
                    .padding(.horizontal, BrandScale.spaceMd)
                    .padding(.vertical, BrandScale.spaceSm)
                    .background(Brand.primaryContainer.opacity(0.4))
                    .clipShape(Capsule())
                }
            }
            .padding(.horizontal, BrandScale.spaceMd)
            .padding(.vertical, BrandScale.spaceSm)
        }
        .background(Brand.surfaceCard)
    }

    private var composer: some View {
        HStack(spacing: BrandScale.spaceSm) {
            TextField("Coordinate pickup…", text: $draft)
                .textFieldStyle(.roundedBorder)

            Button {
                let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else { return }
                draft = ""
                Task { await viewModel.sendMessage(matchId: match.id, text: text) }
            } label: {
                Image(systemName: "paperplane.fill")
                    .foregroundColor(Brand.onPrimary)
                    .padding(BrandScale.spaceMd)
                    .background(Brand.primary)
                    .clipShape(Circle())
            }
            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(BrandScale.spaceMd)
        .background(Brand.surfaceCard)
    }
}

// MARK: - Bubbles

/// One message. Which of the three shapes it takes comes from `Message.kind` — a real field on the
/// model rather than a `[PROPOSAL]` prefix parsed out of the text.
struct MessageBubble: View {
    let message: Message
    let isMine: Bool
    /// Whether a confirmation already answers this proposal. Computed by the parent, which is the
    /// only thing that can see the whole conversation.
    let isConfirmed: Bool
    let isConfirming: Bool
    let onConfirm: () -> Void

    var body: some View {
        HStack {
            if isMine || message.isSystem { Spacer(minLength: 40) }

            switch message.kind {
            case MessageType.shared.PICKUP_PROPOSAL:
                proposal
            case MessageType.shared.PICKUP_CONFIRMED:
                confirmed
            default:
                text
            }

            if !isMine || message.isSystem { Spacer(minLength: 40) }
        }
    }

    private var text: some View {
        VStack(alignment: .leading, spacing: 2) {
            if !isMine && !message.isSystem {
                Text(message.senderName)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.primary)
            }
            Text(message.text)
                .font(.callout)
                .foregroundColor(bubbleTextColor)
        }
        .padding(.horizontal, BrandScale.spaceMd)
        .padding(.vertical, BrandScale.spaceSm)
        .background(bubbleColor)
        .cornerRadius(BrandScale.radiusLg)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                .stroke(isMine || message.isSystem ? Color.clear : Brand.outline, lineWidth: 1)
        )
    }

    private var bubbleColor: Color {
        if message.isSystem { return Brand.outline }
        return isMine ? Brand.primary : Brand.surfaceCard
    }

    private var bubbleTextColor: Color {
        if message.isSystem { return Brand.textSecondary }
        return isMine ? Brand.onPrimary : Brand.textPrimary
    }

    /// The labelled lines shared by the proposal and the confirmation, so the two read identically
    /// and the confirmation is visibly an agreement to the same terms.
    private var pickupDetails: some View {
        VStack(alignment: .leading, spacing: 2) {
            detailRow("Pick up", message.spot)
            if !message.dropoffSpot.isEmpty {
                detailRow("Drop off", message.dropoffSpot)
            }
            detailRow("Time", message.time)
            if message.contribution > 0 {
                detailRow(
                    message.kind == MessageType.shared.PICKUP_CONFIRMED ? "Agreed share" : "Your share",
                    String(format: "$%.2f", message.contribution)
                )
            }
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: BrandScale.spaceSm) {
            Text(label)
                .font(BrandFont.eyebrow(.bold))
                .foregroundColor(Brand.textSecondary)
                .frame(width: 68, alignment: .leading)
            Text(value)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(Brand.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var proposal: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
            Label("Proposed pickup", systemImage: "mappin.and.ellipse")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Brand.primary)

            pickupDetails

            if isConfirmed {
                Text("Confirmed")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.success)
            } else if isMine {
                Text("Awaiting confirmation…")
                    .font(.caption2)
                    .foregroundColor(Brand.textSecondary)
            } else {
                Button(isConfirming ? "Confirming…" : "Accept and confirm", action: onConfirm)
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.onPrimary)
                    .padding(.horizontal, BrandScale.spaceMd)
                    .padding(.vertical, BrandScale.spaceSm)
                    .background(Brand.success.opacity(isConfirming ? 0.5 : 1))
                    .cornerRadius(BrandScale.radiusSm)
                    .disabled(isConfirming)
            }
        }
        .padding(BrandScale.spaceMd)
        .background(Brand.primaryContainer.opacity(0.25))
        .cornerRadius(BrandScale.radiusLg)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                .stroke(Brand.primary, lineWidth: 1)
        )
    }

    private var confirmed: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
            Label("Pickup confirmed", systemImage: "checkmark.circle.fill")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Brand.success)

            pickupDetails

            if message.contribution > 0 {
                Text("Both of you have agreed to this amount. Pay in cash when you meet.")
                    .font(BrandFont.eyebrow(.regular))
                    .foregroundColor(Brand.textSecondary)
            }
        }
        .padding(BrandScale.spaceMd)
        .background(Brand.success.opacity(0.12))
        .cornerRadius(BrandScale.radiusLg)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                .stroke(Brand.success, lineWidth: 1)
        )
    }
}

// MARK: - Propose a pickup

struct ProposePickupSheet: View {
    @Environment(\.presentationMode) private var presentationMode
    @ObservedObject var viewModel: AppViewModel

    /// Seeded with the ride's own coordinates, not just its text: `LocationAutocompleteField` only
    /// shows a prefilled selection once `isResolved` is true, which needs a real lat/lon.
    let initialPickup: PlaceSelection
    let initialDropoff: PlaceSelection
    let initialContribution: Double
    let onPropose: (String, String, String, Double) -> Void

    @State private var pickup = PlaceSelection()
    @State private var dropoff = PlaceSelection()
    @State private var time = ""
    @State private var contribution = ""
    @State private var didPrefill = false

    private var amount: Double? { Double(contribution.trimmingCharacters(in: .whitespaces)) }

    private var canSend: Bool {
        !pickup.name.trimmingCharacters(in: .whitespaces).isEmpty
            && !time.trimmingCharacters(in: .whitespaces).isEmpty
            && (contribution.isEmpty || (amount ?? -1) >= 0)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: BrandScale.spaceXl) {
                    Text("Agree the exact addresses, the time, and what the ride costs. The other "
                        + "person confirms it, and the amount becomes the ride's split.")
                        .font(BrandFont.body())
                        .foregroundColor(Brand.textSecondary)

                    FormSection(title: "Where") {
                        LocationAutocompleteField(
                            title: "Exact pickup address",
                            placeholder: "e.g. 360 Huntington Ave, Boston",
                            selection: $pickup,
                            viewModel: viewModel,
                            accent: Brand.primary,
                            leadingSystemImage: "location.fill",
                            accessibilityID: "propose_location_input"
                        )
                        LocationAutocompleteField(
                            title: "Exact drop-off address",
                            placeholder: "e.g. 700 Commonwealth Ave, Boston",
                            selection: $dropoff,
                            viewModel: viewModel,
                            bias: pickup,
                            accent: Brand.primary,
                            leadingSystemImage: "mappin.circle.fill",
                            accessibilityID: "propose_dropoff_input"
                        )
                    }
                    FormSection(title: "When") {
                        BrandTextField(
                            title: "Pickup time",
                            placeholder: "e.g. 5:45 PM or in 10 mins",
                            text: $time,
                            icon: "clock.fill",
                            accessibilityID: "propose_time_input"
                        )
                    }
                    FormSection(title: "Cost") {
                        BrandTextField(
                            title: "Rider's share",
                            placeholder: "0.00",
                            text: $contribution,
                            icon: "dollarsign.circle.fill",
                            keyboard: .decimalPad,
                            accessibilityID: "propose_contribution_input"
                        )
                        Text(contribution.isEmpty || amount != nil
                            ? "Cash, settled in person when you meet"
                            : "Enter an amount like 12.50")
                            .font(BrandFont.eyebrow(.regular))
                            .foregroundColor(amount == nil && !contribution.isEmpty
                                ? Brand.danger
                                : Brand.textSecondary)
                    }

                    Button("Send proposal") {
                        onPropose(
                            pickup.name.trimmingCharacters(in: .whitespaces),
                            dropoff.name.trimmingCharacters(in: .whitespaces),
                            time.trimmingCharacters(in: .whitespaces),
                            amount ?? 0
                        )
                        presentationMode.wrappedValue.dismiss()
                    }
                    .buttonStyle(BrandButtonStyle(isEnabled: canSend))
                    .disabled(!canSend)
                }
                .padding(BrandScale.spaceXl)
            }
            .onAppear {
                // Once only: the sheet's body re-runs, and re-seeding would stamp on typing.
                guard !didPrefill else { return }
                didPrefill = true
                // Only seed a selection the field will actually display. An unresolved one (a ride
                // stored without coordinates) would leave a blank box above an enabled Send button.
                if initialPickup.isResolved { pickup = initialPickup }
                if initialDropoff.isResolved { dropoff = initialDropoff }
                if initialContribution > 0 {
                    contribution = String(format: "%.2f", initialContribution)
                }
            }
            .background(Brand.surface)
            .scrollContentBackground(.hidden)
            .navigationTitle("Propose a pickup")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
    }
}
