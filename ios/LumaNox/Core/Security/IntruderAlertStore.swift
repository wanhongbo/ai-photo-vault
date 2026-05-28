import AVFoundation
import Foundation
import UIKit

enum IntruderCaptureStatus: String, Codable, Equatable {
    case captured
    case permissionDenied
    case unavailable
    case failed
}

struct IntruderAlertRecord: Codable, Identifiable, Equatable {
    let id: UUID
    let createdAtMs: Int64
    let attemptedPin: String
    let capturePath: String?
    let captureStatus: IntruderCaptureStatus
}

@MainActor
final class IntruderAlertStore: ObservableObject {
    static let shared = IntruderAlertStore()

    @Published private(set) var records: [IntruderAlertRecord] = []
    @Published private(set) var isEnabled: Bool
    @Published private(set) var cameraAuthorized: Bool

    private let enabledKey = "intruder_alert_enabled"
    private let recordLimit = 50

    private init() {
        isEnabled = UserDefaults.standard.bool(forKey: enabledKey)
        cameraAuthorized = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        load()
    }

    func setEnabled(_ enabled: Bool) {
        isEnabled = enabled
        UserDefaults.standard.set(enabled, forKey: enabledKey)
    }

    func requestCameraPermissionIfNeeded() async {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            cameraAuthorized = true
        case .notDetermined:
            let lockToken = AppLockManager.shared.beginSystemInteraction()
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            AppLockManager.shared.endSystemInteraction(lockToken)
            cameraAuthorized = granted
        default:
            cameraAuthorized = false
        }
    }

    func refreshCameraAuthorization() {
        cameraAuthorized = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
    }

    func load() {
        records = Self.readRecords().sorted { $0.createdAtMs > $1.createdAtMs }
        refreshCameraAuthorization()
    }

    func clearAll() {
        try? FileManager.default.removeItem(at: Self.alertsDirectory)
        try? FileManager.default.removeItem(at: Self.indexURL)
        records = []
    }

    func delete(_ record: IntruderAlertRecord) {
        if let capturePath = record.capturePath {
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: capturePath))
        }
        let next = records.filter { $0.id != record.id }
        Self.writeRecords(next)
        records = next
    }

    func thumbnail(for record: IntruderAlertRecord) async -> UIImage? {
        guard let capturePath = record.capturePath else { return nil }
        return await Task.detached(priority: .utility) {
            guard let data = try? VaultCipher.shared.decryptFile(at: URL(fileURLWithPath: capturePath)) else { return nil }
            return UIImage(data: data)
        }.value
    }

    func recordFailedPinAttempt(_ attemptedPin: String) {
        guard isEnabled else { return }
        let id = UUID()
        Task {
            let capture = await Self.captureEncryptedImage(recordId: id)
            let record = IntruderAlertRecord(
                id: id,
                createdAtMs: Int64(Date().timeIntervalSince1970 * 1000),
                attemptedPin: attemptedPin,
                capturePath: capture.path,
                captureStatus: capture.status
            )
            var next = ([record] + records).sorted { $0.createdAtMs > $1.createdAtMs }
            if next.count > recordLimit {
                let dropped = next.dropFirst(recordLimit)
                dropped.forEach { old in
                    if let capturePath = old.capturePath {
                        try? FileManager.default.removeItem(at: URL(fileURLWithPath: capturePath))
                    }
                }
                next = Array(next.prefix(recordLimit))
            }
            Self.writeRecords(next)
            records = next
        }
    }

    private static func captureEncryptedImage(recordId: UUID) async -> (status: IntruderCaptureStatus, path: String?) {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        guard status == .authorized else {
            return (status == .denied || status == .restricted) ? (.permissionDenied, nil) : (.unavailable, nil)
        }

        do {
            try FileManager.default.createDirectory(at: alertsDirectory, withIntermediateDirectories: true)
            let tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("intruder_capture_\(recordId.uuidString).jpg")
            let encryptedURL = alertsDirectory
                .appendingPathComponent("capture_\(recordId.uuidString).jpg.lnx")
            try await IntruderCaptureService.shared.captureFrontCamera(to: tempURL)
            try VaultCipher.shared.encryptFile(at: tempURL, to: encryptedURL)
            try? FileManager.default.removeItem(at: tempURL)
            return (.captured, encryptedURL.path)
        } catch IntruderCaptureError.permissionDenied {
            return (.permissionDenied, nil)
        } catch IntruderCaptureError.unavailable {
            return (.unavailable, nil)
        } catch {
            return (.failed, nil)
        }
    }

    private static func readRecords() -> [IntruderAlertRecord] {
        guard FileManager.default.fileExists(atPath: indexURL.path) else { return [] }
        return (try? VaultCipher.shared.decryptFile(at: indexURL))
            .flatMap { try? JSONDecoder().decode([IntruderAlertRecord].self, from: $0) }
            ?? []
    }

    private static func writeRecords(_ records: [IntruderAlertRecord]) {
        do {
            try FileManager.default.createDirectory(at: alertsDirectory, withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(records)
            let temp = alertsDirectory.appendingPathComponent("intruder_alerts_plain_\(UUID().uuidString).json")
            try data.write(to: temp, options: .atomic)
            try VaultCipher.shared.encryptFile(at: temp, to: indexURL)
            try? FileManager.default.removeItem(at: temp)
        } catch {
            try? FileManager.default.removeItem(at: alertsDirectory.appendingPathComponent("intruder_alerts_plain.json"))
        }
    }

    private static var lumaSupportDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LumaNox", isDirectory: true)
    }

    private static var alertsDirectory: URL {
        lumaSupportDirectory.appendingPathComponent("intruder_alerts", isDirectory: true)
    }

    private static var indexURL: URL {
        lumaSupportDirectory.appendingPathComponent("intruder_alerts_v1.json.lnx", isDirectory: false)
    }
}

