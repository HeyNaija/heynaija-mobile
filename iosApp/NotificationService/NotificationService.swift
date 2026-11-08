import CryptoKit
import Foundation
import Security
import UserNotifications

/// Opens what HeyNaija's server sealed for this phone before iOS shows it (ADR
/// 0026 in the web repository). Apple carries only "You have a new notification"
/// and `sealed`: AES-256-GCM under the key the app made (IosPush.kt), which only
/// the app and the server have. Opened, it gives the words to show, the link to
/// open when tapped, and whose notification it is. One that cannot be opened, or
/// is not for the member signed in here, keeps Apple's stand-in words.
final class NotificationService: UNNotificationServiceExtension {
    /// The app group shared with the app (APP_GROUP in IosPush.kt).
    static let group = "group.ng.heynaija.app"

    override func didReceive(_ request: UNNotificationRequest, withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        guard let content = request.content.mutableCopy() as? UNMutableNotificationContent else {
            contentHandler(request.content)
            return
        }
        if let sealed = request.content.userInfo["sealed"] as? String, let note = Self.open(sealed) {
            content.title = note.title
            content.body = note.body ?? ""
            content.userInfo = ["url": note.url]
        }
        contentHandler(content)
    }
}

/// What the server seals: the browser's notification, and whose it is (fcm-sender.ts and apns-sender.ts).
private struct Note: Decodable {
    let title: String
    let body: String?
    let url: String
    let tag: String
    let member: String
}

private extension NotificationService {
    static func open(_ sealed: String) -> Note? {
        guard let key = pushKey(),
              let bytes = base64url(sealed),
              let box = try? AES.GCM.SealedBox(combined: bytes),
              let text = try? AES.GCM.open(box, using: SymmetricKey(data: key)),
              let note = try? JSONDecoder().decode(Note.self, from: text),
              note.member == memberSignedIn()
        else { return nil }
        return note
    }

    /// The key the app made (ApnsPushDevice.key), from the group's Keychain.
    static func pushKey() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "ng.heynaija.app.shared",
            kSecAttrAccount as String: "push_key",
            kSecAttrAccessGroup as String: group,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let text = String(data: data, encoding: .utf8)
        else { return nil }
        return base64url(text)
    }

    /// Who is signed in: the app keeps them as `me` in the group's defaults (AppState.kt).
    static func memberSignedIn() -> String? {
        guard let me = UserDefaults(suiteName: group)?.string(forKey: "me")?.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: me) as? [String: Any]
        else { return nil }
        return object["id"] as? String
    }

    static func base64url(_ text: String) -> Data? {
        var base64 = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        return Data(base64Encoded: base64)
    }
}
