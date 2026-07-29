import SwiftUI
import Shared

// Views are kept deliberately small and split into computed sub-views. Swift's type checker gives
// up on large ViewBuilder bodies ("unable to type-check this expression in reasonable time"), and
// that has already cost this project a macOS CI round trip once.

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
    @State private var isSigningUp = false

    private var header: some View {
        VStack(spacing: 12) {
            Image(systemName: "car.fill")
                .font(.system(size: 48))
                .foregroundColor(.blue)

            Text("Split Cruiser")
                .font(.title)
                .fontWeight(.bold)

            Text("Rideshare, cost split")
                .font(.subheadline)
                .foregroundColor(.gray)
        }
        .padding(.top, 40)
    }

    private var form: some View {
        VStack(spacing: 16) {
            // Email and password, matching Android. This screen used to ask for a phone number,
            // which no backend has ever authenticated against.
            TextField("Email", text: $email)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

            SecureField("Password", text: $password)
                .textFieldStyle(.roundedBorder)

            if !viewModel.isBackendConfigured {
                Text("This build has no Firebase configuration, so sign-in is unavailable.")
                    .font(.caption)
                    .foregroundColor(.orange)
                    .multilineTextAlignment(.center)
            }

            if let error = viewModel.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }

            Button(action: submit) {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding()
                } else {
                    Text(isSigningUp ? "Create account" : "Log in")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
            .disabled(viewModel.isLoading || !viewModel.isBackendConfigured)

            Button(isSigningUp ? "Already have an account? Log in"
                               : "New here? Create an account") {
                isSigningUp.toggle()
                viewModel.clearError()
            }
            .font(.callout)
        }
        .padding()
    }

    var body: some View {
        VStack(spacing: 24) {
            header
            Spacer()
            form
            Spacer()
        }
        .padding()
    }

    private func submit() {
        Task {
            if isSigningUp {
                await viewModel.signUp(email: email, password: password)
            } else {
                await viewModel.logIn(email: email, password: password)
            }
        }
    }
}

// MARK: - Profile setup

struct ProfileSetupView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var name = ""
    @State private var lastInitial = ""
    @State private var homeArea = ""

    var body: some View {
        NavigationView {
            Form {
                Section("About you") {
                    TextField("First name", text: $name)
                    TextField("Last initial", text: $lastInitial)
                    TextField("Home area", text: $homeArea)
                }

                if let error = viewModel.errorMessage {
                    Text(error).font(.caption).foregroundColor(.red)
                }

                Button("Continue") {
                    Task {
                        await viewModel.completeProfile(
                            name: name,
                            lastInitial: lastInitial,
                            homeArea: homeArea
                        )
                    }
                }
                .disabled(viewModel.isLoading)
            }
            .navigationTitle("Set up your profile")
        }
    }
}

// MARK: - Home

struct HomeTabView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var showPostOffer = false

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "car.front.waves.up")
                .font(.system(size: 48))
                .foregroundColor(.gray)
            Text("No rides available").font(.headline)
            Text("Check back soon, or post one yourself")
                .font(.caption)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
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
    @State private var departure = Date().addingTimeInterval(3600)
    @State private var seats = 3
    @State private var cost = "10"
    @State private var vehicleInfo = ""
    @State private var womenOnly = false

    private var canSubmit: Bool {
        origin.isResolved && destination.isResolved && !viewModel.isLoading
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Route") {
                    PlaceField(title: "Pickup", selection: $origin, viewModel: viewModel)
                    PlaceField(title: "Dropoff", selection: $destination, viewModel: viewModel)
                }

                Section("Trip") {
                    DatePicker("Departure", selection: $departure, in: Date()...)
                    Stepper("Seats: \(seats)", value: $seats, in: 1...8)
                    TextField("Cost per rider", text: $cost)
                        .keyboardType(.decimalPad)
                    TextField("Vehicle (e.g. Blue Civic)", text: $vehicleInfo)
                    Toggle("Women only", isOn: $womenOnly)
                }

                if let error = viewModel.errorMessage {
                    Text(error).font(.caption).foregroundColor(.red)
                }

                Button("Post ride") { submit() }
                    .disabled(!canSubmit)
            }
            .navigationTitle("Offer a ride")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
    }

    private func submit() {
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
                costPerRider: Double(cost) ?? 0,
                womenOnly: womenOnly,
                vehicleInfo: vehicleInfo
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
    @State private var departure = Date().addingTimeInterval(3600)
    @State private var seats = 1
    @State private var notes = ""
    @State private var womenOnly = false

    private var canSubmit: Bool {
        origin.isResolved && destination.isResolved && !viewModel.isLoading
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Route") {
                    PlaceField(title: "Pickup", selection: $origin, viewModel: viewModel)
                    PlaceField(title: "Dropoff", selection: $destination, viewModel: viewModel)
                }

                Section("Trip") {
                    DatePicker("Departure", selection: $departure, in: Date()...)
                    Stepper("Seats needed: \(seats)", value: $seats, in: 1...8)
                    TextField("Notes", text: $notes)
                    Toggle("Women only", isOn: $womenOnly)
                }

                if let error = viewModel.errorMessage {
                    Text(error).font(.caption).foregroundColor(.red)
                }

                Button("Post request") { submit() }
                    .disabled(!canSubmit)
            }
            .navigationTitle("Request a ride")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
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
                womenOnly: womenOnly
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

    @State private var query = ""
    @State private var results: [PhotonPlaceResult] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            TextField(title, text: $query)
                .onChange(of: query) { newValue in
                    selection = PlaceSelection()
                    Task { results = await viewModel.searchPlaces(newValue) }
                }

            if selection.isResolved {
                Text(selection.name)
                    .font(.caption)
                    .foregroundColor(.green)
            }

            ForEach(results.prefix(4), id: \.formattedAddress) { place in
                Button {
                    selection = PlaceSelection(name: place.name, lat: place.lat, lon: place.lon)
                    query = place.name
                    results = []
                } label: {
                    VStack(alignment: .leading) {
                        Text(place.name).font(.callout)
                        Text(place.formattedAddress).font(.caption2).foregroundColor(.gray)
                    }
                }
            }
        }
    }
}

