import SwiftUI
import Shared

// The component vocabulary every screen is built from, mirroring the reusable composables in
// `app/.../ui/SplitCruiserApp.kt`. Extracting these is the whole point: Android's `RouteIndicator`
// replaced seven copies that each had their own dot size and rail height, and `statusColor`
// replaced five independent `when (status)` blocks that disagreed about the same status.
//
// `Theme.swift` holds tokens only. Anything that renders lives here.

// MARK: - Buttons

/// The primary call-to-action, so "Log in", "Reserve a seat" and "Post ride offer" are one button.
///
/// Android draws these at a fixed 54dp height with a 16sp bold label; matching that matters
/// because several iOS forms currently render their submit as small tinted text.
struct BrandButtonStyle: ButtonStyle {
    var background: Color = Brand.primary
    var isEnabled: Bool = true
    var height: CGFloat = 54

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(BrandFont.title(.bold))
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(isEnabled ? background : Brand.textSecondary.opacity(0.4))
            .foregroundColor(Brand.onPrimary)
            .cornerRadius(BrandScale.radiusMd)
            // Android animates a 0.95 press scale on a spring; this is the same gesture.
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

/// The outlined secondary action ("View Details", "Cancel ride").
struct BrandOutlineButtonStyle: ButtonStyle {
    var tint: Color = Brand.primary
    var height: CGFloat = 44

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(BrandFont.caption(.bold))
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .foregroundColor(tint)
            .background(Color.clear)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                    .stroke(tint.opacity(0.5), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

// MARK: - Status

/// Android's `StatusBadge`. Colour comes from the shared `statusColor` table in `Theme.swift`.
struct StatusBadge: View {
    let status: String
    var compact: Bool = false

    var body: some View {
        Text(status.uppercased())
            .font(BrandFont.fixed(compact ? 9 : 10, .bold))
            .foregroundColor(statusColor(status))
            .padding(.horizontal, compact ? 6 : BrandScale.spaceSm)
            .padding(.vertical, compact ? 2 : BrandScale.spaceXs)
            .background(statusColor(status).opacity(compact ? 0.10 : 0.15))
            .cornerRadius(compact ? 6 : BrandScale.radiusSm)
    }
}

// MARK: - Card furniture

/// The all-caps icon + label that heads every card section.
struct CardEyebrow: View {
    let label: String
    let systemImage: String
    var tint: Color = Brand.primary
    var compact: Bool = false

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
                .font(.system(size: compact ? 14 : 16))
                .foregroundColor(tint)
            Text(label.uppercased())
                .font(BrandFont.fixed(compact ? 10 : BrandFont.eyebrowSize, .bold))
                .kerning(compact ? 0.8 : 1)
                .foregroundColor(tint)
        }
    }
}

/// A small label-over-value pair. Every card footer on Android is built from these.
struct CardStat: View {
    let label: String
    let value: String
    var alignment: HorizontalAlignment = .leading
    var valueColor: Color = Brand.textPrimary

    var body: some View {
        VStack(alignment: alignment, spacing: 2) {
            Text(label.uppercased())
                .font(BrandFont.fixed(10, .bold))
                .foregroundColor(Brand.textSecondary)
            Text(value)
                .font(BrandFont.caption(.bold))
                .foregroundColor(valueColor)
        }
        .frame(maxWidth: .infinity, alignment: alignment == .leading ? .leading
                                    : alignment == .trailing ? .trailing : .center)
    }
}

/// Android's `FormSection` — a titled group of fields on a card.
///
/// Deliberately *not* SwiftUI `Form`/`Section`: those re-import the system grouped background
/// (which is how three screens ended up not painting `Brand.surface`) and they coalesce a row's
/// buttons into one tap target, which is what made the location suggestions fire the wrong row.
struct FormSection<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title.uppercased())
                .font(BrandFont.eyebrow(.black))
                .kerning(1)
                .foregroundColor(Brand.textSecondary)
                .padding(.leading, BrandScale.spaceXs)
                .padding(.bottom, BrandScale.spaceSm)

            VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
                content()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(BrandScale.spaceMd)
            .background(Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusLg)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                    .stroke(Brand.outline, lineWidth: 1)
            )
        }
    }
}

