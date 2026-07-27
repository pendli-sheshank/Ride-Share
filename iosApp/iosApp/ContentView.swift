import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var viewModel = AppViewModel()
    @State private var isLoggedIn = false
    @State private var selectedTab = 0

    var body: some View {
        if isLoggedIn {
            TabView(selection: $selectedTab) {
                // Home / Browse Rides
                HomeTabView(viewModel: viewModel)
                    .tabItem {
                        Image(systemName: "house.fill")
                        Text("Home")
                    }
                    .tag(0)

                // My Rides
                MyRidesTabView(viewModel: viewModel)
                    .tabItem {
                        Image(systemName: "car.fill")
                        Text("My Rides")
                    }
                    .tag(1)

                // Messages
                MessagesTabView()
                    .tabItem {
                        Image(systemName: "message.fill")
                        Text("Messages")
                    }
                    .tag(2)

                // Profile
                ProfileTabView(viewModel: viewModel, isLoggedIn: $isLoggedIn)
                    .tabItem {
                        Image(systemName: "person.fill")
                        Text("Profile")
                    }
                    .tag(3)
            }
        } else {
            LoginView(viewModel: viewModel, isLoggedIn: $isLoggedIn)
        }
    }
}

// MARK: - Login View

struct LoginView: View {
    @ObservedObject var viewModel: AppViewModel
    @Binding var isLoggedIn: Bool
    @State private var phoneNumber = ""
    @State private var password = ""

    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                VStack(spacing: 12) {
                    Image(systemName: "car.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.blue)

                    Text("SawaariShare")
                        .font(.title)
                        .fontWeight(.bold)

                    Text("US Desi Student Carpools")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
                .padding(.top, 40)

                Spacer()

                VStack(spacing: 16) {
                    TextField("Phone Number", text: $phoneNumber)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.phonePad)

                    SecureField("Password", text: $password)
                        .textFieldStyle(.roundedBorder)

                    if let error = viewModel.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    }

                    Button(action: {
                        Task {
                            await viewModel.loginUser(phoneNumber: phoneNumber, password: password)
                            if viewModel.currentUser != nil {
                                isLoggedIn = true
                            }
                        }
                    }) {
                        if viewModel.isLoading {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .frame(maxWidth: .infinity)
                                .padding()
                        } else {
                            Text("Login")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.blue)
                                .foregroundColor(.white)
                                .cornerRadius(8)
                        }
                    }
                    .disabled(viewModel.isLoading)
                }
                .padding()

                Spacer()

                VStack(spacing: 12) {
                    Text("Don't have an account?")
                        .font(.callout)
                        .foregroundColor(.gray)

                    NavigationLink("Sign Up") {
                        SignUpView()
                    }
                    .font(.callout)
                    .fontWeight(.semibold)
                }
                .padding(.bottom, 40)
            }
            .padding()
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Sign Up View

struct SignUpView: View {
    @Environment(\.presentationMode) var presentationMode

    var body: some View {
        VStack(spacing: 16) {
            Text("Create Account")
                .font(.title)
                .fontWeight(.bold)

            TextField("Full Name", text: .constant(""))
                .textFieldStyle(.roundedBorder)

            TextField("Email", text: .constant(""))
                .textFieldStyle(.roundedBorder)
                .keyboardType(.emailAddress)

            TextField("Phone", text: .constant(""))
                .textFieldStyle(.roundedBorder)
                .keyboardType(.phonePad)

            SecureField("Password", text: .constant(""))
                .textFieldStyle(.roundedBorder)

            Button(action: { presentationMode.wrappedValue.dismiss() }) {
                Text("Create Account")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }

            Spacer()
        }
        .padding()
    }
}

// MARK: - Home Tab

