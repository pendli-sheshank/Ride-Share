import SwiftUI
import PhotosUI
import Shared

// The four features that existed only on Android. All of them call `AppViewModel` wrappers that
// already shipped and had no caller — the plumbing landed in the "iOS parity 1/2" commit, and this
// is the other half.

// MARK: - Profile

/// The account screen: identity, notification preferences, alerts, safety, ratings, log out.
struct ProfileScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    @State private var isEditing = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BrandScale.spaceXl) {
                if let user = viewModel.currentUser {
                    identityCard(user)
                    notificationPreferences(user)
                    alertsSection
                    safetySection(user)
                    RatingsCard()
                    Button("Log Out") { viewModel.logOut() }
                        .buttonStyle(BrandButtonStyle(background: Brand.danger))
                } else {
                    // Previously this branch rendered nothing at all — a blank "Profile" screen
                    // with no explanation whenever the profile had not arrived yet.
                    BrandEmptyState(
                        icon: "person.crop.circle.badge.questionmark",
                        title: "Profile unavailable",
                        description: "We couldn't load your account just now. Pull to refresh, or check your connection."
                    )
                }

                Spacer().frame(height: BrandScale.spaceXl)
            }
            .padding(BrandScale.spaceXl)
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
        .navigationTitle("Your Split Cruiser Account")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await viewModel.refresh() }
        .sheet(isPresented: $isEditing) { EditProfileSheet() }
    }

    // MARK: Identity

    private func identityCard(_ user: User) -> some View {
        VStack(spacing: BrandScale.spaceMd) {
            StudentAvatar(avatarUrl: user.avatarUrl, name: user.name, size: 72, fontSize: 28)

            Text(user.displayName)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Brand.textPrimary)

            HStack(spacing: BrandScale.spaceSm) {
                FirebaseStatusPill(isEnabled: viewModel.isBackendConfigured)
                if user.verifiedTier == "vouched" {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark").font(.system(size: 10))
                        Text("Verified").font(BrandFont.eyebrow(.bold))
                    }
                    .foregroundColor(Brand.success)
                    .padding(.horizontal, BrandScale.spaceSm)
                    .padding(.vertical, 4)
                    .background(Brand.success.opacity(0.15))
                    .cornerRadius(BrandScale.radiusMd)
                }
            }

            Button {
                isEditing = true
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "pencil").font(.system(size: 14))
                    Text("Edit Profile Details").font(BrandFont.caption(.bold))
                }
                .foregroundColor(Brand.primary)
                .padding(.horizontal, BrandScale.spaceLg)
                .frame(height: 36)
                .background(Brand.primary.opacity(0.15))
                .cornerRadius(BrandScale.radiusSm)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("edit_profile_button")

            Divider().background(Brand.outline)

            HStack {
                statColumn(
                    value: user.ratingCount > 0 ? "\(TripFormat.rating(user.ratingAvg)) ★" : "N/A",
                    label: "Rating Avg",
                    tint: Brand.primary
                )
                statColumn(value: "\(user.ratingCount)", label: "Trips Shared", tint: Brand.textPrimary)
                statColumn(value: "\(user.noShowCount)", label: "No Shows", tint: Brand.danger.opacity(0.8))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusLg)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                .stroke(Brand.outline, lineWidth: 1)
        )
    }

    private func statColumn(value: String, label: String, tint: Color) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 18, weight: .black))
                .foregroundColor(tint)
            Text(label)
                .font(BrandFont.eyebrow(.regular))
                .foregroundColor(Brand.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Notifications

    private func notificationPreferences(_ user: User) -> some View {
        BrandCard(title: "Notification preferences", tint: Brand.primary) {
            Text("Choose how we let you know about matching routes and new messages.")
                .font(BrandFont.caption())
                .foregroundColor(Brand.textSecondary)

            toggleRow(
                icon: "envelope.fill",
                title: "Email Notifications",
                subtitle: "Receive matching routes via inbox",
                isOn: user.emailNotificationsEnabled,
                tint: Brand.primary
            ) { enabled in
                Task { await viewModel.toggleEmailNotifications(enabled) }
            }

            Divider().background(Brand.outline)

            toggleRow(
                icon: "bell.fill",
                title: "Push Notifications",
                subtitle: "Instantly alert on device screen",
                isOn: user.pushNotificationsEnabled,
                tint: Brand.primary
            ) { enabled in
                Task { await viewModel.togglePushNotifications(enabled) }
            }
        }
    }

    private func toggleRow(
        icon: String,
        title: String,
        subtitle: String,
        isOn: Bool,
        tint: Color,
        onChange: @escaping (Bool) -> Void
    ) -> some View {
        Toggle(isOn: Binding(get: { isOn }, set: onChange)) {
            HStack(spacing: BrandScale.spaceSm) {
                Image(systemName: icon).foregroundColor(tint)
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
        .tint(tint)
    }

    // MARK: Alerts

    @ViewBuilder
    private var alertsSection: some View {
        if !viewModel.notifications.isEmpty {
            VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
                HStack {
                    Text("ACTIVE TRIP ALERT MATCHES")
                        .font(BrandFont.eyebrow(.black))
                        .kerning(1)
                        .foregroundColor(Brand.primary)
                    Spacer()
                    Button("Clear All") {
                        Task { await viewModel.clearNotifications() }
                    }
                    .font(BrandFont.eyebrow(.bold))
                    .foregroundColor(Brand.danger.opacity(0.8))
                }

                ForEach(viewModel.notifications) { alert in
                    alertRow(alert)
                }
            }
        }
    }

    private func alertRow(_ alert: NotificationAlert) -> some View {
        HStack(alignment: .top, spacing: BrandScale.spaceMd) {
            Image(systemName: alert.type == "email" ? "envelope.fill" : "bell.fill")
                .foregroundColor(alert.isRead ? Brand.textSecondary : Brand.primary)

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(alert.title)
                        .font(BrandFont.caption(.bold))
                        .foregroundColor(Brand.textPrimary)
                    Spacer()
                    if !alert.isRead {
                        Button("Mark Read") {
                            Task { await viewModel.markNotificationAsRead(alert.id) }
                        }
                        .font(BrandFont.fixed(10, .semibold))
                        .foregroundColor(Brand.primary)
                    }
                }
                Text(alert.message)
                    .font(BrandFont.eyebrow(.regular))
                    .foregroundColor(Brand.textSecondary)
            }
        }
        .padding(BrandScale.spaceMd)
        .background(Brand.surfaceCard.opacity(alert.isRead ? 0.5 : 1))
        .cornerRadius(BrandScale.radiusMd)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                .stroke(alert.isRead ? .clear : Brand.primary.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: Safety

    private func safetySection(_ user: User) -> some View {
        BrandCard(title: "Safety and privacy", tint: Brand.primary) {
            toggleRow(
                icon: "person.fill",
                title: "Women-Only Filter",
                subtitle: "Only match with other women",
                isOn: user.isWomenOnlyFilterEnabled,
                tint: Brand.accent
            ) { enabled in
                Task { await viewModel.toggleWomenOnlyFilter(enabled) }
            }

            Divider().background(Brand.outline)

            navRow(icon: "nosign", title: "Manage Blocked Users") {
                router.push(.blockedList)
            }

            Divider().background(Brand.outline)

            // Android registers a host-analytics route but never navigates to it, so the screen is
            // unreachable there. It gets a real entry point here.
            navRow(icon: "chart.bar.fill", title: "Host Dashboard") {
                router.push(.hostDashboard)
            }
        }
    }

    private func navRow(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: BrandScale.spaceSm) {
                Image(systemName: icon).foregroundColor(Brand.textSecondary)
                Text(title)
                    .font(BrandFont.fixed(13, .bold))
                    .foregroundColor(Brand.textPrimary)
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(Brand.textSecondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Ratings

/// Someone the current user has actually ridden with, and can therefore rate.
///
/// The whole point of deriving this list is that the user never types — or sees — a Firebase uid.
/// The Android form used to ask them to paste one in.
struct RatingCompanion: Identifiable {
    let id: String
    let displayName: String
    let wasHost: Bool
}

/// "Rate someone you rode with", matching Android's card on the profile screen.
struct RatingsCard: View {
    @EnvironmentObject private var viewModel: AppViewModel

    @State private var target: RatingCompanion?
    @State private var value: Double = 5
    @State private var comment = ""

    /// The other party on every accepted or completed match, named without ever exposing an id.
    private var companions: [RatingCompanion] {
        guard let me = viewModel.currentUser?.id else { return [] }
        var seen = Set<String>()
        return viewModel.userMatches
            .filter { $0.status == "accepted" || $0.status == "completed" }
            .compactMap { match -> RatingCompanion? in
                let wasHost = match.hostId != me
                let otherId = wasHost ? match.hostId : match.riderId
                guard !otherId.isEmpty, otherId != me, seen.insert(otherId).inserted else { return nil }

                let name: String
                if !wasHost {
                    name = match.riderName.isEmpty ? "Your rider" : match.riderName
                } else if let profile = viewModel.repository.getUserPublicProfile(userId: otherId),
                          !profile.displayName.isEmpty {
                    name = profile.displayName
                } else {
                    let hostName = viewModel.offer(for: match)?.hostName ?? ""
                    name = hostName.isEmpty ? "Your host" : hostName
                }
                return RatingCompanion(id: otherId, displayName: name, wasHost: wasHost)
            }
    }

    var body: some View {
        BrandCard(title: "Rate someone you rode with", tint: Brand.primary) {
            if companions.isEmpty {
                Text("Once you've shared a ride, whoever you rode with shows up here to rate.")
                    .font(BrandFont.caption())
                    .foregroundColor(Brand.textSecondary)
            } else {
                Text("Who did you ride with?")
                    .font(BrandFont.caption(.bold))
                    .foregroundColor(Brand.textPrimary)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: BrandScale.spaceSm) {
                        ForEach(companions) { companion in
                            companionChip(companion)
                        }
                    }
                }

                Text("How did it go?")
                    .font(BrandFont.caption(.bold))
                    .foregroundColor(Brand.textPrimary)

                Slider(value: $value, in: 1...5, step: 1)
                    .tint(Brand.primary)

                Text("\(Int(value)) of 5 stars")
                    .font(BrandFont.eyebrow(.bold))
                    .foregroundColor(Brand.primary)

                BrandTextField(
                    title: "Add a note (optional)",
                    placeholder: "Friendly, easy to find, safe driving",
                    text: $comment,
                    icon: "text.bubble.fill"
                )

                Button("Submit rating") { submit() }
                    .buttonStyle(BrandButtonStyle(isEnabled: target != nil, height: 46))
                    .disabled(target == nil)
                    .accessibilityIdentifier("submit_rating_button")
            }
        }
        .onChange(of: companions.map(\.id)) { ids in
            // If the selected companion drops out of the list, drop the selection with it.
            if let current = target, !ids.contains(current.id) { target = nil }
        }
    }

    private func companionChip(_ companion: RatingCompanion) -> some View {
        let isSelected = target?.id == companion.id
        return Button {
            target = companion
        } label: {
            HStack(spacing: 5) {
                Image(systemName: companion.wasHost ? "car.fill" : "person.fill")
                    .font(.system(size: 14))
                Text(companion.displayName)
                    .font(BrandFont.caption(.bold))
            }
            .foregroundColor(isSelected ? Brand.onPrimary : Brand.textPrimary)
            .padding(.horizontal, BrandScale.spaceMd)
            .padding(.vertical, BrandScale.spaceSm)
            .background(isSelected ? Brand.primary : Brand.primaryContainer.opacity(0.4))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("rating_companion_\(companion.id)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private func submit() {
        guard let target else { return }
        Task {
            if await viewModel.submitRating(
                toUserId: target.id,
                rating: Float(value),
                comment: comment
            ) {
                viewModel.notify("Thanks — your rating is in.")
                self.target = nil
                comment = ""
                value = 5
            }
        }
    }
}

// MARK: - Edit profile

/// Android's `EditProfileDialog`, plus the picture upload that only ever lived on its onboarding
/// screen. The shared `uploadProfilePicture` takes raw JPEG bytes precisely so iOS can hand it
/// `UIImage.jpegData(compressionQuality:)`.
struct EditProfileSheet: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var lastInitial = ""
    @State private var avatarUrl = ""
    @State private var customUrl = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var isUploading = false

    private let presetKeys = [
        "preset_grad", "preset_driver", "preset_tech",
        "preset_explorer", "preset_star", "preset_globe",
    ]

    private var resolvedAvatar: String {
        customUrl.isEmpty ? avatarUrl : customUrl
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: BrandScale.spaceXl) {
                    VStack(spacing: BrandScale.spaceMd) {
                        StudentAvatar(
                            avatarUrl: resolvedAvatar,
                            name: name,
                            size: 72,
                            fontSize: 28
                        )
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            HStack(spacing: 6) {
                                if isUploading {
                                    ProgressView().scaleEffect(0.7)
                                } else {
                                    Image(systemName: "photo.on.rectangle")
                                }
                                Text(isUploading ? "Uploading…" : "Upload a photo")
                                    .font(BrandFont.caption(.bold))
                            }
                            .foregroundColor(Brand.primary)
                        }
                        .disabled(isUploading)
                    }
                    .frame(maxWidth: .infinity)

                    FormSection(title: "Profile picture") {
                        Text("Select a preset avatar:")
                            .font(BrandFont.caption())
                            .foregroundColor(Brand.textSecondary)
                        HStack(spacing: BrandScale.spaceSm) {
                            ForEach(presetKeys, id: \.self) { key in
                                presetButton(key)
                            }
                        }
                        BrandTextField(
                            title: "Or paste a custom image URL",
                            placeholder: "https://…",
                            text: $customUrl,
                            icon: "link"
                        )
                    }

                    FormSection(title: "Personal details") {
                        BrandTextField(title: "First Name", placeholder: "Amit",
                                       text: $name, icon: "person.fill")
                        BrandTextField(title: "Initial", placeholder: "S",
                                       text: $lastInitial, icon: "textformat")
                            .onChange(of: lastInitial) { lastInitial = String($0.prefix(1)).uppercased() }
                    }

                    Button("Save Changes") { save() }
                        .buttonStyle(BrandButtonStyle())

                    Spacer().frame(height: BrandScale.spaceXl)
                }
                .padding(BrandScale.spaceXl)
            }
            .background(Brand.surface)
            .scrollContentBackground(.hidden)
            .navigationTitle("Edit profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .onAppear {
            guard let user = viewModel.currentUser else { return }
            name = user.name
            lastInitial = user.lastInitial
            avatarUrl = user.avatarUrl
        }
        .onChange(of: photoItem) { item in
            guard let item else { return }
            Task { await upload(item) }
        }
    }

    private func presetButton(_ key: String) -> some View {
        let isSelected = resolvedAvatar == key
        return Button {
            customUrl = ""
            avatarUrl = key
        } label: {
            ZStack {
                Circle().fill(isSelected ? Brand.primary.opacity(0.25) : Brand.surface)
                Circle().stroke(isSelected ? Brand.primary : .clear, lineWidth: 2)
                Text(StudentAvatar.presets[key] ?? "🙂").font(.system(size: 20))
            }
            .frame(width: 40, height: 40)
        }
        .buttonStyle(.plain)
    }

    /// Mirrors Android's `ProfileImages.readResizedJpeg` — a 512px max edge at quality 0.85 —
    /// so neither platform uploads a 12-megapixel original.
    private func upload(_ item: PhotosPickerItem) async {
        guard let userId = viewModel.currentUser?.id else { return }
        isUploading = true
        defer { isUploading = false }

        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data),
              let jpeg = image.resizedForUpload().jpegData(compressionQuality: 0.85)
        else {
            viewModel.setError("That image couldn't be read. Try another one.")
            return
        }

        if await viewModel.uploadProfilePicture(userId: userId, imageData: jpeg) {
            customUrl = ""
            avatarUrl = viewModel.currentUser?.avatarUrl ?? avatarUrl
            viewModel.notify("Profile picture updated")
        }
    }

    private func save() {
        Task {
            if await viewModel.updateProfile(
                name: name,
                lastInitial: lastInitial,
                avatarUrl: resolvedAvatar
            ) {
                viewModel.notify("Profile saved")
                dismiss()
            }
        }
    }
}

