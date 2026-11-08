import SwiftUI
import UserNotifications
import Shared

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// What iOS tells the app about push (ADR 0026 in the web repository), passed on
/// to the shared code: the token Apple gave this installation, and notifications
/// that arrive while the app is open or are tapped. The notification service
/// extension has already opened what the server sealed, so a notification here
/// carries its words and, as `url`, the link to open.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Before launching ends, so that the tap that started the app is not missed.
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        ApnsTokens.shared.registered(token: deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        ApnsTokens.shared.failed(error: error)
    }

    /// One arriving while the app is open shows as it would otherwise, as on Android, and the unread count moves.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        PushEvents.shared.shown()
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let link = response.notification.request.content.userInfo["url"] as? String {
            PushEvents.shared.open(link: link)
        }
        completionHandler()
    }
}
