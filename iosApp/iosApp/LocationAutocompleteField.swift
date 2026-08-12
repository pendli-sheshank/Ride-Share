import SwiftUI
import Shared

/// A resolved place: a name plus the coordinates the backend validation requires.
struct PlaceSelection: Equatable {
    var name = ""
    var lat = 0.0
    var lon = 0.0

    var isResolved: Bool { !name.isEmpty && lat != 0.0 && lon != 0.0 }
}

/// One row in the suggestion dropdown.
///
/// Carries its own identity. The previous implementation keyed `ForEach` on `formattedAddress`,
/// and Photon regularly returns two distinct points of interest sharing one address — duplicate
/// `ForEach` IDs make SwiftUI drop rows and log animation warnings. `id: \.self` is not an option
/// either: `RankedPlace` is a Kotlin/Native class whose hashing is object identity.
struct PlaceSuggestion: Identifiable {
    let id = UUID()
    let name: String
    let address: String
    let category: String
    let lat: Double
    let lon: Double
    /// "0.4 mi away", or empty when there was no location to measure from.
    let distanceText: String

    init(_ ranked: RankedPlace) {
        name = ranked.name
        address = ranked.formattedAddress
        category = ranked.type
        lat = ranked.lat
        lon = ranked.lon
        distanceText = ranked.distanceText
    }

    init(_ place: LocationPlace) {
        name = place.name
        address = place.address
        category = place.category
        lat = place.lat
        lon = place.lng
        distanceText = ""
    }

    /// The tile Android draws beside each result, by category.
    var icon: String {
        switch category {
        case "Campus": return "graduationcap.fill"
        case "Airport": return "airplane"
        case "Transit": return "bus.fill"
        case "Neighborhood": return "building.2.fill"
        default: return "mappin.and.ellipse"
        }
    }

    var iconTint: Color {
        switch category {
        case "Campus": return Brand.info
        case "Airport": return Brand.warning
        case "Transit": return Brand.success
        case "Neighborhood": return BrandLiteral.neighborhood
        default: return BrandLiteral.osmSuggestion
        }
    }
}

/// The address field with an overlaid suggestion dropdown, matching Android's
/// `LocationAutoCompleteTextField`.
///
/// Two bugs are fixed relative to the `PlaceField` this replaces:
///
/// 1. **Suggestions used to be unpickable.** They were default-styled `Button`s inside a `Form`
///    row, and SwiftUI collapses those into a single row-wide tap target — so tapping one result
///    could activate another. They are now `.buttonStyle(.plain)` inside a `FormSection`, which is
///    a plain `VStack` rather than a `Form`.
/// 2. **The dropdown used to push the form down** as results arrived. It is now an overlay, so
///    the fields below stay where the user last saw them.
///
/// The 250 ms debounce, the 2-character minimum, the task cancellation and the
/// `suppressNextChange` guard are carried over unchanged — each exists for a reason recorded below.
struct LocationAutocompleteField: View {
    let title: String
    var placeholder: String = ""
    @Binding var selection: PlaceSelection
    @ObservedObject var viewModel: AppViewModel
    /// A fallback anchor, used only when there is no location fix — a home address, or an
    /// already-resolved origin when this field is the destination.
    var bias: PlaceSelection? = nil
    var accent: Color = Brand.success
    var leadingSystemImage: String = "mappin.circle.fill"
    var accessibilityID: String?

    @ObservedObject private var deviceLocation = DeviceLocationProvider.shared
    @State private var isResolvingCurrentLocation = false
    @State private var query = ""
    @State private var results: [PlaceSuggestion] = []
    @State private var searchTask: Task<Void, Never>?
    @State private var isSearching = false
    @State private var isShowingResults = false
    /// `query` is also set programmatically — tapping a result, and the `onAppear` prefill — and
    /// `.onChange(of:)` fires for those writes exactly like a keystroke. Without this flag,
    /// setting `query` right after setting `selection` immediately wiped `selection` back out and
    /// re-triggered a search, making it impossible to ever keep a chosen place.
    @State private var suppressNextChange = false

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
            Text(title)
                .font(BrandFont.eyebrow(.semibold))
                .foregroundColor(Brand.textSecondary)

