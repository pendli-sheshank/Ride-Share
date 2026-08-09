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
///
/// Every token in `SplitCruiserColors` is wrapped here. Two of them used to be missing, and both
/// absences showed: without `surfaceTrack` there was nothing to draw the Rider/Host segmented
/// control's track in, and without `info` the status badge painted `completed` and `matched` in
/// `primary` while Android painted them `#3B82F6`.
enum Brand {
    static let surface = Color(argb: SplitCruiserColors.shared.Surface)
    static let surfaceCard = Color(argb: SplitCruiserColors.shared.SurfaceCard)
    static let surfaceMuted = Color(argb: SplitCruiserColors.shared.SurfaceMuted)
    static let surfaceTrack = Color(argb: SplitCruiserColors.shared.SurfaceTrack)

    static let primary = Color(argb: SplitCruiserColors.shared.Primary)
    static let primaryContainer = Color(argb: SplitCruiserColors.shared.PrimaryContainer)
    static let onPrimary = Color(argb: SplitCruiserColors.shared.OnPrimary)
    static let onPrimaryContainer = Color(argb: SplitCruiserColors.shared.OnPrimaryContainer)

    static let success = Color(argb: SplitCruiserColors.shared.Success)
    static let danger = Color(argb: SplitCruiserColors.shared.Danger)
    static let info = Color(argb: SplitCruiserColors.shared.Info)
    static let warning = Color(argb: SplitCruiserColors.shared.Warning)
    static let accent = Color(argb: SplitCruiserColors.shared.Accent)

    static let textPrimary = Color(argb: SplitCruiserColors.shared.TextPrimary)
    static let textSecondary = Color(argb: SplitCruiserColors.shared.TextSecondary)
    static let outline = Color(argb: SplitCruiserColors.shared.Outline)
}

/// Colours Android writes as literals rather than tokens.
///
/// These are not in `SplitCruiserColors`, so they are duplicated here to match the pixels. Each
/// one is a token the two platforms should eventually reconcile — `womenOnlyTag` in particular is
/// a second pink sitting next to `Brand.accent` for the same concept.
enum BrandLiteral {
    static let womenOnlyTag = Color(argb: Int64(0xFFF4_72B6))   // TripOfferCard's "WOMEN ONLY" chip
    static let pendingAmber = Color(argb: Int64(0xFFD9_7706))   // pending / full foreground
    static let pendingAmberFill = Color(argb: Int64(0xFFFE_F3C7))
    static let cancelRed = Color(argb: Int64(0xFFDC_2626))
    static let cancelRedFill = Color(argb: Int64(0xFFFE_E2E2))
    static let destinationOrange = Color(argb: Int64(0xFFF9_7316))
    static let seatsViolet = Color(argb: Int64(0xFF8B_5CF6))
    static let notesTeal = Color(argb: Int64(0xFF14_B8A6))
    static let heroEyebrow = Color(argb: Int64(0xFFF5_9E0B))
    static let osmSuggestion = Color(argb: Int64(0xFF38_BDF8))
    static let neighborhood = Color(argb: Int64(0xFFA8_55F7))
}

/// Spacing and radius steps, from the same shared scale Android reads.
enum BrandScale {
    static let spaceXs = CGFloat(SplitCruiserScale.shared.SpaceXs)
    static let spaceSm = CGFloat(SplitCruiserScale.shared.SpaceSm)
    static let spaceMd = CGFloat(SplitCruiserScale.shared.SpaceMd)
    static let spaceLg = CGFloat(SplitCruiserScale.shared.SpaceLg)
    static let spaceXl = CGFloat(SplitCruiserScale.shared.SpaceXl)
    static let spaceXxl = CGFloat(SplitCruiserScale.shared.SpaceXxl)

    static let radiusSm = CGFloat(SplitCruiserScale.shared.RadiusSm)
    static let radiusMd = CGFloat(SplitCruiserScale.shared.RadiusMd)
    static let radiusLg = CGFloat(SplitCruiserScale.shared.RadiusLg)
    /// 999 on the shared scale. Prefer `Capsule()`/`Circle()` where the shape is the whole point;
    /// this is here for the places Android passes a radius rather than a shape.
    static let radiusPill = CGFloat(SplitCruiserScale.shared.RadiusPill)
}

/// The type scale, in points, from `SplitCruiserScale`.
///
/// Android sets explicit `sp` sizes at every call site rather than using typography roles, so
/// matching it means using explicit sizes here too — SwiftUI's semantic `.caption`/`.headline`
/// resolve to different numbers and cannot line up. `.black` (900) is the house weight for screen
/// titles and prices, `.bold` for card titles, `.semibold` for chip labels.
enum BrandFont {
    static let eyebrowSize = CGFloat(SplitCruiserScale.shared.TextEyebrow)
    static let captionSize = CGFloat(SplitCruiserScale.shared.TextCaption)
    static let bodySize = CGFloat(SplitCruiserScale.shared.TextBody)
    static let titleSize = CGFloat(SplitCruiserScale.shared.TextTitle)
    static let headlineSize = CGFloat(SplitCruiserScale.shared.TextHeadline)

    static func eyebrow(_ weight: Font.Weight = .bold) -> Font {
        .system(size: eyebrowSize, weight: weight)
    }
    static func caption(_ weight: Font.Weight = .regular) -> Font {
        .system(size: captionSize, weight: weight)
    }
    static func body(_ weight: Font.Weight = .regular) -> Font {
        .system(size: bodySize, weight: weight)
    }
    static func title(_ weight: Font.Weight = .bold) -> Font {
        .system(size: titleSize, weight: weight)
    }
    static func headline(_ weight: Font.Weight = .black) -> Font {
        .system(size: headlineSize, weight: weight)
    }
    /// Sizes Android uses that are off the shared scale.
    static func fixed(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight)
    }
}

/// The colour a ride or match status renders in.
///
/// A free function, not a method on the badge, because six different cards tint by status and
/// they must all read the same table. Android learned this the hard way: with five independent
/// `when (status)` blocks, a status one card handled fell through another's `else`.
///
/// Mirrors `statusColor` in `app/.../ui/SplitCruiserApp.kt`.
func statusColor(_ status: String) -> Color {
    switch status.lowercased() {
    case "active": return Brand.success
    case "full": return Brand.warning
    case "closed": return Brand.textSecondary
    case "completed", "matched": return Brand.info
    case "cancelled", "declined": return Brand.danger
    case "pending": return Brand.warning
    default: return Brand.primary
    }
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