struct HomeTabView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        NavigationView {
            VStack {
                if viewModel.activeOffers.isEmpty && !viewModel.isLoading {
                    VStack(spacing: 16) {
                        Image(systemName: "car.front.waves.up")
                            .font(.system(size: 48))
                            .foregroundColor(.gray)

                        Text("No Rides Available")
                            .font(.headline)

                        Text("Check back soon for available rides in your area")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(viewModel.activeOffers, id: \.id) { offer in
                        NavigationLink(destination: RideDetailView(offer: offer)) {
                            RideOfferRow(offer: offer)
                        }
                    }
                }
            }
            .navigationTitle("Available Rides")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        Task {
                            await viewModel.fetchActiveOffers()
                        }
                    }) {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
        .onAppear {
            Task {
                await viewModel.fetchActiveOffers()
            }
        }
    }
}

// MARK: - My Rides Tab

struct MyRidesTabView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        NavigationView {
            VStack {
                Text("Your Rides")
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()

                if viewModel.activeOffers.isEmpty && viewModel.activeRequests.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "rectangle.and.pencil.and.ellipsis")
                            .font(.system(size: 48))
                            .foregroundColor(.gray)

                        Text("No Rides Yet")
                            .font(.headline)

                        Text("Post a ride offer or request to get started")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        Section("Posted Rides") {
                            ForEach(viewModel.activeOffers, id: \.id) { offer in
                                Text(offer.origin + " → " + offer.destination)
                            }
                        }

                        Section("Ride Requests") {
                            ForEach(viewModel.activeRequests, id: \.id) { request in
                                Text(request.origin + " → " + request.destination)
                            }
                        }
                    }
                }
            }
            .navigationTitle("My Rides")
        }
    }
}

// MARK: - Messages Tab

struct MessagesTabView: View {
    var body: some View {
        NavigationView {
            VStack {
                Text("Messages")
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()

                VStack(spacing: 16) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .font(.system(size: 48))
                        .foregroundColor(.gray)

                    Text("No Messages")
                        .font(.headline)

                    Text("Start a conversation with other riders")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .navigationTitle("Messages")
        }
    }
}

// MARK: - Profile Tab

struct ProfileTabView: View {
    @ObservedObject var viewModel: AppViewModel
    @Binding var isLoggedIn: Bool

    var body: some View {
        NavigationView {
            VStack {
                if let user = viewModel.currentUser {
                    VStack(spacing: 16) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 64))
                            .foregroundColor(.blue)

                        Text(user.name)
                            .font(.title2)
                            .fontWeight(.bold)

                        Text(user.email)
                            .font(.callout)
                            .foregroundColor(.gray)

                        VStack(spacing: 12) {
                            HStack {
                                Text("Rating")
                                Spacer()
                                Text("⭐ \(String(format: "%.1f", user.ratingAvg))")
                            }

                            Divider()

                            HStack {
                                Text("Rides Completed")
                                Spacer()
                                Text("\(user.ratingCount)")
                            }
                        }
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(8)

                        Button(action: { isLoggedIn = false }) {
                            Text("Logout")
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

// MARK: - Supporting Views

struct RideOfferRow: View {
    let offer: TripOffer

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(offer.origin)
                        .font(.headline)

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
                Text(offer.destination)
                    .font(.subheadline)

                Spacer()

                Text("🚗 \(offer.vehicleInfo)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
        .padding(.vertical, 4)
    }

    private func formatTime(_ timestamp: Int64) -> String {
        guard timestamp > 0 else { return "Time TBD" }
        let date = Date(timeIntervalSince1970: Double(timestamp) / 1000)
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

struct RideDetailView: View {
    let offer: TripOffer
    @Environment(\.presentationMode) var presentationMode

    var body: some View {
        VStack(spacing: 20) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Ride Details")
                    .font(.title2)
                    .fontWeight(.bold)

                HStack {
                    Text("From:")
                    Spacer()
                    Text(offer.origin)
                }

                HStack {
                    Text("To:")
                    Spacer()
                    Text(offer.destination)
                }

                HStack {
                    Text("Cost:")
                    Spacer()
                    Text("$\(String(format: "%.0f", offer.costPerRider))")
                }

                HStack {
                    Text("Seats Available:")
                    Spacer()
                    Text("\(offer.seatsLeft)")
                }
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(8)

            Button(action: { presentationMode.wrappedValue.dismiss() }) {
                Text("Request Ride")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Ride Details")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
