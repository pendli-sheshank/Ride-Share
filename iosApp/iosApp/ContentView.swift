import SwiftUI
import PhotosUI
import Shared

// Views are kept deliberately small and split into computed sub-views. Swift's type checker gives
// up on large ViewBuilder bodies ("unable to type-check this expression in reasonable time"), and
// that has already cost this project a macOS CI round trip once.
//
// Colours, spacing and the shared building blocks (`BrandCard`, `StatusBadge`, `RouteIndicator`,
// `BrandEmptyState`) live in Theme.swift and come from the same tokens Android reads. This file
// used to paint everything in system defaults — `.blue`, `.gray`, `systemGray6` — so the app had
// no visual identity on iOS at all.

struct ContentView: View {
    @StateObject private var viewModel = AppViewModel()

    var body: some View {
        Group {
            if !viewModel.isSignedIn {
                LoginView(viewModel: viewModel)
            } else if viewModel.needsProfileSetup {
                ProfileSetupView(viewModel: viewModel)
            } else {
                MainTabView(viewModel: viewModel)
            }
        }
        .tint(Brand.primary)
    }
}

struct MainTabView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeTabView(viewModel: viewModel)
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(0)

            MyRidesTabView(viewModel: viewModel)
                .tabItem { Label("My Rides", systemImage: "car.fill") }
                .tag(1)

            MatchesTabView(viewModel: viewModel)
                .tabItem { Label("Matches", systemImage: "person.2.fill") }
                .tag(2)

            ProfileTabView(viewModel: viewModel)
                .tabItem { Label("Profile", systemImage: "person.fill") }
                .tag(3)
        }
    }
}

// MARK: - Login

struct LoginView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isSigningUp = false
    @State private var validationError: String?

    private var header: some View {
        VStack(spacing: BrandScale.spaceMd) {
            Image(systemName: "car.fill")
                .font(.system(size: 48))
                .foregroundColor(Brand.primary)

            Text("Split Cruiser")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(Brand.textPrimary)

            Text("Rideshare, cost split")
                .font(.subheadline)
                .foregroundColor(Brand.textSecondary)
        }
        .padding(.top, 40)
    }

    private var form: some View {
        VStack(spacing: BrandScale.spaceLg) {
            // Email and password, matching Android. This screen used to ask for a phone number,
            // which no backend has ever authenticated against.
            TextField("Email", text: $email)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

            SecureField("Password", text: $password)
                .textFieldStyle(.roundedBorder)

            if isSigningUp {
                SecureField("Confirm password", text: $confirmPassword)
                    .textFieldStyle(.roundedBorder)

                // The same guidance Android shows inline. Firebase rejects anything shorter, and
                // finding that out from a server error after tapping is a worse way to learn it.
                Text("At least 6 characters.")
                    .font(.caption)
                    .foregroundColor(Brand.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            if !viewModel.isBackendConfigured {
                Text("This build has no Firebase configuration, so sign-in is unavailable.")
                    .font(.caption)
                    .foregroundColor(Brand.warning)
                    .multilineTextAlignment(.center)
            }

            if let error = validationError ?? viewModel.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundColor(Brand.danger)
                    .multilineTextAlignment(.center)
            }

            Button(action: submit) {
                if viewModel.isLoading {
                    ProgressView().frame(maxWidth: .infinity).padding()
                } else {
                    Text(isSigningUp ? "Create account" : "Log in")
                }
            }
            .buttonStyle(BrandButtonStyle(isEnabled: viewModel.isBackendConfigured))
            .disabled(viewModel.isLoading || !viewModel.isBackendConfigured)

            Button(isSigningUp ? "Already have an account? Log in"
                               : "New here? Create an account") {
                isSigningUp.toggle()
                validationError = nil
                viewModel.clearError()
            }
            .font(.callout)
            .foregroundColor(Brand.primary)
        }
        .padding()
    }

    var body: some View {
        VStack(spacing: BrandScale.spaceXl) {
            header
            Spacer()
            form
            Spacer()
        }
        .padding()
        .background(Brand.surface.ignoresSafeArea())
    }

    private func submit() {
        validationError = nil
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        let trimmedPassword = password.trimmingCharacters(in: .whitespaces)

        if isSigningUp {
            guard trimmedPassword.count >= 6 else {
                validationError = "Passwords need at least 6 characters."
                return
            }
            guard trimmedPassword == confirmPassword.trimmingCharacters(in: .whitespaces) else {
                validationError = "Those passwords don't match."
                return
            }
        }

        Task {
            if isSigningUp {
                await viewModel.signUp(email: trimmedEmail, password: trimmedPassword)
            } else {
                await viewModel.logIn(email: trimmedEmail, password: trimmedPassword)
            }
        }
    }
}

