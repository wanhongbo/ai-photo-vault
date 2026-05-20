import Foundation
import LocalAuthentication

struct BiometricAvailability {
    let canEvaluate: Bool
    let biometryType: LABiometryType
    let errorMessage: String?
}

final class BiometricAuthService {
    static let shared = BiometricAuthService()
    private init() {}

    func availability() -> BiometricAvailability {
        let context = LAContext()
        var err: NSError?
        let ok = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &err)
        return BiometricAvailability(
            canEvaluate: ok,
            biometryType: context.biometryType,
            errorMessage: err?.localizedDescription
        )
    }

    func authenticate(reason: String) async -> Result<Void, Error> {
        let context = LAContext()
        context.localizedCancelTitle = L10n.commonCancel
        do {
            let ok = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            )
            return ok ? .success(()) : .failure(BiometricError.failed)
        } catch {
            return .failure(error)
        }
    }
}

enum BiometricError: LocalizedError {
    case failed
    var errorDescription: String? { L10n.tr("lock_biometric_failed_use_pin") }
}
