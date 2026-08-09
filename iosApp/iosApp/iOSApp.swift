import SwiftUI
import UIKit

@main
struct iOSApp: App {

    init() {
        Self.applyBrandAppearance()
    }

    var body: some Scene {
        WindowGroup {
            // No NavigationView here.
            //
            // This used to wrap ContentView, while five screens below opened one of their own.
            // Nested NavigationViews render a doubled navigation bar, lose vertical space and
            // make push/pop unreliable — and because TARGETED_DEVICE_FAMILY includes iPad, the
            // outer one resolved to a split view there and broke the layout outright. Each screen
            // now owns a single NavigationStack.
            ContentView()
                // Info.plist already pins UIUserInterfaceStyle to Light, so this is belt and
                // braces at runtime — but Xcode Previews ignore the plist, and a preview that
                // renders dark is a preview that cannot be used to check parity.
                .preferredColorScheme(.light)
        }
    }

    /// Paints the two UIKit-backed bars with the shared tokens.
    ///
    /// SwiftUI has no declarative hook for either, so they follow the system appearance unless
    /// told otherwise — which is why the tab bar's unselected items were system grey while
    /// Android drew them in `TextSecondary`.
    private static func applyBrandAppearance() {
        let surfaceCard = UIColor(Brand.surfaceCard)
        let primary = UIColor(Brand.primary)
        let textSecondary = UIColor(Brand.textSecondary)

        let tabBar = UITabBarAppearance()
        tabBar.configureWithOpaqueBackground()
        tabBar.backgroundColor = surfaceCard
        for item in [tabBar.stackedLayoutAppearance,
                     tabBar.inlineLayoutAppearance,
                     tabBar.compactInlineLayoutAppearance] {
            item.selected.iconColor = primary
            item.selected.titleTextAttributes = [.foregroundColor: primary]
            item.normal.iconColor = textSecondary
            item.normal.titleTextAttributes = [.foregroundColor: textSecondary]
        }
        UITabBar.appearance().standardAppearance = tabBar
        UITabBar.appearance().scrollEdgeAppearance = tabBar

        let navBar = UINavigationBarAppearance()
        navBar.configureWithOpaqueBackground()
        navBar.backgroundColor = surfaceCard
        navBar.titleTextAttributes = [.foregroundColor: UIColor(Brand.textPrimary)]
        navBar.largeTitleTextAttributes = [.foregroundColor: UIColor(Brand.textPrimary)]
        UINavigationBar.appearance().standardAppearance = navBar
        UINavigationBar.appearance().scrollEdgeAppearance = navBar
        UINavigationBar.appearance().compactAppearance = navBar
    }
}