// MARK: - Profile setup

/// Onboarding, collecting the same fields Android does.
///
/// It used to ask for a name and a home area only. Everything downstream that depends on the rest
/// degraded silently: an Android rider matched with an iOS-onboarded host opened the contact card
/// to a blank phone row, and this app could not prefill a pickup because it had no address.
struct ProfileSetupView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var name = ""
    @State private var lastInitial = ""
    @State private var homeArea = ""
    @State private var phoneNumber = ""
    @State private var homeAddress = PlaceSelection()
    @State private var addsVehicle = false
    @State private var vehicleMake = ""
    @State private var vehicleModel = ""
    @State private var vehicleYear = ""
    @State private var vehicleColor = ""
    @State private var licensePlate = ""
    @State private var validationError: String?

    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isUploadingPhoto = false

    private var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && !lastInitial.trimmingCharacters(in: .whitespaces).isEmpty
            && !homeArea.trimmingCharacters(in: .whitespaces).isEmpty
            && !viewModel.isLoading
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Profile picture (optional)") {
                    HStack {
                        Spacer()
                        BrandAvatar(avatarUrl: viewModel.currentUser?.avatarUrl ?? "", name: name.isEmpty ? "?" : name, size: 72)
                        Spacer()
                    }

                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Label(isUploadingPhoto ? "Uploading…" : "Add a photo", systemImage: "camera.fill")
                    }
                    .disabled(isUploadingPhoto)
                    .onChange(of: selectedPhotoItem) { newItem in
                        Task { await uploadSelectedPhoto(newItem) }
                    }
                }

                Section("About you") {
                    TextField("First name", text: $name)
                    TextField("Last initial", text: $lastInitial)
                    TextField("Home area", text: $homeArea)
                }

                Section {
                    TextField("Contact number", text: $phoneNumber)
                        .keyboardType(.phonePad)
                } header: {
                    Text("How riders reach you")
                } footer: {
                    // Say why the field is required, not just that it is — the pattern Android's
                    // onboarding already uses.
                    Text("Shared only with people you've matched with, so they can find you at pickup.")
                }

                Section {
                    PlaceField(title: "Home address", selection: $homeAddress, viewModel: viewModel)
                } header: {
                    Text("Where you usually start")
                } footer: {
                    Text("Used to prefill the pickup on a ride request. Never shown to other riders.")
                }

                Section("Your vehicle (optional)") {
                    Toggle("I drive", isOn: $addsVehicle)

                    if addsVehicle {
                        TextField("Make", text: $vehicleMake)
                        TextField("Model", text: $vehicleModel)
                        TextField("Year", text: $vehicleYear).keyboardType(.numberPad)
                        TextField("Colour", text: $vehicleColor)
                        TextField("Licence plate", text: $licensePlate)
                    }
                }

                if let error = validationError ?? viewModel.errorMessage {
                    Text(error).font(.caption).foregroundColor(Brand.danger)
                }

                Button("Finish setup") { submit() }
                    .disabled(!canSubmit)
            }
            .navigationTitle("Set up your profile")
        }
    }

    private func submit() {
        validationError = nil
        let trimmedPhone = phoneNumber.trimmingCharacters(in: .whitespaces)
        guard !trimmedPhone.isEmpty else {
            validationError = "Please enter a contact number so riders can reach you."
            return
        }

        let vehicle: Vehicle? = addsVehicle
            ? Vehicle(
                ownerId: "",
                make: vehicleMake,
                model: vehicleModel,
                year: vehicleYear,
                color: vehicleColor,
                licensePlate: licensePlate
            )
            : nil

        Task {
            await viewModel.completeProfile(
                name: name,
                lastInitial: lastInitial,
                homeArea: homeArea,
                phoneNumber: trimmedPhone,
                homeAddress: homeAddress.name,
                homeLat: homeAddress.lat,
                homeLng: homeAddress.lon,
                vehicle: vehicle
            )
        }
    }

    /// Matches Android's `ProfileSetupScreen` image picker (`SplitCruiserApp.kt:1104-1136`): the
    /// Firebase Auth user already exists at this point, only its Firestore profile doc doesn't
    /// yet, so `uploadProfilePicture` can run before `completeProfile` does.
    private func uploadSelectedPhoto(_ item: PhotosPickerItem?) async {
        guard let item, let userId = viewModel.currentUser?.id else { return }
        isUploadingPhoto = true
        defer { isUploadingPhoto = false }
        guard let data = try? await item.loadTransferable(type: Data.self),
              let jpegData = ProfileImageResizer.resizeToUploadContract(data) else {
            validationError = "Couldn't read that photo. Try another one."
            return
        }
        _ = await viewModel.uploadProfilePicture(userId: userId, imageData: jpegData)
    }
}