// MARK: - My rides

struct MyRidesTabView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var showPostRequest = false

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "rectangle.and.pencil.and.ellipsis")
                .font(.system(size: 48))
                .foregroundColor(.gray)
            Text("No rides yet").font(.headline)
            Text("Post a ride offer or request to get started")
                .font(.caption)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var ridesList: some View {
        List {
            Section("Ride requests you posted") {
                ForEach(viewModel.myRideRequests, id: \.id) { request in
                    VStack(alignment: .leading) {
                        Text("\(request.origin) → \(request.destination)")
                        Text(request.status).font(.caption).foregroundColor(.gray)
                    }
                }
            }
        }
    }

    var body: some View {
        NavigationView {
            Group {
                if viewModel.myRideRequests.isEmpty {
                    emptyState
                } else {
                    ridesList
                }
            }
            .navigationTitle("My rides")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showPostRequest = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showPostRequest) {
                PostRequestView(viewModel: viewModel)
            }
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
                    VStack(spacing: 16) {
                        Image(systemName: "person.2")
                            .font(.system(size: 48))
                            .foregroundColor(.gray)
                        Text("No matches yet").font(.headline)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
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
        VStack(alignment: .leading, spacing: 8) {
            Text(match.riderName).font(.headline)
            Text("Status: \(match.status)").font(.caption).foregroundColor(.gray)

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
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Profile

struct ProfileTabView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        NavigationView {
            VStack {
                if let user = viewModel.currentUser {
                    VStack(spacing: 16) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 64))
                            .foregroundColor(.blue)

                        Text(user.displayName).font(.title2).fontWeight(.bold)
                        Text(user.email).font(.callout).foregroundColor(.gray)

                        VStack(spacing: 12) {
                            HStack {
                                Text("Rating")
                                Spacer()
                                Text("⭐ \(String(format: "%.1f", user.ratingAvg))")
                            }
                            Divider()
                            HStack {
                                Text("Ratings received")
                                Spacer()
                                Text("\(user.ratingCount)")
                            }
                            Divider()
                            HStack {
                                Text("Backend")
                                Spacer()
                                Text(viewModel.isConnected ? "Connected" : "Offline")
                                    .foregroundColor(viewModel.isConnected ? .green : .orange)
                            }
                        }
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(8)

                        Button(action: { viewModel.logOut() }) {
                            Text("Log out")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.red)
                                .foregroundColor(.white)
                                .cornerRadius(8)
                        }
                    }
                    .padding()
                }

                Spacer()
            }
            .navigationTitle("Profile")
        }
    }
}

// MARK: - Supporting views

struct RideOfferRow: View {
    let offer: TripOffer

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(offer.origin).font(.headline)
                    Text("Departure: \(formatTime(offer.departureTime))")
                        .font(.caption)
                        .foregroundColor(.gray)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("$\(String(format: "%.0f", offer.costPerRider))")
                        .font(.headline)
                        .foregroundColor(.green)
                    Text("\(offer.seatsLeft) seats left")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }

            Divider()

            HStack {
                Text(offer.destination).font(.subheadline)
                Spacer()
                if !offer.vehicleInfo.isEmpty {
                    Text("🚗 \(offer.vehicleInfo)").font(.caption).foregroundColor(.gray)
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func formatTime(_ timestamp: Int64) -> String {
        guard timestamp > 0 else { return "Time TBD" }
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: Date(epochMillis: timestamp))
    }
}

struct RideDetailView: View {
    @ObservedObject var viewModel: AppViewModel
    let offer: TripOffer
    @Environment(\.presentationMode) private var presentationMode

    private var details: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Ride details").font(.title2).fontWeight(.bold)

            HStack { Text("From:"); Spacer(); Text(offer.origin) }
            HStack { Text("To:"); Spacer(); Text(offer.destination) }
            HStack {
                Text("Cost:")
                Spacer()
                Text("$\(String(format: "%.0f", offer.costPerRider))")
            }
            HStack { Text("Seats available:"); Spacer(); Text("\(offer.seatsLeft)") }
            HStack { Text("Host:"); Spacer(); Text(offer.hostName) }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(8)
    }

    var body: some View {
        VStack(spacing: 20) {
            details

            if let error = viewModel.errorMessage {
                Text(error).font(.caption).foregroundColor(.red)
            }

            Button {
                Task {
                    // Reserves a seat, exactly as the Android "Join ride" button does.
                    if await viewModel.joinRide(offerId: offer.id) {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            } label: {
                Text(offer.seatsLeft > 0 ? "Reserve a seat" : "Full")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(offer.seatsLeft > 0 ? Color.blue : Color.gray)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            .disabled(offer.seatsLeft <= 0 || viewModel.isLoading)

            Spacer()
        }
        .padding()
        .navigationTitle("Ride details")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