extension UIImage {
    /// Scales the longest edge down to 512px, matching the Android upload path.
    func resizedForUpload(maxEdge: CGFloat = 512) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxEdge else { return self }
        let scale = maxEdge / longest
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: target)) }
    }
}

// MARK: - Blocked users

/// Blocked-user management.
///
/// `getBlockedUsers()` is a synchronous cache read, not a published stream — Android's screen gets
/// away with reading it inline because navigation recomposes it, but a SwiftUI `body` has no
/// dependency to invalidate, so an unblocked row would simply never disappear. Hence the explicit
/// `@State` + reload.
struct BlockedListScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel

    @State private var blocked: [User] = []

    var body: some View {
        Group {
            if blocked.isEmpty {
                BrandEmptyState(
                    icon: "checkmark.seal.fill",
                    title: "High Trust Community!",
                    description: "You haven't blocked anyone. Everyone is vouched and trusted."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: BrandScale.spaceMd) {
                        ForEach(blocked) { user in
                            row(user)
                        }
                    }
                    .padding(BrandScale.spaceLg)
                }
            }
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
        .navigationTitle("Blocked Users")
        .navigationBarTitleDisplayMode(.inline)
        .task { reload() }
    }

    private func row(_ user: User) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(user.displayName)
                    .font(BrandFont.body(.bold))
                    .foregroundColor(Brand.textPrimary)
                // The consequence, never the uid.
                Text("Hidden from your feed and can't message you")
                    .font(BrandFont.eyebrow(.regular))
                    .foregroundColor(Brand.textSecondary)
            }
            Spacer()
            Button("Unblock") { unblock(user) }
                .font(BrandFont.caption(.bold))
                .foregroundColor(Brand.onPrimary)
                .padding(.horizontal, BrandScale.spaceMd)
                .padding(.vertical, BrandScale.spaceSm)
                .background(Brand.primary)
                .cornerRadius(BrandScale.radiusSm)
                .buttonStyle(.plain)
        }
        .padding(BrandScale.spaceLg)
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusMd)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                .stroke(Brand.outline, lineWidth: 1)
        )
    }

    private func reload() {
        blocked = viewModel.blockedUsers()
    }

    private func unblock(_ user: User) {
        Task {
            if await viewModel.unblockUser(user.id) {
                viewModel.notify("Unblocked \(user.displayName)")
                reload()
            }
        }
    }
}

