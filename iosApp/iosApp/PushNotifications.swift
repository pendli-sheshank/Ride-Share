import FirebaseCore
import FirebaseMessaging
import Foundation
import SwiftUI
import UIKit
import UserNotifications

/// Push notifications on iOS.
///
/// Until this existed the app could reach nobody who did not already have it open:
/// `sendNotificationAlert` in `:shared` wrote a `notifications` document, the in-app bell rendered
/// it, and that was the whole chain. `fcmToken` sat on the user model referenced by nothing, on
/// both platforms, because **a device cannot obtain an FCM token over REST** — registration goes
/// through the platform SDK or it does not happen. That is the single reason the Firebase iOS SDK
/// is here at all; `:shared` still speaks REST for auth, Firestore and Storage.
///
/// The registration itself is written by `:shared`, to `users/{uid}/private/push`, exactly as
/// Android does — see `SplitCruiserRepository.registerPushToken`. Nothing about the backend moves.

/// The last FCM token this device was issued.
///
/// A holding pen, and it needs to be one. `AppDelegate` learns the token from Firebase whenever
/// Firebase feels like telling it — at launch, on a rotation, after a restore to a new device —
/// which is frequently *before* anyone has signed in, and the registration needs a session to
/// write to. So the delegate parks it here and `ContentView` drains it once signed in.
///
/// Deliberately **not** `@MainActor`. `ContentView` holds this as a `@StateObject`, and a View's
/// property initialiser is a non-isolated context — reading a main-actor-isolated `static` from
/// there is a warning under Swift 5 and an error under Swift 6. The hop happens inside `update`
/// instead, which is where it actually matters: Firebase's callbacks arrive on unspecified
/// queues, and `@Published` must be mutated on the main thread or SwiftUI updates from the wrong
/// one.
final class PushTokenStore: ObservableObject {

    static let shared = PushTokenStore()

    /// `nil` until Firebase issues one, or forever on a build with no Firebase configuration.
    @Published private(set) var token: String?

    private init() {}

    func update(_ newToken: String?) {
        DispatchQueue.main.async { [weak self] in
            guard let self, self.token != newToken else { return }
            self.token = newToken
        }
    }
}

/// Whether this build can actually do push, and the user-facing permission.
enum SplitCruiserPush {

    /// Marks the checked-in placeholder `GoogleService-Info.plist`. Must match the file.
    ///
    /// The real plist is written from the `GOOGLE_SERVICE_INFO_PLIST` secret by the release
    /// workflow and is not in the repository. A placeholder has to be committed anyway, because
    /// the PR simulator build has no secrets and the target's Resources phase would otherwise
    /// fail on a missing file.
    static let placeholderSentinel = "PLACEHOLDER_NOT_CONFIGURED"

    /// True when a real `GoogleService-Info.plist` is present.
    ///
    /// `FirebaseApp.configure()` is deliberately **not** called on a placeholder. Firebase would
    /// configure quite happily against fake values — it validates nothing at configure time — and
    /// then hand out a token-shaped failure that looks exactly like a working registration. A
    /// build that cannot do push should do nothing, visibly, rather than appear to work. Android
    /// takes the same position: `currentToken` returns nil when there is no `FirebaseApp`.
    static var isConfigured: Bool {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let values = NSDictionary(contentsOfFile: path),
              let apiKey = values["API_KEY"] as? String
        else { return false }
        return apiKey != placeholderSentinel && !apiKey.isEmpty
    }

    /// Asks for notification permission, returning whether it is granted.
    ///
    /// Called once the user is signed in rather than at launch — iOS gives exactly one chance at
    /// this prompt, and a prompt on a login screen has no reason attached to it. Same reasoning,
    /// and same moment in the flow, as Android's API 33+ `POST_NOTIFICATIONS` request.
    @discardableResult
    static func requestAuthorization() async -> Bool {
        guard isConfigured else { return false }
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()

        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .denied:
            // Asking again is a no-op on iOS; the user has to go to Settings. Do not pester.
            return false
        case .notDetermined:
            let granted = (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            if granted {
                // Registering for remote notifications is what produces the APNs token, which
                // Firebase needs before it will issue an FCM token. Must happen on the main actor.
                await MainActor.run { UIApplication.shared.registerForRemoteNotifications() }
            }
            return granted
        @unknown default:
            return false
        }
    }
}

/// Receives the APNs token, the FCM token, and notifications that arrive while the app is open.
///
/// `iOSApp.swift` had no delegate at all before this — it is a pure SwiftUI `App`. Push requires
/// one: `didRegisterForRemoteNotificationsWithDeviceToken` has no SwiftUI equivalent.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        guard SplitCruiserPush.isConfigured else {
            // A developer build, or CI. Everything except push works — the backend is REST.
            NSLog("[SplitCruiserPush] No Firebase configuration in this build; push is off.")
            return true
        }

        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        // Only register if permission already exists. A fresh install has none, and registering
        // here would trigger the system prompt on the login screen — the thing
        // `requestAuthorization` exists to avoid.
        Task {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            if settings.authorizationStatus == .authorized {
                await MainActor.run { application.registerForRemoteNotifications() }
            }
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Handing the APNs token to Firebase is what lets it mint an FCM token. Without this the
        // `messaging(_:didReceiveRegistrationToken:)` callback below never fires with a usable
        // token, and nothing reports the omission.
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Expected on a simulator, which has no APNs. Not worth surfacing to the user: the cost
        // is one device not receiving notifications.
        NSLog("[SplitCruiserPush] APNs registration failed: \(error.localizedDescription)")
    }
}

extension AppDelegate: MessagingDelegate {

    /// Fires at launch and on every rotation — a token is not permanent.
    ///
    /// Parked rather than written straight through, because this can fire with nobody signed in
    /// (at install, or after a sign-out) and the write needs a session. `ContentView` drains the
    /// store once signed in, and re-drains on every sign-in, which is what keeps a rotated token
    /// from going stale and failing silently at send time.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        PushTokenStore.shared.update(fcmToken)
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {

    /// Shows a notification that arrives while the app is in the foreground.
    ///
    /// iOS suppresses it by default, so without this a chat message landing while the user was on
    /// another screen produced nothing at all — the same gap Android's `onMessageReceived` covers.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .badge]
    }

    /// The user tapped a notification. Bringing the app to the foreground is enough for now —
    /// deep-linking to the right ride would need the router, which is SwiftUI state this delegate
    /// cannot reach. The payload's `type` and `notificationId` are already carried by
    /// `functions/src/pushPayload.ts` for when that lands.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        NSLog("[SplitCruiserPush] Opened from a notification.")
    }
}
