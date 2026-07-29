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

    private var isHost: Bool { viewModel.currentUser?.id == match.hostId }
    private var offer: TripOffer? { viewModel.offer(for: match) }

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
            ProposePickupSheet { spot, time in
                Task {
                    await viewModel.sendPickupMessage(
                        matchId: match.id,
                        type: MessageType.shared.PICKUP_PROPOSAL,
                        spot: spot,
                        time: time
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
                            onConfirm: { spot, time in
                                Task {
                                    await viewModel.sendPickupMessage(
                                        matchId: match.id,
                                        type: MessageType.shared.PICKUP_CONFIRMED,
                                        spot: spot,
                                        time: time
                                    )
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
    let onConfirm: (String, String) -> Void

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

    private var proposal: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
            Label("Proposed pickup", systemImage: "mappin.and.ellipse")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Brand.primary)

            Text("📍 \(message.spot)").font(.callout).foregroundColor(Brand.textPrimary)
            Text("⏰ \(message.time)").font(.callout).foregroundColor(Brand.textPrimary)

            if isMine {
                Text("Awaiting confirmation…")
                    .font(.caption2)
                    .foregroundColor(Brand.textSecondary)
            } else {
                Button("Accept & confirm") { onConfirm(message.spot, message.time) }
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.onPrimary)
                    .padding(.horizontal, BrandScale.spaceMd)
                    .padding(.vertical, BrandScale.spaceSm)
                    .background(Brand.success)
                    .cornerRadius(BrandScale.radiusSm)
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

            Text("Meet at \(message.spot) at \(message.time)")
                .font(.callout)
                .foregroundColor(Brand.textPrimary)
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
    @State private var spot = ""
    @State private var time = ""

    let onPropose: (String, String) -> Void

    var body: some View {
        NavigationView {
            Form {
                Section("Where") {
                    TextField("e.g. the main entrance", text: $spot)
                }
                Section("When") {
                    TextField("e.g. 8:15 am", text: $time)
                }

                Button("Send proposal") {
                    onPropose(spot, time)
                    presentationMode.wrappedValue.dismiss()
                }
                .disabled(spot.isEmpty || time.isEmpty)
            }
            .navigationTitle("Propose a pickup")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
    }
}
