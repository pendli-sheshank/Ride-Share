import SwiftUI
import Shared

// The feed's cards, mirroring `TripOfferCard` and `RideRequestCard` on Android.

/// Which call to action a ride offer shows.
///
/// Android derives these in one place (`TripOfferList`) and passes the result down, rather than
/// letting each card work it out — otherwise "already joined" and "pending" disagree between the
/// feed and the detail screen.
enum OfferCTA {
    case join
    case pending
    case joined
    case ownTrip
    case unavailable
}

/// A ride offer in the browse feed.
///
/// `isWide` is passed in rather than measured here: a `GeometryReader` inside a lazy stack row is
/// greedy and destroys the row's intrinsic height, so the feed measures once and tells every card.
struct TripOfferCard: View {
    let offer: TripOffer
    var cta: OfferCTA = .join
    var isWide: Bool = false
    var onJoin: (() -> Void)?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Group {
                if isWide { wideBody } else { narrowBody }
            }
            .padding(isWide ? 18 : BrandScale.spaceLg)
            .background(Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusLg)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                    .stroke(Brand.outline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Layouts

    private var narrowBody: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
            HStack(spacing: BrandScale.spaceSm) {
                hostAvatar(size: 32)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(offer.hostName.isEmpty ? "Host" : offer.hostName)
                            .font(BrandFont.fixed(13, .bold))
                            .foregroundColor(Brand.textPrimary)
                            .lineLimit(1)
                        if offer.womenOnly { womenOnlyTag }
                    }
                    ratingRow
                }
                Spacer(minLength: 0)
                price(size: 18, captionSize: 9)
            }

            RouteIndicator(
                origin: offer.origin,
                destination: offer.destination,
                scale: .card,
                pins: true
            )

            metaRow

            Divider().background(Brand.outline)

            HStack(spacing: BrandScale.spaceSm) {
                Button("View Details", action: onTap)
                    .buttonStyle(BrandOutlineButtonStyle(height: 40))
                ctaView
            }
        }
    }

    private var wideBody: some View {
        HStack(alignment: .top, spacing: BrandScale.spaceLg) {
            VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                HStack(spacing: BrandScale.spaceSm) {
                    hostAvatar(size: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(offer.hostName.isEmpty ? "Host" : offer.hostName)
                                .font(BrandFont.body(.bold))
                                .foregroundColor(Brand.textPrimary)
                                .lineLimit(1)
                            if offer.womenOnly { womenOnlyTag }
                        }
                        ratingRow(includeVehicle: true)
                    }
                    Spacer(minLength: 0)
                }
                RouteIndicator(
                    origin: offer.origin,
                    destination: offer.destination,
                    scale: .card,
                    pins: true
                )
                metaRow
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: BrandScale.spaceSm) {
                price(size: 22, captionSize: 10)
                Button("View Details", action: onTap)
                    .buttonStyle(BrandOutlineButtonStyle(height: 40))
                ctaView
            }
            .frame(width: 170)
        }
    }

    // MARK: Pieces

    private func hostAvatar(size: CGFloat) -> some View {
        ZStack {
            Circle().fill(Brand.primaryContainer)
            Text(String((offer.hostName.isEmpty ? "H" : offer.hostName).prefix(1)).uppercased())
                .font(.system(size: size * 0.45, weight: .bold))
                .foregroundColor(Brand.onPrimaryContainer)
        }
        .frame(width: size, height: size)
    }

    private var womenOnlyTag: some View {
        Text("WOMEN ONLY")
            .font(BrandFont.fixed(8, .bold))
            .foregroundColor(BrandLiteral.womenOnlyTag)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(BrandLiteral.womenOnlyTag.opacity(0.2))
            .cornerRadius(4)
    }

    private var ratingRow: some View { ratingRow(includeVehicle: false) }

    private func ratingRow(includeVehicle: Bool) -> some View {
        HStack(spacing: 3) {
            Image(systemName: "star.fill")
                .font(.system(size: 12))
                .foregroundColor(Brand.warning)
            Text(TripFormat.rating(offer.hostRating))
                .font(BrandFont.eyebrow(.regular))
                .foregroundColor(Brand.textSecondary)
            if includeVehicle && !offer.vehicleInfo.isEmpty {
                Text("• \(offer.vehicleInfo)")
                    .font(BrandFont.eyebrow(.regular))
                    .foregroundColor(Brand.textSecondary)
                    .lineLimit(1)
            }
        }
    }

    private func price(size: CGFloat, captionSize: CGFloat) -> some View {
        VStack(alignment: .trailing, spacing: 0) {
            Text(TripFormat.moneyShort(offer.costPerRider))
                .font(.system(size: size, weight: .black))
                .foregroundColor(Brand.primary)
            Text("per rider")
                .font(BrandFont.fixed(captionSize))
                .foregroundColor(Brand.textSecondary)
        }
    }

    private var metaRow: some View {
        HStack(spacing: BrandScale.spaceMd) {
            HStack(spacing: 4) {
                Image(systemName: "clock").font(.system(size: 12))
                Text(TripFormat.card(offer.departureTime))
                    .font(BrandFont.eyebrow(.regular))
            }
            .foregroundColor(Brand.textSecondary)

            HStack(spacing: 4) {
                Image(systemName: "chair.lounge.fill").font(.system(size: 12))
                Text("\(offer.seatsLeft) of \(offer.totalSeats) seats open")
                    .font(BrandFont.eyebrow(.bold))
            }
            .foregroundColor(Brand.success)

            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var ctaView: some View {
        switch cta {
        case .join:
            Button {
                onJoin?()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                    Text("Join Ride")
                }
            }
            .buttonStyle(BrandButtonStyle(height: 40))
            .accessibilityIdentifier("card_join_button_\(offer.id)")

        case .pending:
            ctaChip("Pending Approval", icon: "hourglass",
                    tint: BrandLiteral.pendingAmber, fill: BrandLiteral.pendingAmber.opacity(0.12))
        case .joined:
            ctaChip("Joined", icon: "checkmark.circle.fill",
                    tint: Brand.success, fill: Brand.success.opacity(0.12))
        case .ownTrip:
            ctaChip("Your Trip", icon: "car.fill",
                    tint: Brand.primary, fill: Brand.primaryContainer.opacity(0.5))
        case .unavailable:
            ctaChip("Ride is Full", icon: "nosign",
                    tint: Brand.textSecondary, fill: Brand.outline.opacity(0.5))
        }
    }

    private func ctaChip(_ label: String, icon: String, tint: Color, fill: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 12))
            Text(label).font(BrandFont.caption(.bold))
        }
        .foregroundColor(tint)
        .frame(maxWidth: .infinity)
        .frame(height: 40)
        .background(fill)
        .cornerRadius(BrandScale.radiusMd)
    }
}

