import SwiftUI
import Shared

// The two posting forms. Both were SwiftUI `Form`s whose submit rendered as small tinted text and
// whose fields sat on the system grouped background; they are now `FormSection` cards with a
// filled brand button, matching Android.
//
// Each form owns its own `isSubmitting` and `formError` rather than reading the shared
// `isLoading`/`errorMessage`. Those are global: a background refresh used to disable the submit
// button mid-typing and could clear an error before it had been read.

/// A host publishes a ride.
struct PostOfferScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    @State private var origin = PlaceSelection()
    @State private var destination = PlaceSelection()
    @State private var exitLocation = ""
    // Android seeds the offer form at now + 4 hours; iOS used one hour for both forms.
    @State private var departure = Date().addingTimeInterval(4 * 3600)
    @State private var seats = 4
    // What the whole trip costs, not what one rider pays — the app does the division. Matches the
    // Android form's seed.
    @State private var cost = "60.00"
    @State private var vehicleInfo = ""
    @State private var womenOnly = false
    @State private var formError: String?
    @State private var isSubmitting = false

    private var canSubmit: Bool {
        origin.isResolved && destination.isResolved && !isSubmitting
    }

    /// The split, recomputed as either field is typed into — `nil` until both parse.
    ///
    /// Goes through `:shared` so the previewed figure is byte-for-byte the one `postTripOffer`
    /// stores, rather than a Swift reimplementation that can drift from it.
    private var previewShare: Double? {
        guard let parsed = Double(cost), parsed > 0 else { return nil }
        return viewModel.perRiderShare(totalCost: parsed, totalSeats: seats)
    }

    /// Ranks the pickup search toward home, the way Android's post-offer form does.
    private var homeBias: PlaceSelection? {
        guard let home = viewModel.contactDetails, home.hasHomeLocation else { return nil }
        return PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BrandScale.spaceXl) {
                Text("Post a Trip Offer")
                    .font(BrandFont.headline())
                    .foregroundColor(Brand.textPrimary)

                FormSection(title: "Route") {
                    LocationAutocompleteField(
                        title: "Pickup Location (Origin)",
                        placeholder: "e.g. Mission Hill, Boston or Snell Library",
                        selection: $origin,
                        viewModel: viewModel,
                        bias: homeBias,
                        accent: Brand.success,
                        leadingSystemImage: "location.fill",
                        accessibilityID: "offer_origin_input"
                    )
                    LocationAutocompleteField(
                        title: "Dropoff Location (Destination)",
                        placeholder: "e.g. Logan Airport or NEU Campus",
                        selection: $destination,
                        viewModel: viewModel,
                        bias: origin,
                        accent: BrandLiteral.destinationOrange,
                        leadingSystemImage: "mappin.circle.fill",
                        accessibilityID: "offer_destination_input"
                    )
                    BrandTextField(
                        title: "Exact Meeting Spot (optional)",
                        placeholder: "e.g. North Gate, by the flagpole",
                        text: $exitLocation,
                        icon: "pin.fill",
                        accessibilityID: "offer_exit_location_input"
                    )
                }

                FormSection(title: "Trip") {
                    DatePicker(
                        "Departure",
                        selection: $departure,
                        in: Date()...,
                        displayedComponents: [.date, .hourAndMinute]
                    )
                    .font(BrandFont.body())
                    .foregroundColor(Brand.textPrimary)
                    .accessibilityIdentifier("offer_date_input")

                    HStack(spacing: BrandScale.spaceMd) {
                        BrandTextField(
                            title: "Total Trip Cost ($)",
                            placeholder: "60.00",
                            text: $cost,
                            icon: "dollarsign.circle.fill",
                            iconTint: Brand.warning,
                            keyboard: .decimalPad,
                            accessibilityID: "offer_cost_input"
                        )
                        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
                            Text("Seats Available")
                                .font(BrandFont.eyebrow(.semibold))
                                .foregroundColor(Brand.textSecondary)
                            Stepper("\(seats)", value: $seats, in: 1...8)
                                .font(BrandFont.body(.semibold))
                                .foregroundColor(Brand.textPrimary)
                                .accessibilityIdentifier("offer_seats_input")
                        }
                    }

                    // The split, shown as it is typed — the same preview Android renders under
                    // the same two fields.
                    if let previewShare {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(TripFormat.money(previewShare)) each")
                                .font(.system(size: 20, weight: .black))
                                .foregroundColor(Brand.primary)
                            // `seats + 1` says out loud that the driver pays a share too, which is
                            // the difference between a split and a fare.
                            Text("\(TripFormat.money(Double(cost) ?? 0)) split \(seats + 1) ways — \(seats) \(seats == 1 ? "rider" : "riders") and you.")
                                .font(BrandFont.caption())
                                .foregroundColor(Brand.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(BrandScale.spaceMd)
                        .background(Brand.surfaceMuted)
                        .cornerRadius(BrandScale.radiusMd)
                        .accessibilityIdentifier("offer_split_preview")
                    }

                    BrandTextField(
                        title: "Vehicle",
                        placeholder: "e.g. Blue Honda Civic",
                        text: $vehicleInfo,
                        icon: "car.fill"
                    )

                    womenOnlyToggle(
                        title: "Women-Only Trip Offer",
                        subtitle: "Only visible to other female riders"
                    )
                }

                if viewModel.vehicleForCurrentUser == nil {
                    infoCard(
                        // Do not promise a Profile vehicle editor: iOS has none. Vehicle details
                        // are collected once during onboarding and are unreachable afterwards, so
                        // "you can add a vehicle in Profile any time" sent people looking for a
                        // screen that does not exist. Tracked as a real parity gap rather than
                        // papered over in copy.
                        "You haven't set up your vehicle details, so this ride will show as a standard Sedan."
                    )
                }

                if let formError {
                    Text(formError)
                        .font(BrandFont.caption())
                        .foregroundColor(Brand.danger)
                }

                Button("Post ride offer") { submit() }
                    .buttonStyle(BrandButtonStyle(isEnabled: canSubmit))
                    .disabled(!canSubmit)
                    .accessibilityIdentifier("submit_offer_button")

                Spacer().frame(height: BrandScale.spaceXl)
            }
            .padding(BrandScale.spaceXl)
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
        .navigationTitle("Offer a ride")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: prefillFromHome)
    }

    @ViewBuilder
    private func womenOnlyToggle(title: String, subtitle: String) -> some View {
        Toggle(isOn: $womenOnly) {
            HStack(spacing: BrandScale.spaceSm) {
                Image(systemName: "person.fill").foregroundColor(Brand.accent)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(BrandFont.fixed(13, .bold))
                        .foregroundColor(Brand.textPrimary)
                    Text(subtitle)
                        .font(BrandFont.fixed(10))
                        .foregroundColor(Brand.textSecondary)
                }
            }
        }
        .tint(Brand.accent)
    }

    private func infoCard(_ text: String) -> some View {
        HStack(alignment: .top, spacing: BrandScale.spaceSm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(Brand.primary)
            Text(text)
                .font(BrandFont.eyebrow(.regular))
                .foregroundColor(Brand.textPrimary)
        }
        .padding(BrandScale.spaceMd)
        .background(Brand.primary.opacity(0.1))
        .cornerRadius(BrandScale.radiusMd)
    }

    /// A host's ride usually starts from home too, so prefill it the way Android does.
    private func prefillFromHome() {
        guard !origin.isResolved, let home = viewModel.contactDetails, home.hasHomeLocation else { return }
        origin = PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    private func submit() {
        formError = nil
        guard let totalCost = Double(cost), totalCost >= 0 else {
            formError = "Enter the trip cost as a number, like 60.00."
            return
        }
        guard totalCost <= viewModel.maxTripCost(totalSeats: seats) else {
            formError = "A \(seats)-seat ride can split at most $\(Int(viewModel.maxTripCost(totalSeats: seats)))."
            return
        }
        isSubmitting = true
        Task {
            let posted = await viewModel.postRideOffer(
                origin: origin.name,
                destination: destination.name,
                originLat: origin.lat,
                originLng: origin.lon,
                destLat: destination.lat,
                destLng: destination.lon,
                departureTime: departure,
                totalSeats: seats,
                totalCost: totalCost,
                womenOnly: womenOnly,
                vehicleInfo: vehicleInfo.isEmpty ? "Shared Sedan" : vehicleInfo,
                exitLocation: exitLocation
            )
            isSubmitting = false
            if posted {
                viewModel.notify("Ride offer posted successfully!")
                await viewModel.refreshMyTrips()
                router.pop()
            }
        }
    }
}