// MARK: - Home

struct HomeTabView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var showPostOffer = false

    private var emptyState: some View {
        BrandEmptyState(
            icon: "car.front.waves.up",
            title: "No rides available",
            description: "Check back soon, or post one yourself.",
            actionLabel: "Offer a ride",
            action: { showPostOffer = true }
        )
    }

    var body: some View {
        NavigationView {
            Group {
                if viewModel.activeOffers.isEmpty && !viewModel.isLoading {
                    emptyState
                } else {
                    List(viewModel.activeOffers, id: \.id) { offer in
                        NavigationLink(destination: RideDetailView(viewModel: viewModel, offer: offer)) {
                            RideOfferRow(offer: offer)
                        }
                    }
                    .refreshable { await viewModel.refresh() }
                }
            }
            .navigationTitle("Available rides")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showPostOffer = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showPostOffer) {
                PostOfferView(viewModel: viewModel)
            }
        }
    }
}

// MARK: - Post a ride offer

struct PostOfferView: View {
    @ObservedObject var viewModel: AppViewModel
    @Environment(\.presentationMode) private var presentationMode

    @State private var origin = PlaceSelection()
    @State private var destination = PlaceSelection()
    @State private var exitLocation = ""
    @State private var departure = Date().addingTimeInterval(3600)
    @State private var seats = 3
    @State private var cost = "10"
    @State private var vehicleInfo = ""
    @State private var womenOnly = false
    @State private var validationError: String?

    private var canSubmit: Bool {
        origin.isResolved && destination.isResolved && !viewModel.isLoading
    }

    /// Ranks the pickup search toward home, the way Android's post-offer form does — see
    /// `LocationAutoCompleteTextField`'s `biasLat`/`biasLng` there.
    private var homeBias: PlaceSelection? {
        guard let home = viewModel.contactDetails, home.hasHomeLocation else { return nil }
        return PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Route") {
                    PlaceField(title: "Pickup", selection: $origin, viewModel: viewModel, bias: homeBias)
                    PlaceField(title: "Dropoff", selection: $destination, viewModel: viewModel, bias: origin)
                    TextField("Exact meeting spot (optional)", text: $exitLocation)
                }

                Section("Trip") {
                    DatePicker("Departure", selection: $departure, in: Date()...)
                    Stepper("Seats: \(seats)", value: $seats, in: 1...8)
                    TextField("Cost per rider", text: $cost)
                        .keyboardType(.decimalPad)
                    TextField("Vehicle (e.g. Blue Civic)", text: $vehicleInfo)
                    Toggle("Women only", isOn: $womenOnly)
                }

                if let error = validationError ?? viewModel.errorMessage {
                    Text(error).font(.caption).foregroundColor(Brand.danger)
                }

                Button("Post ride offer") { submit() }
                    .disabled(!canSubmit)
            }
            .navigationTitle("Offer a ride")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
            .onAppear(perform: prefillFromHome)
        }
    }

    /// A host's ride usually starts from home too, so prefill it the way Android does.
    private func prefillFromHome() {
        guard !origin.isResolved, let home = viewModel.contactDetails, home.hasHomeLocation else { return }
        origin = PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    private func submit() {
        validationError = nil
        guard let costPerRider = Double(cost), costPerRider > 0 else {
            validationError = "Enter a valid cost per rider."
            return
        }
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
                costPerRider: costPerRider,
                womenOnly: womenOnly,
                vehicleInfo: vehicleInfo,
                exitLocation: exitLocation
            )
            if posted { presentationMode.wrappedValue.dismiss() }
        }
    }
}

// MARK: - Post a ride request

struct PostRequestView: View {
    @ObservedObject var viewModel: AppViewModel
    @Environment(\.presentationMode) private var presentationMode

    @State private var origin = PlaceSelection()
    @State private var destination = PlaceSelection()
    @State private var exitLocation = ""
    @State private var departure = Date().addingTimeInterval(3600)
    @State private var seats = 1
    @State private var notes = ""
    @State private var womenOnly = false

