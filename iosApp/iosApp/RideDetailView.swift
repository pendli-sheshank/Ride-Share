import SwiftUI
import Shared

// Views are kept deliberately small and split into computed sub-views. Swift's type checker gives
// up on large ViewBuilder bodies ("unable to type-check this expression in reasonable time"), and
// that has already cost this project a macOS CI round trip once.

/// The screen where a rider decides whether to get into a stranger's car.
///
/// It used to be five label/value rows in a grey box — From, To, Cost, Seats, Host — while
/// Android's equivalent showed the host's rating and vehicle, a way to contact them, a cost
/// breakdown and who else was already on board. Exactly the information that matters most is what
/// was missing, so this screen is modelled on Android's `TripDetailScreen`.
struct RideDetailView: View {
    @ObservedObject var viewModel: AppViewModel
    let offer: TripOffer
    @Environment(\.presentationMode) private var presentationMode

    @State private var host: User?
    @State private var vehicle: Vehicle?
    @State private var isHostExpanded = false

    private var isOwnRide: Bool { viewModel.currentUser?.id == offer.hostId }
    private var isFull: Bool { offer.seatsLeft <= 0 }
    private var alreadyAboard: Bool {
        guard let me = viewModel.currentUser?.id else { return false }
        return offer.passengers.contains(me)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: BrandScale.spaceLg) {
                routeCard
                hostCard
                costCard
                seatsCard

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(Brand.danger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                actionButton
            }
            .padding(BrandScale.spaceLg)
        }
        .background(Brand.surface.ignoresSafeArea())
        .navigationTitle("Ride details")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            host = await viewModel.hostProfile(userId: offer.hostId)
            vehicle = await viewModel.vehicle(userId: offer.hostId)
        }
    }

    // MARK: - Route

    private var routeCard: some View {
        BrandCard(title: "Route") {
            RouteIndicator(
                origin: offer.origin,
                destination: offer.destination,
                originLabel: "PICKUP",
                destinationLabel: "DROPOFF"
            )

            if !offer.exitLocation.isEmpty {
                DetailRow(label: "Meeting spot", value: offer.exitLocation)
            }

            Divider()

            DetailRow(label: "Departs", value: Self.formatTime(offer.departureTime))

            if offer.womenOnly {
                HStack(spacing: BrandScale.spaceXs) {
                    Image(systemName: "person.fill.checkmark")
                    Text("Women-only ride")
                }
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Brand.accent)
            }
        }
    }

    // MARK: - Host

    /// The trust card: who is driving, how they are rated, what they drive, and how to reach
    /// them. Collapsed by default, like Android's.
    private var hostCard: some View {
        BrandCard(title: "Your host") {
            HStack(spacing: BrandScale.spaceMd) {
                BrandAvatar(
                    avatarUrl: host?.avatarUrl ?? "",
                    name: host?.name ?? offer.hostName,
                    size: 48
                )

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: BrandScale.spaceXs) {
                        Text(host?.displayName ?? offer.hostName)
                            .font(.headline)
                            .foregroundColor(Brand.textPrimary)

                        if host?.verifiedTier == "vouched" {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundColor(Brand.success)
                                .font(.caption)
                        }
                    }

                    Text(ratingSummary)
                        .font(.caption)
                        .foregroundColor(Brand.textSecondary)
                }

                Spacer()

                Button(isHostExpanded ? "Less" : "More") {
                    withAnimation { isHostExpanded.toggle() }
                }
                .font(.caption)
                .foregroundColor(Brand.primary)
            }

            if isHostExpanded {
                Divider()
                hostDetails
            }
        }
    }

    private var ratingSummary: String {
        guard let host, host.ratingCount > 0 else { return "No ratings yet" }
        return String(format: "⭐ %.1f · %d ride%@", host.ratingAvg, host.ratingCount,
                      host.ratingCount == 1 ? "" : "s")
    }

    @ViewBuilder
    private var hostDetails: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
            DetailRow(label: "Vehicle", value: vehicleDescription)

            // The phone number lives on the public user document precisely so this row can
            // exist. It is blank for anyone who onboarded before iOS started collecting it.
            if let phone = host?.phoneNumber, !phone.isEmpty {
                contactRow(icon: "phone.fill", label: phone, url: "tel://\(phone)")
            }

            if let email = host?.email, !email.isEmpty {
                contactRow(icon: "envelope.fill", label: email, url: "mailto:\(email)")
            }

            if host?.phoneNumber.isEmpty != false && host?.email.isEmpty != false {
                Text("No contact details shared yet — use the chat once you're matched.")
                    .font(.caption)
                    .foregroundColor(Brand.textSecondary)
            }
        }
    }

    private var vehicleDescription: String {
        if let vehicle, !vehicle.make.isEmpty {
            return "\(vehicle.color) \(vehicle.make) \(vehicle.model)".trimmingCharacters(in: .whitespaces)
        }
        return offer.vehicleInfo.isEmpty ? "Not specified" : offer.vehicleInfo
    }

    private func contactRow(icon: String, label: String, url: String) -> some View {
        Button {
            if let target = URL(string: url) { UIApplication.shared.open(target) }
        } label: {
            HStack(spacing: BrandScale.spaceSm) {
                Image(systemName: icon)
                Text(label).font(.callout)
                Spacer()
            }
            .foregroundColor(Brand.primary)
        }
    }

    // MARK: - Cost

    /// The same framing Android uses: what to bring, why that number, and how it is paid. This is
    /// a cost split between people going the same way, not a fare.
    private var costCard: some View {
        BrandCard(title: "Your share") {
            HStack(alignment: .firstTextBaseline) {
                Text(String(format: "$%.2f", offer.costPerRider))
                    .font(.largeTitle)
                    .fontWeight(.black)
                    .foregroundColor(Brand.primary)
                Text("suggested contribution")
                    .font(.caption)
                    .foregroundColor(Brand.textSecondary)
                Spacer()
            }

            Text("Cash, paid in person. Split Cruiser never takes a cut and never handles the money.")
                .font(.caption)
                .foregroundColor(Brand.textSecondary)
        }
    }

    // MARK: - Seats

    private var seatsCard: some View {
        BrandCard(title: "Seats") {
            HStack {
                DetailRow(
                    label: "Available",
                    value: "\(offer.seatsLeft) of \(offer.totalSeats)",
                    valueColor: isFull ? Brand.danger : Brand.success
                )
            }

            StatusBadge(status: offer.status)

            if !offer.passengerNames.isEmpty {
                Divider()
                Text("RIDING ALONG")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.textSecondary)

                // A plain wrapped list rather than a flow layout: `Layout` needs iOS 16 and this
                // app targets lower.
                ForEach(offer.passengerNames, id: \.self) { name in
                    HStack(spacing: BrandScale.spaceSm) {
                        Image(systemName: "person.circle.fill").foregroundColor(Brand.primary)
                        Text(name).font(.callout).foregroundColor(Brand.textPrimary)
                        Spacer()
                    }
                }
            }
        }
    }

    // MARK: - Action

    @ViewBuilder
    private var actionButton: some View {
        if isOwnRide {
            hostControls
        } else if alreadyAboard {
            Text("You've reserved a seat on this ride.")
                .font(.callout)
                .foregroundColor(Brand.success)
        } else {
            Button {
                Task {
                    // Reserves a seat, exactly as the Android "Join ride" button does.
                    if await viewModel.joinRide(offerId: offer.id) {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            } label: {
                Text(isFull ? "Full" : "Reserve a seat")
            }
            .buttonStyle(BrandButtonStyle(isEnabled: !isFull))
            .disabled(isFull || viewModel.isLoading)
        }
    }

    /// The two decisions left to a host once seats and departure time drive status automatically.
    /// `closed` still shows both — it means time ran out and this still needs a human answer, not
    /// that the ride is locked. Only `completed`/`cancelled` end it. See `HostControlsPolicy` in
    /// `:shared`, which both platforms call for this.
    @ViewBuilder
    private var hostControls: some View {
        let availability = HostControlsPolicy.shared.availability(offer: offer)
        if availability.canComplete || availability.canCancel {
            VStack(spacing: BrandScale.spaceSm) {
                if availability.canComplete {
                    Button("Complete") {
                        Task { await viewModel.updateOfferStatus(offerId: offer.id, newStatus: "completed") }
                    }
                    .buttonStyle(BrandButtonStyle(isEnabled: !viewModel.isLoading))
                }
                if availability.canCancel {
                    Button("Cancel ride") {
                        Task { await viewModel.updateOfferStatus(offerId: offer.id, newStatus: "cancelled") }
                    }
                    .font(.callout)
                    .foregroundColor(Brand.danger)
                }
            }
        } else {
            Text("This ride is \(offer.status).")
                .font(.callout)
                .foregroundColor(Brand.textSecondary)
        }
    }

    static func formatTime(_ timestamp: Int64) -> String {
        guard timestamp > 0 else { return "Time TBD" }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: Date(epochMillis: timestamp))
    }
}
