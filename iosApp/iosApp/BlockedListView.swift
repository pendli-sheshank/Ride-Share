import SwiftUI
import Shared

/// Mirrors Android's `BlockedListScreen` (`SplitCruiserApp.kt:7070-7136`). Host-side only, like the
/// block button that leads here — see the note on `RideDetailView.hostDetails`.
struct BlockedListView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var blocked: [User] = []

    var body: some View {
        Group {
            if blocked.isEmpty {
                BrandEmptyState(
                    icon: "checkmark.seal.fill",
                    title: "High trust community!",
                    description: "You haven't blocked anyone. Everyone is vouched and trusted."
                )
            } else {
                List(blocked, id: \.id) { user in
                    BlockedUserRow(viewModel: viewModel, user: user, onUnblocked: refresh)
                }
            }
        }
        .navigationTitle("Blocked users")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: refresh)
    }

    private func refresh() {
        blocked = viewModel.blockedUsers()
    }
}

private struct BlockedUserRow: View {
    @ObservedObject var viewModel: AppViewModel
    let user: User
    let onUnblocked: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
                Text(user.displayName)
                    .font(.callout)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.textPrimary)

                // The Firebase uid used to be printed here. It means nothing to the person
                // reading it; "what happens if I unblock" does.
                Text("Hidden from your feed and can't message you")
                    .font(.caption2)
                    .foregroundColor(Brand.textSecondary)
            }

            Spacer()

            Button("Unblock") {
                Task {
                    if await viewModel.unblockUser(user.id) { onUnblocked() }
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(.vertical, BrandScale.spaceXs)
    }
}
