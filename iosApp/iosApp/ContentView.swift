import SwiftUI
import Shared

// MARK: - Root

/// The app's root.
///
/// Mirrors Android's `SplitCruiserApp`: a `Surface` in the brand background holding the current
/// screen, with the global loading overlay and error dialog stacked above it.
///
/// The three top-level states are derived rather than navigated to. Android runs them as a side
/// effect of `LaunchedEffect(currentUser)`, which fires on *every* user emission — including a
/// background poll that merely refreshes the profile. Transcribed literally that would eject
/// someone out of a chat every 20 seconds; deriving an `AppPhase` means the stack resets exactly
/// when signed-in-ness or `needsProfileSetup` changes, and never on a refresh.
struct ContentView: View {
    @StateObject private var viewModel = AppViewModel()
    @StateObject private var router = AppRouter()

    private var phase: AppPhase {
        if !viewModel.isSignedIn { return .login }
        if viewModel.needsProfileSetup { return .profileSetup }
        return .dashboard
    }

    var body: some View {
        ZStack {
            Brand.surface.ignoresSafeArea()

            switch phase {
            case .login:
                LoginScreen()
            case .profileSetup:
                ProfileSetupScreen()
            case .dashboard:
                NavigationStack(path: $router.path) {
                    DashboardScreen()
                        .navigationDestination(for: Route.self) { destination(for: $0) }
                }
            }

            if viewModel.isLoading {
                LoadingOverlay(message: viewModel.loadingMessage)
                    .transition(.opacity)
            }

            ToastHost(message: $viewModel.transientMessage)
        }
        .environmentObject(viewModel)
        .environmentObject(router)
        .tint(Brand.primary)
        .animation(.easeInOut(duration: 0.2), value: viewModel.isLoading)
        .onChange(of: phase) { _ in router.popToRoot() }
        .alert(
            "Information",
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.clearError() } }
            )
        ) {
            Button("Got it") { viewModel.clearError() }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .postOffer:
            PostOfferScreen()
        case .postRequest:
            PostRequestScreen()
        case .tripDetail(let id, let kind):
            TripDetailScreen(id: id, kind: kind)
        case .chat(let matchId):
            ChatScreen(matchId: matchId)
        case .matches:
            MatchesScreen()
        case .profile:
            ProfileScreen()
        case .blockedList:
            BlockedListScreen()
        case .hostDashboard:
            HostDashboardScreen()
        }
    }
}

// MARK: - Dashboard

/// Explore and My-trips, under the hybrid bottom bar.
///
/// Android's `NavigationBar` lives inside its dashboard rather than at app level, so pushing a
/// detail screen hides it. A SwiftUI `TabView` keeps its bar visible on pushed screens unless
/// every destination opts out, so this is a hand-built row instead — which is also what lets the
/// last two items be pushes rather than tabs, exactly as on Android.
struct DashboardScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    @State private var selectedTab: DashboardTab = .explore
    @State private var joinSucceededFor: TripOffer?

    var body: some View {
        VStack(spacing: 0) {
            header

            ZStack(alignment: .bottomTrailing) {
                Group {
                    switch selectedTab {
                    case .explore:
                        ExploreFeed(onJoined: { joinSucceededFor = $0 })
                    case .trips:
                        TripsTab(onFindARide: { selectedTab = .explore })
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                if selectedTab == .explore { postFAB }
            }

            DashboardTabBar(selected: $selectedTab)
        }
        .background(Brand.surface)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $joinSucceededFor) { offer in
            JoinSuccessSheet(offer: offer) {
                joinSucceededFor = nil
                selectedTab = .trips
            }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(spacing: BrandScale.spaceMd) {
            HStack(spacing: BrandScale.spaceSm) {
                Image("SplitCruiserLogo")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 24, height: 24)
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) {
                        Text(titleText)
                            .font(BrandFont.headline())
                            .foregroundColor(Brand.textPrimary)
                            .lineLimit(1)
                        if selectedTab == .explore,
                           viewModel.currentUser?.verifiedTier == "vouched" {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.system(size: 18))
                                .foregroundColor(Brand.primary)
                        }
                    }
                    Text(subtitleText)
                        .font(BrandFont.eyebrow(.regular))
                        .foregroundColor(Brand.textSecondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 0)

                if selectedTab == .trips {
                    circleButton(icon: "arrow.clockwise") {
                        Task { await viewModel.refreshMyTrips() }
                    }
                } else {
                    if let user = viewModel.currentUser, user.ratingCount > 0 {
                        HStack(spacing: 3) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 14))
                                .foregroundColor(Brand.warning)
                            Text(TripFormat.rating(user.ratingAvg))
                                .font(BrandFont.caption(.bold))
                                .foregroundColor(Brand.textPrimary)
                        }
                        .padding(.horizontal, BrandScale.spaceSm)
                        .padding(.vertical, 6)
                        .background(Brand.surfaceMuted)
                        .cornerRadius(BrandScale.radiusMd)
                    }
                    circleButton(icon: "person.fill") { router.push(.profile) }
                }
            }

            if selectedTab == .explore {
                ModeSelector(mode: Binding(
                    get: { viewModel.mode },
                    set: { viewModel.switchMode($0) }
                ))
            }
        }
        .padding(.horizontal, BrandScale.spaceLg)
        .padding(.vertical, BrandScale.spaceSm)
        .background(Brand.surface)
    }

    private var titleText: String {
        selectedTab == .trips
            ? "My Travel Schedule"
            : "Namaste, \(viewModel.currentUser?.name ?? "Rider")"
    }

    private var subtitleText: String {
        if selectedTab == .trips { return "Manage your hosted and joined rides" }
        let homeArea = viewModel.currentUser?.homeArea ?? ""
        return homeArea.isEmpty ? "Find your next ride" : homeArea
    }

    private func circleButton(icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(Brand.primaryContainer.opacity(0.2))
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundColor(Brand.primary)
            }
            .frame(width: 40, height: 40)
        }
        .buttonStyle(.plain)
    }

    // MARK: FAB

    private var postFAB: some View {
        Button {
            router.push(viewModel.mode == .rider ? .postRequest : .postOffer)
        } label: {
            HStack(spacing: BrandScale.spaceSm) {
                Image(systemName: "plus")
                Text(viewModel.mode.actionLabel).fontWeight(.bold)
            }
            .font(BrandFont.body(.bold))
            .foregroundColor(Brand.onPrimary)
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(Brand.primary)
            .cornerRadius(BrandScale.radiusLg)
            .shadow(color: .black.opacity(0.18), radius: 8, y: 4)
        }
        .buttonStyle(.plain)
        .padding(BrandScale.spaceLg)
        .accessibilityIdentifier("action_fab")
    }
}

