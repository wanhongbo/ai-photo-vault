import Foundation

enum LongRunningTaskPhase: String, Equatable, Sendable {
    case preparing
    case scanning
    case verifying
    case encrypting
    case assembling
    case backingUp
    case restoring
    case exporting
    case completed
    case cancelled

    var localizationKey: String {
        "long_task_phase_\(rawValue)"
    }
}

struct LongRunningTaskProgress: Equatable, Sendable {
    var phase: LongRunningTaskPhase
    var current: Int
    var total: Int
    var currentFileName: String?
    var bytesWritten: Int64
    var totalBytes: Int64
    var cancellable: Bool

    var fraction: Double {
        if totalBytes > 0 {
            return min(1, max(0, Double(bytesWritten) / Double(totalBytes)))
        }
        guard total > 0 else { return 0 }
        return min(1, max(0, Double(current) / Double(total)))
    }

    static func initial(phase: LongRunningTaskPhase, cancellable: Bool = true) -> LongRunningTaskProgress {
        LongRunningTaskProgress(
            phase: phase,
            current: 0,
            total: 0,
            currentFileName: nil,
            bytesWritten: 0,
            totalBytes: 0,
            cancellable: cancellable
        )
    }
}

typealias LongRunningTaskProgressHandler = @MainActor @Sendable (LongRunningTaskProgress) -> Void
