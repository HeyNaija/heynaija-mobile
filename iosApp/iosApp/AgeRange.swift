import DeclaredAgeRange
import Shared
import UIKit

/// Apple's Declared Age Range for the shared code (IosAgeSignals.kt), which
/// cannot call Swift-only APIs. Asked only where Apple says a law requires it
/// (iOS 26.2 and later): there Apple asks the person to share their age range
/// with HeyNaija, and says whether they are 18 or older. Everywhere else, and on
/// older iOS, the app's own "I am 18 or older" stands and nobody is asked.
final class AppleDeclaredAgeRange: NSObject, AppleAgeRange {
    func check(done: @escaping (StoreAge) -> Void) {
        guard #available(iOS 26.2, *) else {
            done(.unchecked)
            return
        }
        Task { @MainActor in
            done(await Self.ask())
        }
    }

    @available(iOS 26.2, *)
    @MainActor
    private static func ask() async -> StoreAge {
        do {
            guard try await AgeRangeService.shared.isEligibleForAgeFeatures, let top = topViewController() else { return .unchecked }
            switch try await AgeRangeService.shared.requestAgeRange(ageGates: 18, in: top) {
            case .declinedSharing:
                return .unconfirmed
            case .sharing(let range):
                if let lower = range.lowerBound, lower >= 18 { return .adult }
                if let upper = range.upperBound, upper < 18 { return .minor }
                return .unconfirmed
            @unknown default:
                return .unconfirmed
            }
        } catch {
            // Apple could not answer (the app lacks the entitlement, no Apple Account): the app's own question stands.
            print("HeyNaija: Declared Age Range failed: \(error)")
            return .unchecked
        }
    }

    /// The view controller on top, for Apple's sheet to come up over.
    @MainActor
    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