    private var canSubmit: Bool {
        origin.isResolved && destination.isResolved && !viewModel.isLoading
    }

    /// See `PostOfferView.homeBias` — same reasoning, so a rider's pickup search is ranked toward
    /// home too.
    private var homeBias: PlaceSelection? {
        guard let home = viewModel.contactDetails, home.hasHomeLocation else { return nil }
        return PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Route") {
                    PlaceField(title: "Pickup", selection: $origin, viewModel: viewModel, bias: homeBias)
                    PlaceField(title: "Dropoff", selection: $destination, viewModel: viewModel, bias: origin)
                    TextField("Exact meeting spot (optional)", text: $exitLocation)
                }

                Section("Trip") {
                    DatePicker("Departure", selection: $departure, in: Date()...)
                    Stepper("Seats needed: \(seats)", value: $seats, in: 1...8)
                    TextField("Notes", text: $notes)
                    Toggle("Women only", isOn: $womenOnly)
                }

                if let error = viewModel.errorMessage {
                    Text(error).font(.caption).foregroundColor(Brand.danger)
                }

                Button("Post ride request") { submit() }
                    .disabled(!canSubmit)
            }
            .navigationTitle("Request a ride")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
            .onAppear(perform: prefillFromHome)
        }
    }

    /// The point of collecting a home address in onboarding: the rider does not retype where they
    /// live. Android has done this since the address field existed; iOS could not until now.
    private func prefillFromHome() {
        guard !origin.isResolved, let home = viewModel.contactDetails, home.hasHomeLocation else { return }
        origin = PlaceSelection(name: home.homeAddress, lat: home.homeLat, lon: home.homeLng)
    }

    private func submit() {
        Task {
            let posted = await viewModel.postRideRequest(
                origin: origin.name,
                destination: destination.name,
                originLat: origin.lat,
                originLng: origin.lon,
                destLat: destination.lat,
                destLng: destination.lon,
                departureTime: departure,
                seatsNeeded: seats,
                notes: notes,
                womenOnly: womenOnly,
                exitLocation: exitLocation
            )
            if posted { presentationMode.wrappedValue.dismiss() }
        }
    }
}

// MARK: - Place picking

/// A typed place plus the coordinates the backend requires. The repository rejects a ride whose
/// coordinates are still zero, so the form cannot submit until one is chosen from the results.
struct PlaceSelection {
    var name = ""
    var lat = 0.0
    var lon = 0.0

    var isResolved: Bool { !name.isEmpty && lat != 0.0 && lon != 0.0 }
}

struct PlaceField: View {
    let title: String
    @Binding var selection: PlaceSelection
    @ObservedObject var viewModel: AppViewModel
    /// The best known anchor for ranking results — a home address, or an already-resolved origin
    /// when this field is the destination. Nil leaves results unranked by distance.
    var bias: PlaceSelection? = nil

    @State private var query = ""
    @State private var results: [PhotonPlaceResult] = []
    @State private var searchTask: Task<Void, Never>?
    @State private var isSearching = false
    /// `query` is also set programmatically — tapping a result, and the `onAppear` prefill — and
    /// `.onChange(of:)` fires for those writes exactly like a keystroke. Without this flag,
    /// setting `query` right after setting `selection` immediately wiped `selection` back out and
    /// re-triggered a search, making it impossible to ever keep a chosen place.
    @State private var suppressNextChange = false

    private func setQueryProgrammatically(_ newValue: String) {
        suppressNextChange = true
        query = newValue
    }

    /// Same shape as Android's `LocationAutoCompleteTextField`: a 2-char minimum and a 250ms
    /// debounce before hitting Photon, so a fast typist fires one request per pause instead of
    /// one per keystroke. A bare `Task` per keystroke (the previous implementation) had no
    /// cancellation, so a slow response for an early, short query could overwrite a later,
    /// more specific one — this cancels the in-flight task before starting a new one.
    private func scheduleSearch(for newValue: String) {
        selection = PlaceSelection()
        searchTask?.cancel()
        let trimmed = newValue.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else {
            results = defaultPlaces(matching: trimmed)
            isSearching = false
            return
        }
        searchTask = Task {
            isSearching = true
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            let fetched: [PhotonPlaceResult]
            if let bias, bias.isResolved {
                fetched = await viewModel.searchPlaces(trimmed, biasLat: bias.lat, biasLon: bias.lon)
            } else {
                fetched = await viewModel.searchPlaces(trimmed)
            }
            guard !Task.isCancelled else { return }
            results = fetched.isEmpty ? defaultPlaces(matching: trimmed) : fetched
            isSearching = false
        }
    }