/// A rider publishes a request.
struct PostRequestScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    @State private var origin = PlaceSelection()
    @State private var destination = PlaceSelection()
    @State private var exitLocation = ""
    // Android seeds the request form at now + 6 hours.
    @State private var departure = Date().addingTimeInterval(6 * 3600)
    @State private var seatsNeeded = 1
    @State private var notes = ""
    @State private var womenOnly = false
    @State private var formError: String?
    @State private var isSubmitting = false

    private var canSubmit: Bool {
        origin.isResolved && destination.isResolved && !isSubmitting
    }

    private var homeBias: PlaceSelection? {
        guard let home = viewModel.contactDetails, home.hasHomeLocation else { return nil }
        return PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BrandScale.spaceXl) {
                Text("Post a Ride Request")
                    .font(BrandFont.headline())
                    .foregroundColor(Brand.textPrimary)

                FormSection(title: "Route") {
                    LocationAutocompleteField(
                        title: "Where to pick you up?",
                        placeholder: "e.g. Snell Library lobby or Ruggles Station",
                        selection: $origin,
                        viewModel: viewModel,
                        bias: homeBias,
                        accent: Brand.success,
                        leadingSystemImage: "location.fill",
                        accessibilityID: "request_origin_input"
                    )
                    LocationAutocompleteField(
                        title: "Where are you going?",
                        placeholder: "e.g. Logan International Airport",
                        selection: $destination,
                        viewModel: viewModel,
                        bias: origin,
                        accent: BrandLiteral.destinationOrange,
                        leadingSystemImage: "mappin.circle.fill",
                        accessibilityID: "request_destination_input"
                    )
                    BrandTextField(
                        title: "Exact Meeting Spot (optional)",
                        placeholder: "e.g. North Gate, by the flagpole",
                        text: $exitLocation,
                        icon: "pin.fill",
                        accessibilityID: "request_exit_location_input"
                    )
                }

                FormSection(title: "Trip") {
                    DatePicker(
                        "Preferred Departure Time",
                        selection: $departure,
                        in: Date()...,
                        displayedComponents: [.date, .hourAndMinute]
                    )
                    .font(BrandFont.body())
                    .foregroundColor(Brand.textPrimary)

                    VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
                        Text("Seats needed")
                            .font(BrandFont.eyebrow(.semibold))
                            .foregroundColor(Brand.textSecondary)
                        Stepper("\(seatsNeeded)", value: $seatsNeeded, in: 1...8)
                            .font(BrandFont.body(.semibold))
                            .foregroundColor(Brand.textPrimary)
                    }

                    BrandTextField(
                        title: "Notes for host",
                        placeholder: "e.g. 1 big suitcase. Can pay via Venmo/cash.",
                        text: $notes,
                        icon: "info.circle.fill",
                        iconTint: BrandLiteral.notesTeal
                    )

                    Toggle(isOn: $womenOnly) {
                        HStack(spacing: BrandScale.spaceSm) {
                            Image(systemName: "person.fill").foregroundColor(Brand.accent)
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Women-Only Request")
                                    .font(BrandFont.fixed(13, .bold))
                                    .foregroundColor(Brand.textPrimary)
                                Text("Only visible to other female hosts")
                                    .font(BrandFont.fixed(10))
                                    .foregroundColor(Brand.textSecondary)
                            }
                        }
                    }
                    .tint(Brand.accent)
                }

                if let formError {
                    Text(formError)
                        .font(BrandFont.caption())
                        .foregroundColor(Brand.danger)
                }

                Button("Post ride request") { submit() }
                    .buttonStyle(BrandButtonStyle(isEnabled: canSubmit))
                    .disabled(!canSubmit)
                    .accessibilityIdentifier("submit_request_button")

                Spacer().frame(height: BrandScale.spaceXl)
            }
            .padding(BrandScale.spaceXl)
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
        .navigationTitle("Request a ride")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: prefillFromHome)
    }

    private func prefillFromHome() {
        guard !origin.isResolved, let home = viewModel.contactDetails, home.hasHomeLocation else { return }
        origin = PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    private func submit() {
        formError = nil
        isSubmitting = true
        Task {
            let posted = await viewModel.postRideRequest(
                origin: origin.name,
                destination: destination.name,
                originLat: origin.lat,
                originLng: origin.lon,
                destLat: destination.lat,
                destLng: destination.lon,
                departureTime: departure,
                seatsNeeded: seatsNeeded,
                notes: notes,
                womenOnly: womenOnly,
                exitLocation: exitLocation
            )
            isSubmitting = false
            if posted {
                viewModel.notify("Ride request posted successfully!")
                router.pop()
            }
        }
    }
}

// MARK: - Field

/// A labelled text field on a card, matching Android's `OutlinedTextField` treatment.
struct BrandTextField: View {
    let title: String
    var placeholder: String = ""
    @Binding var text: String
    var icon: String?
    var iconTint: Color = Brand.primary
    var keyboard: UIKeyboardType = .default
    var accessibilityID: String?

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
            Text(title)
                .font(BrandFont.eyebrow(.semibold))
                .foregroundColor(Brand.textSecondary)
            HStack(spacing: BrandScale.spaceSm) {
                if let icon {
                    Image(systemName: icon).foregroundColor(iconTint)
                }
                TextField(placeholder.isEmpty ? title : placeholder, text: $text)
                    .textFieldStyle(.plain)
                    .keyboardType(keyboard)
                    .foregroundColor(Brand.textPrimary)
                    .accessibilityIdentifier(accessibilityID ?? "")
            }
            .padding(BrandScale.spaceMd)
            .background(Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusMd)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                    .stroke(Brand.outline, lineWidth: 1)
            )
        }
    }
}