/// The heading above each group on the My-trips tab.
struct TripsSectionHeader: View {
    let title: String
    var topSpacing: CGFloat = BrandScale.spaceXl

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: topSpacing)
            Text(title)
                .font(BrandFont.title(.black))
                .foregroundColor(Brand.textPrimary)
                .padding(.bottom, BrandScale.spaceSm)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A titled card, the counterpart of the cards Android's detail screens are built from.
struct BrandCard<Content: View>: View {
    let title: String?
    var tint: Color = Brand.textSecondary
    @ViewBuilder var content: () -> Content

    init(title: String? = nil,
         tint: Color = Brand.textSecondary,
         @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.tint = tint
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
            if let title {
                Text(title.uppercased())
                    .font(BrandFont.eyebrow(.black))
                    .kerning(1)
                    .foregroundColor(tint)
            }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(BrandScale.spaceLg)
        .background(Brand.surfaceCard)
        .cornerRadius(BrandScale.radiusLg)
        .overlay(
            RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                .stroke(Brand.outline, lineWidth: 1)
        )
    }
}

/// A `Label:` / `value` row, used throughout the detail screens.
struct DetailRow: View {
    let label: String
    let value: String
    var valueColor: Color = Brand.textPrimary

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(BrandFont.body())
                .foregroundColor(Brand.textSecondary)
            Spacer()
            Text(value)
                .font(BrandFont.body(.semibold))
                .foregroundColor(valueColor)
                .multilineTextAlignment(.trailing)
        }
    }
}

// MARK: - Route

/// The metrics Android's `RouteScale` defines. Marker size, rail height and text size move
/// together, which is why they live in one type rather than as loose parameters.
enum RouteScale {
    case compact, card, detail

    var marker: CGFloat {
        switch self {
        case .compact: return 12
        case .card: return 8
        case .detail: return 16
        }
    }
    /// With pins the card scale draws a larger glyph than its plain dot.
    var pinnedMarker: CGFloat {
        switch self {
        case .compact: return 12
        case .card: return 14
        case .detail: return 16
        }
    }
    var railWidth: CGFloat { self == .compact ? 1.5 : 2 }
    var railHeight: CGFloat {
        switch self {
        case .compact: return 14
        case .card: return 24
        case .detail: return 40
        }
    }
    var gap: CGFloat {
        switch self {
        case .compact: return 10
        case .card: return 12
        case .detail: return 22
        }
    }
    var textSize: CGFloat {
        switch self {
        case .compact: return 12
        case .card: return 14
        case .detail: return 15
        }
    }
}

/// The origin → rail → destination unit. Seven Android screens use this; before it was extracted
/// each had its own dot size and rail height.
struct RouteIndicator: View {
    let origin: String
    let destination: String
    var originLabel: String?
    var destinationLabel: String?
    var scale: RouteScale = .card
    var pins: Bool = false
    /// The faded variant used by past-ride cards.
    var muted: Bool = false

    private var markerSize: CGFloat { muted ? 6 : (pins ? scale.pinnedMarker : scale.marker) }
    private var railWidth: CGFloat { muted ? 1.5 : scale.railWidth }
    private var railHeight: CGFloat { muted ? 18 : scale.railHeight }
    private var gap: CGFloat { muted ? 6 : scale.gap }
    private var textSize: CGFloat { muted ? 13 : scale.textSize }
    private var textWeight: Font.Weight { (muted || pins) ? .semibold : .bold }
    private var opacity: Double { muted ? 0.5 : 1 }