// MARK: - Mode selector

/// Android's Rider/Host segmented control.
///
/// Not a `Picker(.segmented)`: `UISegmentedControl` cannot be styled to a `SurfaceTrack` track
/// with a pure-white thumb at these radii, and the control is prominent enough that the difference
/// reads immediately.
struct ModeSelector: View {
    @Binding var mode: RideMode

    var body: some View {
        HStack(spacing: 0) {
            ForEach(RideMode.allCases, id: \.self) { candidate in
                Text(candidate.label)
                    .font(BrandFont.fixed(13, .bold))
                    .foregroundColor(mode == candidate ? Brand.textPrimary : Brand.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(mode == candidate ? Color.white : Color.clear)
                    .cornerRadius(BrandScale.radiusMd)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.15)) { mode = candidate }
                    }
                    .accessibilityAddTraits(mode == candidate ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(BrandScale.spaceXs)
        .background(Brand.surfaceTrack)
        .cornerRadius(BrandScale.radiusLg)
    }
}

// MARK: - Bottom bar

/// Two tabs and two pushes, matching Android's `NavigationBar`.
///
/// Android's "Chats" item jumps straight into `userMatches.first()` and has no list behind it, so
/// every other conversation is unreachable from the bar. That is a wart, not a design: this pushes
/// the match list instead, and is otherwise identical — same order, labels, colours and badge.
struct DashboardTabBar: View {
    @Binding var selected: DashboardTab
    @EnvironmentObject private var viewModel: AppViewModel
    @EnvironmentObject private var router: AppRouter

    var body: some View {
        HStack(spacing: 0) {
            tabItem(.explore, icon: "car.fill", label: "Explore")
            tabItem(.trips, icon: "map.fill", label: "My trips")
            pushItem(
                icon: "bubble.left",
                label: "Chats",
                showsBadge: !viewModel.userMatches.isEmpty,
                isEnabled: !viewModel.userMatches.isEmpty
            ) {
                router.push(.matches)
            }
            pushItem(icon: "person", label: "Profile") {
                router.push(.profile)
            }
        }
        .padding(.vertical, BrandScale.spaceSm)
        .background(Brand.surfaceCard)
        .overlay(alignment: .top) {
            Rectangle().fill(Brand.outline).frame(height: 1)
        }
    }

    private func tabItem(_ tab: DashboardTab, icon: String, label: String) -> some View {
        let isSelected = selected == tab
        return Button {
            selected = tab
        } label: {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .frame(width: 64, height: 32)
                    .background(
                        Capsule().fill(isSelected ? Brand.primaryContainer : .clear)
                    )
                Text(label).font(BrandFont.eyebrow(.medium))
            }
            .foregroundColor(isSelected ? Brand.primary : Brand.textSecondary)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private func pushItem(
        icon: String,
        label: String,
        showsBadge: Bool = false,
        isEnabled: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: icon)
                        .font(.system(size: 20))
                        .frame(width: 64, height: 32)
                    if showsBadge {
                        Circle()
                            .fill(Brand.danger)
                            .frame(width: 8, height: 8)
                            .offset(x: -16, y: 4)
                    }
                }
                Text(label).font(BrandFont.eyebrow(.medium))
            }
            .foregroundColor(isEnabled ? Brand.textSecondary : Brand.textSecondary.opacity(0.4))
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}