    /// Falls back to the same seed list Android shows for a blank query or no Photon results —
    /// `DEFAULT_LOCATION_PLACES`, a top-level `val` in `shared/commonMain/.../Models.kt`, mapped
    /// into `PhotonPlaceResult`'s shape so `results` stays one type.
    private func defaultPlaces(matching query: String) -> [PhotonPlaceResult] {
        let all = ModelsKt.DEFAULT_LOCATION_PLACES.map {
            PhotonPlaceResult(
                name: $0.name, formattedAddress: $0.address, city: nil, state: nil, country: nil,
                lat: $0.lat, lon: $0.lng, type: $0.category
            )
        }
        guard !query.isEmpty else { return Array(all.prefix(6)) }
        return all.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
                $0.formattedAddress.localizedCaseInsensitiveContains(query) ||
                $0.type.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
            HStack {
                TextField(title, text: $query)
                    .onChange(of: query) { newValue in
                        if suppressNextChange {
                            suppressNextChange = false
                            return
                        }
                        scheduleSearch(for: newValue)
                    }
                if isSearching {
                    ProgressView().scaleEffect(0.7)
                }
            }

            if selection.isResolved {
                Text(selection.name)
                    .font(.caption)
                    .foregroundColor(Brand.success)
            }

            ForEach(results.prefix(6), id: \.formattedAddress) { place in
                Button {
                    searchTask?.cancel()
                    selection = PlaceSelection(name: place.name, lat: place.lat, lon: place.lon)
                    setQueryProgrammatically(place.name)
                    results = []
                } label: {
                    VStack(alignment: .leading) {
                        Text(place.name).font(.callout).foregroundColor(Brand.textPrimary)
                        Text(place.formattedAddress).font(.caption2).foregroundColor(Brand.textSecondary)
                    }
                }
            }
        }
        .onAppear {
            // A prefilled selection needs the field to show it, or the address looks lost.
            if query.isEmpty && selection.isResolved { setQueryProgrammatically(selection.name) }
            if results.isEmpty { results = defaultPlaces(matching: "") }
        }
    }
}

// MARK: - My rides

/// The same four sections Android's Trips tab has, in the same order, so the two apps can be
/// talked about in the same words. Sections with no history stay hidden.
struct MyRidesTabView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var showPostRequest = false

    private var isEmpty: Bool {
        viewModel.hostedRides.isEmpty && viewModel.joinedRides.isEmpty && viewModel.myRideRequests.isEmpty
    }

    private var emptyState: some View {
        BrandEmptyState(
            icon: "rectangle.and.pencil.and.ellipsis",
            title: "Nothing on your schedule yet",
            description: "Post a ride offer if you're driving, or a request if you need a seat.",
            actionLabel: "Request a ride",
            action: { showPostRequest = true }
        )
    }

    private var ridesList: some View {
        List {
            if !viewModel.hostedRides.isEmpty {
                Section("Rides you're hosting") {
                    ForEach(viewModel.hostedRides, id: \.id) { offer in
                        NavigationLink(destination: RideDetailView(viewModel: viewModel, offer: offer)) {
                            RideOfferRow(offer: offer)
                        }
                    }
                }
            }

            if !viewModel.joinedRides.isEmpty {
                Section("Rides you've joined") {
                    ForEach(viewModel.joinedRides, id: \.id) { offer in
                        NavigationLink(destination: RideDetailView(viewModel: viewModel, offer: offer)) {
                            RideOfferRow(offer: offer)
                        }
                    }
                }
            }

            if !viewModel.myRideRequests.isEmpty {
                Section("Your ride requests") {
                    ForEach(viewModel.myRideRequests, id: \.id) { request in
                        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
                            Text("\(request.origin) → \(request.destination)")
                                .font(.callout)
                                .foregroundColor(Brand.textPrimary)
                            StatusBadge(status: request.status)
                        }
                    }
                }
            }
        }
        .refreshable {
            await viewModel.refresh()
            await viewModel.refreshMyTrips()
        }
    }

    var body: some View {
        NavigationView {
            Group {
                if isEmpty {
                    emptyState
                } else {
                    ridesList
                }
            }
            .navigationTitle("My rides")
            .toolbar {
                // No Android entry point to mirror here — `HostDashboard` is a registered but
                // unreachable route there (no `navigate("host_dashboard")` call site exists).
                // This is iOS's own new entry point, gated on having something to show.
                if !viewModel.hostedRides.isEmpty {
                    ToolbarItem(placement: .navigationBarLeading) {
                        NavigationLink(destination: HostDashboardView(viewModel: viewModel)) {
                            Image(systemName: "chart.bar.fill")
                        }
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showPostRequest = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showPostRequest) {
                PostRequestView(viewModel: viewModel)
            }
            .task { await viewModel.refreshMyTrips() }
        }
    }
}

// MARK: - Matches

struct MatchesTabView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        NavigationView {
            Group {
                if viewModel.userMatches.isEmpty {
                    BrandEmptyState(
                        icon: "person.2",
                        title: "No matches yet",
                        description: "Reserve a seat or accept a request, and the conversation starts here."
                    )
                } else {
                    List(viewModel.userMatches, id: \.id) { match in
                        MatchRow(viewModel: viewModel, match: match)
                    }
                }
            }
            .navigationTitle("Matches")
        }
    }
}