enum IntruderCaptureError: Error {
    case permissionDenied
    case unavailable
    case noData
}

final class IntruderCaptureService: NSObject {
    static let shared = IntruderCaptureService()

    private let queue = DispatchQueue(label: "com.xpx.vault.intruder.capture")
    private var delegates: [UUID: IntruderPhotoCaptureDelegate] = [:]

    func captureFrontCamera(to outputURL: URL) async throws {
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else {
            throw IntruderCaptureError.permissionDenied
        }

        let session = AVCaptureSession()
        session.sessionPreset = .photo
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input)
        else {
            throw IntruderCaptureError.unavailable
        }
        let output = AVCapturePhotoOutput()
        guard session.canAddOutput(output) else {
            throw IntruderCaptureError.unavailable
        }

        session.beginConfiguration()
        session.addInput(input)
        session.addOutput(output)
        session.commitConfiguration()

        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                session.startRunning()
                let token = UUID()
                let delegate = IntruderPhotoCaptureDelegate(outputURL: outputURL) { [weak self] result in
                    session.stopRunning()
                    Task { @MainActor in self?.delegates[token] = nil }
                    continuation.resume(with: result)
                }
                DispatchQueue.main.sync { self.delegates[token] = delegate }
                output.capturePhoto(with: AVCapturePhotoSettings(), delegate: delegate)
            }
        }
    }
}

private final class IntruderPhotoCaptureDelegate: NSObject, AVCapturePhotoCaptureDelegate {
    private let outputURL: URL
    private let completion: (Result<Void, Error>) -> Void
    private var completed = false

    init(outputURL: URL, completion: @escaping (Result<Void, Error>) -> Void) {
        self.outputURL = outputURL
        self.completion = completion
    }

    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        guard !completed else { return }
        if let error {
            completed = true
            completion(.failure(error))
            return
        }
        guard let data = photo.fileDataRepresentation() else {
            completed = true
            completion(.failure(IntruderCaptureError.noData))
            return
        }
        do {
            try data.write(to: outputURL, options: .atomic)
            completed = true
            completion(.success(()))
        } catch {
            completed = true
            completion(.failure(error))
        }
    }
}