            HStack(spacing: BrandScale.spaceSm) {
                Image(systemName: leadingSystemImage)
                    .foregroundColor(accent)
                TextField(placeholder.isEmpty ? title : placeholder, text: $query)
                    .textFieldStyle(.plain)
                    .foregroundColor(Brand.textPrimary)
                    .accessibilityIdentifier(accessibilityID ?? "")
                    .onChange(of: query) { newValue in
                        if suppressNextChange {
                            suppressNextChange = false
                            return
                        }
                        isShowingResults = true
                        // Asked here rather than at launch, so the prompt lands with an address
                        // box on screen explaining why it wants a location.
                        deviceLocation.requestIfNeeded()
                        scheduleSearch(for: newValue)
                    }
                if isSearching {
                    ProgressView().scaleEffect(0.7)
                } else if !query.isEmpty {
                    Button {
                        setQueryProgrammatically("")
                        selection = PlaceSelection()
                        results = defaultSuggestions(matching: "")
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Brand.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(BrandScale.spaceMd)
            .background(Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusMd)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                    .stroke(selection.isResolved ? accent : Brand.outline, lineWidth: 1)
            )

            if selection.isResolved {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 12))
                    Text(selection.name).lineLimit(1)
                }
                .font(BrandFont.eyebrow())
                .foregroundColor(accent)
            }
        }
        // The dropdown overlays whatever is below rather than displacing it.
        .overlay(alignment: .topLeading) {
            if isShowingResults && !results.isEmpty {
                dropdown
                    .padding(.top, 74)
                    .zIndex(1)
            }
        }
        .onAppear {
            // A prefilled selection needs the field to show it, or the address looks lost.
            if query.isEmpty && selection.isResolved { setQueryProgrammatically(selection.name) }
            if results.isEmpty { results = defaultSuggestions(matching: "") }
        }
        // A fix arriving after the user has already typed re-runs the search, so the list reorders
        // itself rather than staying in whatever order it had before location was granted.
        .onChange(of: deviceLocation.coordinate?.latitude) { _ in
            guard query.trimmingCharacters(in: .whitespaces).count >= 2 else { return }
            scheduleSearch(for: query)
        }
    }

    /// Fills the field with wherever the user is, reverse-geocoded to a street address.
    ///
    /// Android has had this chip the whole time with Northeastern's campus hardcoded into it; iOS
    /// omitted it entirely. Both now read the real location.
    private var currentLocationChip: some View {
        Button {
            guard !isResolvingCurrentLocation else { return }
            guard let fix = deviceLocation.coordinate else {
                deviceLocation.requestIfNeeded()
                return
            }
            isResolvingCurrentLocation = true
            Task {
                let resolved = try? await OsmLocationService.companion.reverseGeocodeNominatim(
                    lat: fix.latitude,
                    lon: fix.longitude
                )
                let name = resolved?.road ?? resolved?.displayName ?? "My current location"
                selection = PlaceSelection(name: name, lat: fix.latitude, lon: fix.longitude)
                setQueryProgrammatically(name)
                results = []
                isShowingResults = false
                isResolvingCurrentLocation = false
            }
        } label: {
            HStack(spacing: 4) {
                if isResolvingCurrentLocation {
                    ProgressView().scaleEffect(0.5)
                    Text("Finding you…")
                } else {
                    Image(systemName: "location.fill").font(.system(size: 10))
                    Text("Use my location")
                }
            }
            .font(BrandFont.fixed(10, .bold))
            .foregroundColor(Brand.success)
            .padding(.horizontal, BrandScale.spaceSm)
            .padding(.vertical, 4)
            .background(Brand.success.opacity(0.15))
            .cornerRadius(BrandScale.radiusSm)
        }
        .buttonStyle(.plain)
    }

    private var dropdown: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(headerLabel)
                    .font(BrandFont.fixed(10, .bold))
                    .foregroundColor(headerTint)
                Spacer(minLength: BrandScale.spaceSm)
                currentLocationChip
            }
            .padding(.horizontal, BrandScale.spaceMd)
            .padding(.vertical, BrandScale.spaceSm)

            Divider()

            ForEach(results.prefix(6)) { place in
                Button {
                    choose(place)
                } label: {
                    HStack(spacing: BrandScale.spaceMd) {
                        ZStack {
                            Circle().fill(place.iconTint.opacity(0.2))
                            Image(systemName: place.icon)
                                .font(.system(size: 14))
                                .foregroundColor(place.iconTint)
                        }
                        .frame(width: 32, height: 32)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(place.name)
                                .font(BrandFont.fixed(13, .semibold))
                                .foregroundColor(Brand.textPrimary)
                                .lineLimit(1)
                            Text(place.address)
                                .font(BrandFont.eyebrow(.regular))
                                .foregroundColor(Brand.textSecondary)
                                .lineLimit(1)
                        }

                        Spacer(minLength: 0)

                        // Shown so the ordering is visibly justified rather than looking arbitrary.
                        if !place.distanceText.isEmpty {
                            Text(place.distanceText)
                                .font(BrandFont.eyebrow(.semibold))
                                .foregroundColor(Brand.textSecondary)
                                .fixedSize()
                        }
                    }
                    .padding(.horizontal, BrandScale.spaceMd)
                    .padding(.vertical, BrandScale.spaceSm)
                    .contentShape(Rectangle())
                }
                // Without this the enclosing row swallows every suggestion into one tap target.
                .buttonStyle(.plain)
            }
        }
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusMd)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                .stroke(Brand.outline, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.12), radius: 6, y: 3)
    }

    private var isShowingDefaults: Bool {
        query.trimmingCharacters(in: .whitespaces).count < 2
    }

    /// Names what the list is, not which vendor produced it — a rider has no use for the word
    /// "Photon". Matches Android word for word.
    private var headerLabel: String {
        if isShowingDefaults { return "POPULAR CAMPUS & TRANSIT SPOTS" }
        return deviceLocation.coordinate == nil ? "SEARCH RESULTS" : "NEAREST FIRST"
    }

    private var headerTint: Color {
        isShowingDefaults ? Brand.primary : BrandLiteral.osmSuggestion
    }

    private func choose(_ place: PlaceSuggestion) {
        searchTask?.cancel()
        selection = PlaceSelection(name: place.name, lat: place.lat, lon: place.lon)
        setQueryProgrammatically(place.name)
        results = []
        isShowingResults = false
        isSearching = false
    }

    private func setQueryProgrammatically(_ newValue: String) {
        suppressNextChange = true
        query = newValue
    }

    /// Same shape as Android's `LocationAutoCompleteTextField`: a 2-char minimum and a 250 ms
    /// debounce before hitting Photon, so a fast typist fires one request per pause instead of
    /// one per keystroke. A bare `Task` per keystroke had no cancellation, so a slow response for
    /// an early, short query could overwrite a later, more specific one — this cancels the
    /// in-flight task before starting a new one.
    private func scheduleSearch(for newValue: String) {
        selection = PlaceSelection()
        searchTask?.cancel()
        let trimmed = newValue.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else {
            results = defaultSuggestions(matching: trimmed)
            isSearching = false
            return
        }
        // The device's own location wins; `bias` — an already-resolved origin, or the home address
        // — is only the fallback. "Nearest first" means nearest to the person typing.
        let anchor = deviceLocation.coordinate.map { ($0.latitude, $0.longitude) }
            ?? bias.flatMap { $0.isResolved ? ($0.lat, $0.lon) : nil }
            ?? (0.0, 0.0)

        searchTask = Task {
            isSearching = true
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            let fetched = await viewModel.searchPlacesRanked(
                trimmed,
                fromLat: anchor.0,
                fromLon: anchor.1
            )
            guard !Task.isCancelled else { return }
            let mapped = fetched.map(PlaceSuggestion.init)
            results = mapped.isEmpty ? defaultSuggestions(matching: trimmed) : mapped
            isSearching = false
        }
    }

    /// Falls back to the same seed list Android shows for a blank query or no Photon results —
    /// `DEFAULT_LOCATION_PLACES`, a top-level `val` in `shared/commonMain/.../Models.kt`.
    private func defaultSuggestions(matching query: String) -> [PlaceSuggestion] {
        let all = ModelsKt.DEFAULT_LOCATION_PLACES.map(PlaceSuggestion.init)
        guard !query.isEmpty else { return Array(all.prefix(6)) }
        return all.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
                $0.address.localizedCaseInsensitiveContains(query) ||
                $0.category.localizedCaseInsensitiveContains(query)
        }
    }
}