// MARK: - Host analytics

/// Host analytics. Every figure is derived client-side from `hostedRides`; there is no analytics
/// API in `:shared`, and none is needed.
struct HostDashboardScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    @State private var filter: RideFilter = .all

    enum RideFilter: String, CaseIterable {
        case all = "All Rides"
        case active = "Active"
        case closed = "Closed"
        case completed = "Completed"
        case cancelled = "Cancelled"

        /// "Active" deliberately also matches a full ride — it has not departed yet.
        func matches(_ status: String) -> Bool {
            switch self {
            case .all: return true
            case .active: return status == "active" || status == "full"
            case .closed: return status == "closed"
            case .completed: return status == "completed"
            case .cancelled: return status == "cancelled"
            }
        }
    }

    private var hosted: [TripOffer] { viewModel.hostedRides }

    private var filteredRides: [TripOffer] {
        hosted
            .filter { filter.matches($0.status) }
            .sorted { $0.departureTime > $1.departureTime }
    }

    private var activeCount: Int { hosted.filter { $0.status == "active" }.count }
    private var totalPassengers: Int { hosted.reduce(0) { $0 + $1.passengers.count } }
    /// "Chipped in", never "Revenue" — the product is a cost split, not a fare.
    private var chippedIn: Double {
        hosted.reduce(0) { $0 + $1.costPerRider * Double($1.totalSeats - $1.seatsLeft) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BrandScale.spaceLg) {
                HStack(spacing: BrandScale.spaceMd) {
                    HostStatCard(label: "Active Rides", value: "\(activeCount)", systemImage: "car.fill")
                    HostStatCard(label: "Total Passengers", value: "\(totalPassengers)", systemImage: "person.2.fill")
                    HostStatCard(label: "Chipped in", value: TripFormat.money(chippedIn), systemImage: "dollarsign.circle.fill")
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: BrandScale.spaceSm) {
                        ForEach(RideFilter.allCases, id: \.self) { candidate in
                            BrandFilterChip(
                                label: candidate.rawValue,
                                isSelected: filter == candidate
                            ) {
                                filter = candidate
                            }
                        }
                    }
                }

                if filteredRides.isEmpty {
                    BrandEmptyState(
                        icon: "car.fill",
                        title: "No Hosted Rides",
                        description: "You haven't posted any trip offers yet.",
                        actionLabel: "Post a Ride",
                        action: { router.push(.postOffer) },
                        illustrationType: .hosted
                    )
                } else {
                    ForEach(filteredRides) { offer in
                        HostedRideScheduleCard(
                            offer: offer,
                            onTap: { router.push(.tripDetail(id: offer.id, kind: .offer)) },
                            onStatusChange: { status in updateStatus(offer, to: status) }
                        )
                    }
                }

                Spacer().frame(height: BrandScale.spaceXl)
            }
            .padding(BrandScale.spaceLg)
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
        .navigationTitle("Host Dashboard")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.refreshMyTrips() }
        .refreshable { await viewModel.refreshMyTrips() }
    }

    private func updateStatus(_ offer: TripOffer, to status: String) {
        Task {
            if await viewModel.updateOfferStatus(offerId: offer.id, newStatus: status) {
                viewModel.notify("Ride status updated")
                await viewModel.refreshMyTrips()
            }
        }
    }
}