struct MatchRow: View {
    @ObservedObject var viewModel: AppViewModel
    let match: TripMatch

    private var isHost: Bool { viewModel.currentUser?.id == match.hostId }

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
            HStack {
                Text(isHost ? match.riderName : "Your host")
                    .font(.headline)
                    .foregroundColor(Brand.textPrimary)
                Spacer()
                StatusBadge(status: match.status)
            }

            if isHost && match.status == "pending" {
                HStack {
                    Button("Accept") {
                        Task { await viewModel.acceptMatch(matchId: match.id) }
                    }
                    .buttonStyle(.borderedProminent)

                    Button("Decline") {
                        Task { await viewModel.declineMatch(matchId: match.id) }
                    }
                    .buttonStyle(.bordered)
                }
            }

            // Once a match is accepted there was nowhere on iOS to actually agree on a pickup —
            // Android has had a chat screen the whole time, and this is its entry point.
            if match.status == "accepted" || match.status == "completed" {
                NavigationLink(destination: ChatView(viewModel: viewModel, match: match)) {
                    Label("Open chat", systemImage: "bubble.left.and.bubble.right.fill")
                        .font(.callout)
                        .foregroundColor(Brand.primary)
                }
            }
        }
        .padding(.vertical, BrandScale.spaceXs)
    }
}

// MARK: - Profile

