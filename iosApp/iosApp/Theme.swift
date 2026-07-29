import SwiftUI
import Shared

// MARK: - Brand palette

/// The Split Cruiser palette, read from `SplitCruiserColors` in `:shared` so iOS and Android
/// cannot drift apart.
///
/// iOS used to draw everything in system defaults — `.blue`, `.gray`, `Color(.systemGray6)` —
/// while Android painted a custom palette. The first screen a user saw was the least branded.
///
/// The shared tokens are `0xAARRGGBB` longs; `Color(argb:)` below unpacks them.
enum Brand {
    static let surface = Color(argb: SplitCruiserColors.shared.Surface)
    static let surfaceCard = Color(argb: SplitCruiserColors.shared.SurfaceCard)
    static let surfaceMuted = Color(argb: SplitCruiserColors.shared.SurfaceMuted)

    static let primary = Color(argb: SplitCruiserColors.shared.Primary)
    static let primaryContainer = Color(argb: SplitCruiserColors.shared.PrimaryContainer)
    static let onPrimary = Color(argb: SplitCruiserColors.shared.OnPrimary)
    static let onPrimaryContainer = Color(argb: SplitCruiserColors.shared.OnPrimaryContainer)

    static let success = Color(argb: SplitCruiserColors.shared.Success)
    static let danger = Color(argb: SplitCruiserColors.shared.Danger)
    static let warning = Color(argb: SplitCruiserColors.shared.Warning)
    static let accent = Color(argb: SplitCruiserColors.shared.Accent)

    static let textPrimary = Color(argb: SplitCruiserColors.shared.TextPrimary)
    static let textSecondary = Color(argb: SplitCruiserColors.shared.TextSecondary)
    static let outline = Color(argb: SplitCruiserColors.shared.Outline)
}

/// Spacing and radius steps, from the same shared scale Android reads.
enum BrandScale {
    static let spaceXs = CGFloat(SplitCruiserScale.shared.SpaceXs)
    static let spaceSm = CGFloat(SplitCruiserScale.shared.SpaceSm)
    static let spaceMd = CGFloat(SplitCruiserScale.shared.SpaceMd)
    static let spaceLg = CGFloat(SplitCruiserScale.shared.SpaceLg)
    static let spaceXl = CGFloat(SplitCruiserScale.shared.SpaceXl)

    static let radiusSm = CGFloat(SplitCruiserScale.shared.RadiusSm)
    static let radiusMd = CGFloat(SplitCruiserScale.shared.RadiusMd)
    static let radiusLg = CGFloat(SplitCruiserScale.shared.RadiusLg)
}

extension Color {
    /// Unpacks one of the shared `0xAARRGGBB` tokens.
    init(argb: Int64) {
        let value = UInt32(truncatingIfNeeded: argb)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: Double((value >> 24) & 0xFF) / 255
        )
    }
}

// MARK: - Shared building blocks

/// The primary call-to-action, so "Log in", "Reserve a seat" and "Continue" are one button.
struct BrandButtonStyle: ButtonStyle {
    var background: Color = Brand.primary
    var isEnabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding()
            .background(isEnabled ? background : Brand.textSecondary.opacity(0.4))
            .foregroundColor(Brand.onPrimary)
            .cornerRadius(BrandScale.radiusMd)
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Android's `StatusBadge`, in SwiftUI. Same statuses, same colours.
struct StatusBadge: View {
    let status: String

    private var color: Color {
        switch status.lowercased() {
        case "active": return Brand.success
        case "completed", "matched": return Brand.primary
        case "cancelled", "declined": return Brand.danger
        case "pending": return Brand.warning
        default: return Brand.primary
        }
    }

    var body: some View {
        Text(status.uppercased())
            .font(.caption2)
            .fontWeight(.bold)
            .foregroundColor(color)
            .padding(.horizontal, BrandScale.spaceSm)
            .padding(.vertical, BrandScale.spaceXs)
            .background(color.opacity(0.15))
            .cornerRadius(BrandScale.radiusSm)
    }
}

/// The origin → destination rail, matching Android's `RouteIndicator`.
struct RouteIndicator: View {
    let origin: String
    let destination: String
    var originLabel: String?
    var destinationLabel: String?

    var body: some View {
        HStack(alignment: .center, spacing: BrandScale.spaceMd) {
            VStack(spacing: 0) {
                Image(systemName: "smallcircle.filled.circle")
                    .foregroundColor(Brand.primary)
                Rectangle()
                    .fill(Brand.outline)
                    .frame(width: 2, height: 28)
                Image(systemName: "mappin.circle.fill")
                    .foregroundColor(Brand.primary)
            }

            VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
                place(label: originLabel, value: origin)
                place(label: destinationLabel, value: destination)
            }

            Spacer(minLength: 0)
        }
    }

    private func place(label: String?, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            if let label {
                Text(label)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(Brand.textSecondary)
            }
            Text(value)
                .font(.callout)
                .fontWeight(.semibold)
                .foregroundColor(Brand.textPrimary)
        }
    }
}

/// A titled card, the SwiftUI counterpart of the cards Android's detail screens are built from.
struct BrandCard<Content: View>: View {
    let title: String?
    @ViewBuilder var content: () -> Content

    init(title: String? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceMd) {
            if let title {
                Text(title.uppercased())
                    .font(.caption2)
                    .fontWeight(.black)
                    .kerning(1)
                    .foregroundColor(Brand.textSecondary)
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
                .font(.callout)
                .foregroundColor(Brand.textSecondary)
            Spacer()
            Text(value)
                .font(.callout)
                .fontWeight(.semibold)
                .foregroundColor(valueColor)
                .multilineTextAlignment(.trailing)
        }
    }
}

/// The avatar, using the user's own picture when there is one.
///
/// `AsyncImage` needs iOS 15, which this app already targets; the SF Symbol is the fallback for
/// a user who has not uploaded one (and for a URL that fails to load).
struct BrandAvatar: View {
    let avatarUrl: String
    let name: String
    var size: CGFloat = 64

    var body: some View {
        Group {
            if let url = URL(string: avatarUrl), avatarUrl.hasPrefix("http") {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    initials
                }
            } else {
                initials
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var initials: some View {
        ZStack {
            LinearGradient(
                colors: [Brand.primary, Brand.onPrimaryContainer],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Text(String(name.prefix(1)).uppercased())
                .font(.system(size: size * 0.4, weight: .black))
                .foregroundColor(Brand.onPrimary)
        }
    }
}

/// The empty state, matching Android's `SplitCruiserEmptyState`: icon, title, description and an
/// optional call to action.
struct BrandEmptyState: View {
    let icon: String
    let title: String
    let description: String
    var actionLabel: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: BrandScale.spaceLg) {
            ZStack {
                Circle()
                    .fill(Brand.primary.opacity(0.12))
                    .frame(width: 96, height: 96)
                Image(systemName: icon)
                    .font(.system(size: 36))
                    .foregroundColor(Brand.primary)
            }

            Text(title)
                .font(.headline)
                .foregroundColor(Brand.textPrimary)

            Text(description)
                .font(.callout)
                .foregroundColor(Brand.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, BrandScale.spaceXl)

            if let actionLabel, let action {
                Button(actionLabel, action: action)
                    .buttonStyle(BrandButtonStyle())
                    .padding(.horizontal, BrandScale.spaceXl)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
