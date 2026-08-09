import SwiftUI
import Shared

// The My-trips tab's cards. Android builds all four from the same `CardEyebrow` + `RouteIndicator`
// + `CardStat` vocabulary, which is why they line up with each other.

/// A ride the current user is hosting, with the host's two real controls.
struct HostedRideScheduleCard: View {
    let offer: TripOffer
    let onTap: () -> Void
    let onStatusChange: (String) -> Void

    private var controls: HostControlsAvailability {
        HostControlsPolicy.shared.availability(offer: offer)
    }

    private var seatsTaken: Int32 { offer.totalSeats - offer.seatsLeft }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                HStack {
                    CardEyebrow(label: "Hosted ride", systemImage: "car.fill", tint: Brand.primary)
                    Spacer()
                    StatusBadge(status: offer.status)
                }

                RouteIndicator(origin: offer.origin, destination: offer.destination, scale: .card)

                Divider().background(Brand.outline)

                HStack(alignment: .top) {
                    CardStat(label: "Departure", value: TripFormat.card(offer.departureTime))
                    CardStat(
                        label: "Seats occupied",
                        value: "\(seatsTaken) / \(offer.totalSeats)",
                        alignment: .trailing
                    )
                }

                if !offer.passengerNames.isEmpty {
                    Divider().background(Brand.outline)
                    Text("PASSENGERS")
                        .font(BrandFont.fixed(10, .bold))
                        .foregroundColor(Brand.textSecondary)
                    passengerChips
                }

                if controls.canComplete || controls.canCancel {
                    HStack(spacing: BrandScale.spaceSm) {
                        if controls.canCancel {
                            Button("Cancel ride") { onStatusChange("cancelled") }
                                .buttonStyle(BrandOutlineButtonStyle(tint: Brand.danger, height: 40))
                                .accessibilityIdentifier("host_status_cancelled_btn")
                        }
                        if controls.canComplete {
                            Button("Complete ride") { onStatusChange("completed") }
                                .buttonStyle(BrandButtonStyle(background: Brand.success, height: 40))
                                .accessibilityIdentifier("host_status_completed_btn")
                        }
                    }
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
        .buttonStyle(.plain)
    }

    private var passengerChips: some View {
        // A simple wrapping row; the passenger count is small enough that a lazy grid would be
        // more machinery than the problem needs.
        HStack(spacing: BrandScale.spaceSm) {
            ForEach(Array(offer.passengerNames.prefix(4).enumerated()), id: \.offset) { _, name in
                Text(name)
                    .font(BrandFont.eyebrow(.bold))
                    .foregroundColor(Brand.onPrimaryContainer)
                    .padding(.horizontal, BrandScale.spaceSm)
                    .padding(.vertical, BrandScale.spaceXs)
                    .background(Brand.primaryContainer.opacity(0.2))
                    .cornerRadius(BrandScale.radiusMd)
                    .overlay(
                        RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                            .stroke(Brand.primaryContainer.opacity(0.4), lineWidth: 1)
                    )
                    .lineLimit(1)
            }
            if offer.passengerNames.count > 4 {
                Text("+\(offer.passengerNames.count - 4)")
                    .font(BrandFont.eyebrow(.bold))
                    .foregroundColor(Brand.textSecondary)
            }
            Spacer(minLength: 0)
        }
    }
}

/// A ride the current user has a seat on.
struct JoinedRideScheduleCard: View {
    let offer: TripOffer
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                HStack {
                    CardEyebrow(label: "Joined ride", systemImage: "car.fill", tint: Brand.primary)
                    Spacer()
                    StatusBadge(status: offer.status)
                }

                RouteIndicator(origin: offer.origin, destination: offer.destination, scale: .card)

                Divider().background(Brand.outline)

                HStack(alignment: .top) {
                    CardStat(label: "Host", value: offer.hostName.isEmpty ? "Your host" : offer.hostName)
                    CardStat(
                        label: "Departure",
                        value: TripFormat.card(offer.departureTime),
                        alignment: .center
                    )
                    CardStat(
                        label: "Contribution",
                        value: TripFormat.money(offer.costPerRider),
                        alignment: .trailing,
                        valueColor: Brand.primary
                    )
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
        .buttonStyle(.plain)
    }
}

/// A finished ride. Deliberately faded — it is reference, not something to act on.
struct PastRideCard: View {
    let offer: TripOffer
    let currentUserId: String
    let onTap: () -> Void

    private var wasHost: Bool { offer.hostId == currentUserId }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
                HStack {
                    CardEyebrow(
                        label: wasHost ? "Past hosted" : "Past joined",
                        systemImage: wasHost ? "car.fill" : "clock.arrow.circlepath",
                        tint: Brand.textSecondary,
                        compact: true
                    )
                    Spacer()
                    StatusBadge(status: offer.status, compact: true)
                }

                RouteIndicator(
                    origin: offer.origin,
                    destination: offer.destination,
                    scale: .card,
                    muted: true
                )

                Divider().background(Brand.outline.opacity(0.3))

                HStack(alignment: .top) {
                    CardStat(
                        label: "Date & time",
                        value: TripFormat.past(offer.departureTime),
                        valueColor: Brand.textPrimary.opacity(0.7)
                    )
                    CardStat(
                        label: "Your role",
                        value: wasHost ? "Driver" : "Passenger (with \(offer.hostName))",
                        alignment: .trailing,
                        valueColor: Brand.primary.opacity(0.8)
                    )
                }
            }
            .padding(14)
            .background(Brand.surfaceCard.opacity(0.6))
            .cornerRadius(BrandScale.radiusLg)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                    .stroke(Brand.outline.opacity(0.5), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

/// One of the current user's own open ride requests.
struct MyRideRequestCard: View {
    let request: RideRequest
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
            HStack {
                CardEyebrow(label: "My ride request", systemImage: "car.fill", tint: Brand.success)
                Spacer()
                StatusBadge(status: request.status)
            }

            RouteIndicator(origin: request.origin, destination: request.destination, scale: .card)

            Divider().background(Brand.outline)

            HStack(alignment: .top) {
                CardStat(label: "Preferred departure", value: TripFormat.card(request.departureTime))
                CardStat(
                    label: "Seats needed",
                    value: "\(request.seatsNeeded)",
                    alignment: .trailing
                )
            }

            if !request.notes.isEmpty {
                Divider().background(Brand.outline)
                Text("NOTES")
                    .font(BrandFont.fixed(10, .bold))
                    .foregroundColor(Brand.textSecondary)
                Text(request.notes)
                    .font(BrandFont.caption())
                    .foregroundColor(Brand.textPrimary)
            }

            if request.status.lowercased() == "active" {
                Button("Cancel request", action: onCancel)
                    .buttonStyle(BrandOutlineButtonStyle(tint: Brand.danger, height: 40))
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