struct ProfileTabView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var showEditProfile = false

    var body: some View {
        NavigationView {
            ScrollView {
                if let user = viewModel.currentUser {
                    VStack(spacing: BrandScale.spaceLg) {
                        header(user: user)
                        recordCard(user: user)
                        notificationPreferencesCard(user: user)
                        alertsCard
                        safetyCard(user: user)
                        BrandCard(title: "Rate someone you rode with") {
                            RatingSubmissionView(viewModel: viewModel)
                        }

                        Button("Log out") { viewModel.logOut() }
                            .buttonStyle(BrandButtonStyle(background: Brand.danger))
                    }
                    .padding()
                }
            }
            .background(Brand.surface.ignoresSafeArea())
            .navigationTitle("Profile")
            .sheet(isPresented: $showEditProfile) {
                EditProfileView(viewModel: viewModel)
            }
        }
    }

    // The user's own picture, matching Android's `StudentAvatar`. This was a generic SF Symbol
    // even though `User` has always carried an avatarUrl.
    private func header(user: User) -> some View {
        VStack(spacing: BrandScale.spaceMd) {
            BrandAvatar(avatarUrl: user.avatarUrl, name: user.name, size: 88)

            Text(user.displayName)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(Brand.textPrimary)

            Text(user.email)
                .font(.callout)
                .foregroundColor(Brand.textSecondary)

            Button("Edit profile details") { showEditProfile = true }
                .font(.callout)
                .foregroundColor(Brand.primary)
        }
    }

    private func recordCard(user: User) -> some View {
        BrandCard(title: "Your record") {
            DetailRow(
                label: "Rating",
                value: user.ratingCount > 0
                    ? String(format: "⭐ %.1f", user.ratingAvg)
                    : "No ratings yet"
            )
            Divider()
            DetailRow(label: "Ratings received", value: "\(user.ratingCount)")

            if !user.phoneNumber.isEmpty {
                Divider()
                DetailRow(label: "Contact number", value: user.phoneNumber)
            }

            // Backend connectivity is developer instrumentation, not something a
            // rider needs next to their rating. Debug builds only, matching
            // Android's `FirebaseStatusPill`.
            #if DEBUG
            Divider()
            DetailRow(
                label: "Backend (debug)",
                value: viewModel.isConnected ? "Connected" : "Offline",
                valueColor: viewModel.isConnected ? Brand.success : Brand.warning
            )
            #endif
        }
    }

    /// Neither platform has real push (FCM/APNs) — these are in-app-alert preference flags, the
    /// same ones Android's notification card toggles (`SplitCruiserApp.kt:6737-6800`).
    private func notificationPreferencesCard(user: User) -> some View {
        BrandCard(title: "Notification preferences") {
            Text("Get alerts when another rider posts a trip that matches your active ride requests.")
                .font(.caption)
                .foregroundColor(Brand.textSecondary)

            Toggle(isOn: Binding(
                get: { user.emailNotificationsEnabled },
                set: { newValue in Task { await viewModel.toggleEmailNotifications(newValue) } }
            )) {
                Label("Email notifications", systemImage: "envelope.fill")
                    .foregroundColor(Brand.textPrimary)
            }

            Divider()

            Toggle(isOn: Binding(
                get: { user.pushNotificationsEnabled },
                set: { newValue in Task { await viewModel.togglePushNotifications(newValue) } }
            )) {
                Label("Push notifications", systemImage: "bell.fill")
                    .foregroundColor(Brand.textPrimary)
            }
        }
    }

    @ViewBuilder
    private var alertsCard: some View {
        if !viewModel.notifications.isEmpty {
            BrandCard(title: "Active trip alert matches") {
                HStack {
                    Spacer()
                    Button("Clear all") { Task { await viewModel.clearNotifications() } }
                        .font(.caption)
                        .foregroundColor(Brand.danger)
                }

                ForEach(Array(viewModel.notifications.enumerated()), id: \.element.id) { index, alert in
                    if index > 0 { Divider() }
                    NotificationAlertRow(alert: alert, viewModel: viewModel)
                }
            }
        }
    }

    private func safetyCard(user: User) -> some View {
        BrandCard(title: "Safety and privacy") {
            Toggle(isOn: Binding(
                get: { user.isWomenOnlyFilterEnabled },
                set: { newValue in Task { await viewModel.toggleWomenOnlyFilter(newValue) } }
            )) {
                Label("Women-only filter", systemImage: "lock.shield.fill")
                    .foregroundColor(Brand.textPrimary)
            }
            .tint(Brand.accent)

            Divider()

            NavigationLink(destination: BlockedListView(viewModel: viewModel)) {
                HStack {
                    Label("Manage blocked users", systemImage: "hand.raised.slash.fill")
                        .foregroundColor(Brand.textPrimary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundColor(Brand.textSecondary)
                        .font(.caption)
                }
            }
        }
    }
}

/// One in-app alert row, matching Android's `ProfileScreen` alert card
/// (`SplitCruiserApp.kt:6816-6871`): a read/unread visual state and a "Mark read" action.
struct NotificationAlertRow: View {
    let alert: NotificationAlert
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
            HStack {
                Image(systemName: alert.type == "email" ? "envelope.fill" : "bell.fill")
                    .foregroundColor(alert.isRead ? Brand.textSecondary : Brand.primary)
                Text(alert.title)
                    .font(.callout)
                    .fontWeight(.bold)
                    .foregroundColor(alert.isRead ? Brand.textSecondary : Brand.textPrimary)
                Spacer()
                if !alert.isRead {
                    Button("Mark read") { Task { await viewModel.markNotificationAsRead(alert.id) } }
                        .font(.caption2)
                        .foregroundColor(Brand.primary)
                }
            }
            Text(alert.message)
                .font(.caption)
                .foregroundColor(Brand.textSecondary)
        }
        .opacity(alert.isRead ? 0.6 : 1)
    }
}

/// One person to rate: derived client-side from `userMatches`, mirroring Android's
/// filter/map/distinctBy chain (`SplitCruiserApp.kt:6591-6608`) rather than asking for a Firebase
/// uid — nobody knows their own match's uid, and the rating form used to ask for exactly that.
struct RatingCompanion: Identifiable {
    let userId: String
    let displayName: String
    let wasHost: Bool
    var id: String { userId }
}

