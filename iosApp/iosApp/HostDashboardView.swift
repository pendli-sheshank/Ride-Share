import SwiftUI
import Shared

/// Android registers a `HostDashboard` route but never navigates to it (`navigate("host_dashboard")`
/// has zero call sites there) — so there is no Android screen to mirror pixel-for-pixel, only the
/// composable itself (`SplitCruiserApp.kt:2156-2353`) to match in spirit. This is iOS's own new
/// entry point, reached from a toolbar button on `MyRidesTabView`.
struct HostDashboardView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var filterStatus = "all"

    private static let filters: [(key: String, label: String)] = [
        ("all", "All rides"), ("active", "Active"), ("closed", "Closed"),
        ("completed", "Completed"), ("cancelled", "Cancelled"),
    ]

    private var activeRides: [TripOffer] {
        viewModel.hostedRides.filter { $0.status == "active" }
    }

    private var totalPassengers: Int {
        viewModel.hostedRides.reduce(0) { $0 + $1.passengers.count }
    }

    /// Named for what it is: what passengers have chipped in toward this host's costs. Calling
    /// it "Revenue" told a different story than the rest of the app, which frames every number as
    /// a cost split, not a fare.
    private var totalContributions: Double {
        viewModel.hostedRides.reduce(0) { $0 + $1.costPerRider * Double($1.totalSeats - $1.seatsLeft) }
    }

    private var filteredRides: [TripOffer] {
        let filtered: [TripOffer]
        switch filterStatus {
        case "active": filtered = viewModel.hostedRides.filter { $0.status == "active" || $0.status == "full" }
        case "closed": filtered = viewModel.hostedRides.filter { $0.status == "closed" }
        case "completed": filtered = viewModel.hostedRides.filter { $0.status == "completed" }
        case "cancelled": filtered = viewModel.hostedRides.filter { $0.status == "cancelled" }
        default: filtered = viewModel.hostedRides
        }
        return filtered.sorted { $0.departureTime > $1.departureTime }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: BrandScale.spaceLg) {
                statsRow
                filterChips

                if filteredRides.isEmpty {
                    BrandEmptyState(
                        icon: "car.fill",
                        title: "No hosted rides",
                        description: "You haven't posted any trip offers yet."
                    )
                } else {
                    VStack(spacing: BrandScale.spaceMd) {
                        ForEach(filteredRides, id: \.id) { offer in
                            HostedRideDashboardCard(viewModel: viewModel, offer: offer)
                        }
                    }
                }
            }
            .padding()
        }
        .background(Brand.surface.ignoresSafeArea())
        .navigationTitle("Host dashboard")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.refreshMyTrips() }
    }

    private var statsRow: some View {
        HStack(spacing: BrandScale.spaceMd) {
            HostStatCard(label: "Active rides", value: "\(activeRides.count)", icon: "car.fill")
            HostStatCard(label: "Total passengers", value: "\(totalPassengers)", icon: "person.2.fill")
            HostStatCard(label: "Chipped in", value: String(format: "$%.2f", totalContributions), icon: "dollarsign.circle.fill")
        }
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: BrandScale.spaceSm) {
                ForEach(Self.filters, id: \.key) { filter in
                    Button(filter.label) { filterStatus = filter.key }
                        .font(.caption)
                        .fontWeight(.bold)
                        .padding(.horizontal, BrandScale.spaceMd)
                        .padding(.vertical, BrandScale.spaceSm)
                        .background(filterStatus == filter.key ? Brand.primary : Brand.surfaceMuted)
                        .foregroundColor(filterStatus == filter.key ? Brand.onPrimary : Brand.textPrimary)
                        .cornerRadius(BrandScale.radiusLg)
                }
            }
        }
    }
}

private struct HostStatCard: View {
    let label: String
    let value: String
    let icon: String

    var body: some View {
        VStack(spacing: BrandScale.spaceXs) {
            Image(systemName: icon).foregroundColor(Brand.primary)
            Text(value)
                .font(.headline)
                .foregroundColor(Brand.textPrimary)
            Text(label)
                .font(.caption2)
                .foregroundColor(Brand.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(BrandScale.spaceMd)
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusMd)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusMd).stroke(Brand.outline, lineWidth: 1)
        )
    }
}

private struct HostedRideDashboardCard: View {
    @ObservedObject var viewModel: AppViewModel
    let offer: TripOffer

    var body: some View {
        NavigationLink(destination: RideDetailView(viewModel: viewModel, offer: offer)) {
            VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
                HStack {
                    Text("HOSTED RIDE")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(Brand.primary)
                    Spacer()
                    StatusBadge(status: offer.status)
                }

                RouteIndicator(origin: offer.origin, destination: offer.destination)

                Divider()

                HStack {
                    DetailRow(label: "Departure", value: RideDetailView.formatTime(offer.departureTime))
                }
                DetailRow(label: "Seats occupied", value: "\(offer.totalSeats - offer.seatsLeft) / \(offer.totalSeats)")
            }
            .padding(BrandScale.spaceLg)
            .background(Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusLg)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusLg).stroke(Brand.outline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}
