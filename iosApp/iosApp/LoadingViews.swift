import SwiftUI

// Loading and transient feedback. Android has all three of these; iOS had none of them, which is
// why the feed rendered as a blank list on first load and why every action completed silently.

// MARK: - Feed skeleton

/// Placeholder cards shown while the feed loads, matching Android's `SplitCruiserFeedLoadingSkeleton`.
///
/// The shimmer is one alpha sweep over the whole card (0.3 ↔ 0.8, 800 ms, autoreversing), not a
/// moving gradient — same as Android.
struct FeedLoadingSkeleton: View {
    var itemsCount: Int = 3

    @State private var shimmering = false

    private let block = Brand.textSecondary.opacity(0.3)
    private let rail = Brand.textSecondary.opacity(0.2)

    var body: some View {
        VStack(spacing: BrandScale.spaceMd) {
            ForEach(0..<itemsCount, id: \.self) { _ in
                card
            }
        }
        .opacity(shimmering ? 0.8 : 0.3)
        .onAppear { shimmering = true }
        .animation(
            .linear(duration: 0.8).repeatForever(autoreverses: true),
            value: shimmering
        )
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
            HStack(spacing: BrandScale.spaceMd) {
                Circle().fill(block).frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 6) {
                    bar(width: 120, height: 14)
                    bar(width: 60, height: 10)
                }
                Spacer()
                RoundedRectangle(cornerRadius: 10).fill(block).frame(width: 50, height: 20)
            }

            HStack(alignment: .center, spacing: BrandScale.spaceMd) {
                VStack(spacing: 0) {
                    Circle().fill(block).frame(width: 10, height: 10)
                    Rectangle().fill(rail).frame(width: 2, height: 24)
                    Circle().fill(block).frame(width: 10, height: 10)
                }
                VStack(alignment: .leading, spacing: 14) {
                    GeometryReader { proxy in
                        VStack(alignment: .leading, spacing: 14) {
                            bar(width: proxy.size.width * 0.8, height: 14)
                            bar(width: proxy.size.width * 0.6, height: 14)
                        }
                    }
                    .frame(height: 42)
                }
            }

            HStack {
                bar(width: 140, height: 12)
                Spacer()
                RoundedRectangle(cornerRadius: 14).fill(block).frame(width: 70, height: 28)
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

    private func bar(width: CGFloat, height: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: height / 2)
            .fill(block)
            .frame(width: width, height: height)
    }
}

// MARK: - Global overlay

/// The full-screen loader, matching Android's `SplitCruiserLoadingState(isFullScreen = true)`.
///
/// The message names the action in progress — "Securing your ride…" belongs to reserving a seat,
/// not to logging in. The neutral default is "Just a moment…".
struct LoadingOverlay: View {
    let message: String

    @State private var rotation: Double = 0
    @State private var pulsing = false

    var body: some View {
        ZStack {
            // Swallows taps, exactly as Android's `.clickable(enabled = false) {}` does.
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {}

            VStack(spacing: BrandScale.spaceLg) {
                ZStack {
                    Circle()
                        .trim(from: 0, to: 0.75)
                        .stroke(Brand.primary, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                        .frame(width: 80, height: 80)
                        .rotationEffect(.degrees(rotation))

                    Image("SplitCruiserLogo")
                        .resizable()
                        .scaledToFill()
                        .frame(width: 48, height: 48)
                        .clipShape(Circle())
                        .scaleEffect(pulsing ? 1.1 : 0.9)
                }
                .frame(width: 80, height: 80)

                Text(message)
                    .font(BrandFont.fixed(15, .bold))
                    .foregroundColor(Brand.textPrimary)
                    .multilineTextAlignment(.center)
            }
            .padding(BrandScale.spaceXxl)
            .background(Brand.surfaceCard)
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Brand.outline, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
            .padding(BrandScale.spaceXl)
        }
        .onAppear {
            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                rotation = 360
            }
            withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) {
                pulsing = true
            }
        }
    }
}

// MARK: - Toast

/// Android fires a `Toast` in about fifteen places — "Ride offer posted successfully!", "Ride
/// status updated!", and so on. SwiftUI has no equivalent primitive.
///
/// An alert per site would be fifteen modal interruptions, each needing a dismiss tap. A silent
/// floating capsule would be un-iOS *and* invisible to VoiceOver, which does not announce a view
/// that merely appears. This does both halves: a brand capsule that auto-dismisses, plus an
/// explicit accessibility announcement carrying the same words.
struct ToastHost: View {
    @Binding var message: String?

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.clear
            if let message {
                Text(message)
                    .font(BrandFont.body(.semibold))
                    .foregroundColor(Brand.onPrimary)
                    .padding(.horizontal, BrandScale.spaceXl)
                    .padding(.vertical, BrandScale.spaceMd)
                    .background(Brand.textPrimary.opacity(0.92))
                    .clipShape(Capsule())
                    .padding(.bottom, 90)
                    .padding(.horizontal, BrandScale.spaceXl)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        UIAccessibility.post(notification: .announcement, argument: message)
                    }
                    .task(id: message) {
                        try? await Task.sleep(nanoseconds: 2_200_000_000)
                        withAnimation { self.message = nil }
                    }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: message)
        .allowsHitTesting(false)
    }
}