    var body: some View {
        HStack(alignment: .center, spacing: gap) {
            VStack(spacing: 0) {
                marker(isOrigin: true)
                Rectangle()
                    .fill(Brand.outline)
                    .frame(width: railWidth, height: railHeight)
                marker(isOrigin: false)
            }
            .opacity(opacity)

            VStack(alignment: .leading, spacing: railHeight * 0.4) {
                place(label: originLabel, value: origin)
                place(label: destinationLabel, value: destination)
            }

            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private func marker(isOrigin: Bool) -> some View {
        if pins {
            Image(systemName: isOrigin ? "smallcircle.filled.circle" : "mappin.circle.fill")
                .font(.system(size: markerSize))
                .foregroundColor(Brand.primary)
        } else {
            Circle()
                .fill(isOrigin ? Brand.primaryContainer : Brand.primary)
                .frame(width: markerSize, height: markerSize)
        }
    }

    private func place(label: String?, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            if let label {
                Text(label)
                    .font(BrandFont.fixed(9, .bold))
                    .foregroundColor(Brand.textSecondary)
            }
            Text(value)
                .font(BrandFont.fixed(textSize, textWeight))
                .foregroundColor(Brand.textPrimary.opacity(muted ? 0.85 : 1))
                .lineLimit(1)
                .truncationMode(.tail)
        }
    }
}

// MARK: - Avatar

/// The user avatar, matching Android's `StudentAvatar`.
///
/// Four branches, in the same order on both platforms: an http URL loads an uploaded photo; one
/// of the twelve `avatar_NN` keys draws its vector portrait; a legacy `preset_*` key still draws
/// its emoji; anything else falls back to the initial on a gradient.
///
/// The key is stored *as* the avatarUrl string rather than as a separate field, so the legacy
/// branch cannot be dropped — anyone who picked one of the old object emoji still has that key on
/// their user document, and removing it would turn their avatar into a bare letter.
///
/// The key list itself lives in `SplitCruiserAvatars` in `:shared`; it used to be written out
/// three times across the two platforms.
struct StudentAvatar: View {
    let avatarUrl: String
    let name: String
    var size: CGFloat = 64
    var fontSize: CGFloat = 24

    /// `avatar_07` → `Avatar07`, the imageset name the generator writes.
    static func imageName(for key: String) -> String {
        "Avatar" + key.replacingOccurrences(of: "avatar_", with: "")
    }