struct RatingSubmissionView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var ratingTarget: RatingCompanion?
    @State private var ratingValue: Double = 5
    @State private var ratingComment = ""

    private var rateableCompanions: [RatingCompanion] {
        let me = viewModel.currentUser?.id ?? ""
        var seen = Set<String>()
        var result: [RatingCompanion] = []
        for match in viewModel.userMatches where match.status == "accepted" || match.status == "completed" {
            let otherId = match.hostId == me ? match.riderId : match.hostId
            guard !otherId.isEmpty, otherId != me, !seen.contains(otherId) else { continue }
            let name: String
            if match.hostId == me {
                name = match.riderName.isEmpty ? "Your rider" : match.riderName
            } else {
                name = viewModel.repository.getUserPublicProfile(userId: otherId)?.displayName
                    ?? viewModel.offer(for: match)?.hostName
                    ?? "Your host"
            }
            seen.insert(otherId)
            result.append(RatingCompanion(userId: otherId, displayName: name, wasHost: match.hostId != me))
        }
        return result
    }

    var body: some View {
        Group {
            if rateableCompanions.isEmpty {
                Text("Once you've shared a ride, whoever you rode with shows up here to rate.")
                    .font(.caption)
                    .foregroundColor(Brand.textSecondary)
            } else {
                VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                    Text("Who did you ride with?")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(Brand.textPrimary)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: BrandScale.spaceSm) {
                            ForEach(rateableCompanions) { companion in
                                companionChip(companion)
                            }
                        }
                    }

                    Text("How did it go?")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(Brand.textPrimary)

                    HStack(spacing: BrandScale.spaceXs) {
                        ForEach(1...5, id: \.self) { star in
                            Image(systemName: star <= Int(ratingValue) ? "star.fill" : "star")
                                .foregroundColor(Brand.warning)
                                .onTapGesture { ratingValue = Double(star) }
                        }
                        Spacer()
                        Text("\(Int(ratingValue)) of 5 stars")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(Brand.primary)
                    }

                    TextField("Add a note (optional)", text: $ratingComment)
                        .textFieldStyle(.roundedBorder)

                    Button("Submit rating") { submit() }
                        .buttonStyle(BrandButtonStyle(isEnabled: ratingTarget != nil))
                        .disabled(ratingTarget == nil || viewModel.isLoading)
                }
            }
        }
        // A companion who leaves the list (e.g. the match was cancelled) must not stay selected —
        // mirrors Android's `LaunchedEffect(rateableCompanions)` at `SplitCruiserApp.kt:6614-6616`.
        .onChange(of: viewModel.userMatches.count) { _ in
            if let target = ratingTarget, !rateableCompanions.contains(where: { $0.userId == target.userId }) {
                ratingTarget = nil
            }
        }
    }

    private func companionChip(_ companion: RatingCompanion) -> some View {
        let isSelected = companion.userId == ratingTarget?.userId
        return Button {
            ratingTarget = companion
        } label: {
            HStack(spacing: BrandScale.spaceXs) {
                Image(systemName: companion.wasHost ? "car.fill" : "person.fill")
                Text(companion.displayName).fontWeight(.bold)
            }
            .font(.caption)
            .foregroundColor(isSelected ? Brand.onPrimary : Brand.textPrimary)
            .padding(.horizontal, BrandScale.spaceMd)
            .padding(.vertical, BrandScale.spaceSm)
            .background(isSelected ? Brand.primary : Brand.primaryContainer.opacity(0.4))
            .cornerRadius(BrandScale.radiusLg)
        }
    }

    private func submit() {
        guard let target = ratingTarget else { return }
        Task {
            if await viewModel.submitRating(toUserId: target.userId, rating: Float(ratingValue), comment: ratingComment) {
                ratingTarget = nil
                ratingComment = ""
                ratingValue = 5
            }
        }
    }
}

// MARK: - Supporting views

struct RideOfferRow: View {
    let offer: TripOffer

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
            HStack {
                VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
                    Text(offer.origin)
                        .font(.headline)
                        .foregroundColor(Brand.textPrimary)
                    Text("Departure: \(RideDetailView.formatTime(offer.departureTime))")
                        .font(.caption)
                        .foregroundColor(Brand.textSecondary)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: BrandScale.spaceXs) {
                    Text("$\(String(format: "%.0f", offer.costPerRider))")
                        .font(.headline)
                        .foregroundColor(Brand.primary)
                    Text("\(offer.seatsLeft) seats left")
                        .font(.caption)
                        .foregroundColor(Brand.textSecondary)
                }
            }

            Divider()

            HStack {
                Text(offer.destination)
                    .font(.subheadline)
                    .foregroundColor(Brand.textPrimary)
                Spacer()
                if !offer.vehicleInfo.isEmpty {
                    Text("🚗 \(offer.vehicleInfo)")
                        .font(.caption)
                        .foregroundColor(Brand.textSecondary)
                }
            }
        }
        .padding(.vertical, BrandScale.spaceXs)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