/// A rider's open request, as a host sees it in the Explore feed.
struct RideRequestCard: View {
    let request: RideRequest
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                HStack(spacing: BrandScale.spaceSm) {
                    ZStack {
                        Circle().fill(Brand.primary.opacity(0.2))
                        Text(String((request.riderName.isEmpty ? "R" : request.riderName).prefix(1)).uppercased())
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Brand.primary)
                    }
                    .frame(width: 32, height: 32)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(request.riderName.isEmpty ? "Rider" : request.riderName)
                            .font(BrandFont.fixed(13, .bold))
                            .foregroundColor(Brand.textPrimary)
                        HStack(spacing: 3) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 11))
                                .foregroundColor(Brand.warning)
                            Text(TripFormat.rating(request.riderRating))
                                .font(BrandFont.eyebrow(.regular))
                                .foregroundColor(Brand.textSecondary)
                        }
                    }

                    Spacer(minLength: 0)

                    Text("\(request.seatsNeeded) Seat\(request.seatsNeeded == 1 ? "" : "s")")
                        .font(BrandFont.eyebrow(.bold))
                        .foregroundColor(Brand.primary)
                        .padding(.horizontal, BrandScale.spaceSm)
                        .padding(.vertical, 4)
                        .background(Brand.primary.opacity(0.15))
                        .cornerRadius(6)
                }

                RouteIndicator(
                    origin: request.origin,
                    destination: request.destination,
                    scale: .card,
                    pins: true
                )

                HStack(spacing: BrandScale.spaceMd) {
                    HStack(spacing: 4) {
                        Image(systemName: "clock").font(.system(size: 12))
                        Text(TripFormat.card(request.departureTime))
                            .font(BrandFont.eyebrow(.regular))
                    }
                    .foregroundColor(Brand.textSecondary)

                    if request.womenOnly {
                        HStack(spacing: 4) {
                            Image(systemName: "person.fill").font(.system(size: 11))
                            Text("Women Only").font(BrandFont.fixed(10, .bold))
                        }
                        .foregroundColor(Brand.accent)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Brand.accent.opacity(0.15))
                        .cornerRadius(6)
                    }

                    Spacer(minLength: 0)
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

/// One of the three figures on the host analytics screen.
struct HostStatCard: View {
    let label: String
    let value: String
    let systemImage: String

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: systemImage)
                .font(.system(size: 24))
                .foregroundColor(Brand.primary)
            Text(value)
                .font(BrandFont.title(.bold))
                .foregroundColor(Brand.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(BrandFont.fixed(10))
                .foregroundColor(Brand.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(BrandScale.spaceMd)
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusMd)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                .stroke(Brand.outline, lineWidth: 1)
        )
    }
}