    var body: some View {
        Group {
            if avatarUrl.hasPrefix("http"), let url = URL(string: avatarUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    fallback
                }
            } else if SplitCruiserAvatars.shared.isAvatarKey(avatarUrl: avatarUrl) {
                Image(Self.imageName(for: avatarUrl))
                    .resizable()
                    .scaledToFill()
            } else if let emoji = SplitCruiserAvatars.shared.legacyEmoji(avatarUrl: avatarUrl) {
                ZStack {
                    Brand.primaryContainer
                    Text(emoji).font(.system(size: size * 0.5))
                }
            } else {
                fallback
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(SplitCruiserAvatars.shared.accessibilityLabel(avatarUrl: avatarUrl))
    }

    private var fallback: some View {
        ZStack {
            LinearGradient(
                colors: [Brand.primary, Brand.onPrimaryContainer],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Text(String(name.prefix(1)).uppercased())
                .font(.system(size: fontSize, weight: .black))
                .foregroundColor(Brand.onPrimary)
        }
    }
}

// MARK: - Empty state

/// Which accent the empty state's illustration uses. Android's `illustrationType`.
enum EmptyStateAccent {
    case generic, hosted, joined, past

    var color: Color {
        switch self {
        case .hosted, .generic: return Brand.primary
        case .joined: return Brand.success
        case .past: return Brand.textSecondary
        }
    }
}

/// Android's `SplitCruiserEmptyState`: a pulsing illustration, a title, a description and an
/// optional call to action.
struct BrandEmptyState: View {
    let icon: String
    let title: String
    let description: String
    var actionLabel: String?
    var action: (() -> Void)?
    var illustrationType: EmptyStateAccent = .generic

    @State private var pulsing = false

    private var accent: Color { illustrationType.color }

    var body: some View {
        VStack(spacing: BrandScale.spaceSm) {
            ZStack {
                RadialGradient(
                    colors: [accent.opacity(0.18), .clear],
                    center: .center,
                    startRadius: 0,
                    endRadius: 70
                )
                Circle()
                    .stroke(accent.opacity(0.25), lineWidth: 2)
                    .frame(width: 95, height: 95)
                    .scaleEffect(pulsing ? 1.06 : 0.94)

                ZStack {
                    Circle().fill(Brand.surfaceCard)
                    Circle().stroke(accent, lineWidth: 2)
                    Image(systemName: icon)
                        .font(.system(size: 28))
                        .foregroundColor(accent)
                }
                .frame(width: 68, height: 68)
            }
            .frame(width: 140, height: 140)
            .onAppear { pulsing = true }
            .animation(
                .easeInOut(duration: 2.4).repeatForever(autoreverses: true),
                value: pulsing
            )

            Text(title)
                .font(BrandFont.fixed(18, .black))
                .foregroundColor(Brand.textPrimary)
                .multilineTextAlignment(.center)

            Text(description)
                .font(BrandFont.fixed(13))
                .foregroundColor(Brand.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.horizontal, BrandScale.spaceXl)

            if let actionLabel, let action {
                Button(actionLabel, action: action)
                    .buttonStyle(BrandButtonStyle(height: 44))
                    .padding(.horizontal, BrandScale.spaceXl)
                    .padding(.top, BrandScale.spaceSm)
            }
        }
        .padding(.vertical, BrandScale.spaceXxl)
        .padding(.horizontal, BrandScale.spaceXl)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Chips

/// The feed's filter chips. Selected is a solid primary fill; unselected is a bordered card.
struct BrandFilterChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(BrandFont.caption(.bold))
                .foregroundColor(isSelected ? Brand.onPrimary : Brand.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(isSelected ? Brand.primary : Brand.surfaceCard)
                .clipShape(Capsule())
                .overlay(
                    Capsule().stroke(isSelected ? Color.clear : Brand.outline, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("filter_chip_\(label)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

/// The quick-place chips under the filter row ("Snell", "Airport", …).
struct QuickPlaceChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: 12))
                Text(label)
                    .font(BrandFont.eyebrow(.semibold))
            }
            .foregroundColor(isSelected ? Brand.success : Brand.textSecondary)
            .padding(.horizontal, BrandScale.spaceMd)
            .padding(.vertical, 6)
            .background(isSelected ? Brand.success.opacity(0.25) : Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusMd)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                    .stroke(isSelected ? Brand.success : Brand.outline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Debug instrumentation

/// Backend connectivity, in debug builds only.
///
/// This is developer instrumentation. It used to sit on the profile screen with the same visual
/// weight as the user's rating, which is to say it shipped to riders.
struct FirebaseStatusPill: View {
    let isEnabled: Bool

    var body: some View {
        #if DEBUG
        let tint = isEnabled ? Brand.success : Brand.primary
        HStack(spacing: 4) {
            Image(systemName: isEnabled ? "cloud.fill" : "cloud.slash")
                .font(.system(size: 14))
            Text(isEnabled ? "Firebase Live" : "Sandbox Mode")
                .font(BrandFont.eyebrow(.bold))
        }
        .foregroundColor(tint)
        .padding(.horizontal, BrandScale.spaceMd)
        .padding(.vertical, 6)
        .background(tint.opacity(0.15))
        .clipShape(Capsule())
        .overlay(Capsule().stroke(tint.opacity(0.5), lineWidth: 1))
        #else
        EmptyView()
        #endif
    }
}
