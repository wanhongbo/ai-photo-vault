import AVFoundation
import UIKit

@MainActor
final class CameraSessionController: NSObject, ObservableObject {
    @Published private(set) var isRunning = false
    @Published private(set) var permissionDenied = false
    @Published private(set) var flashMode: AVCaptureDevice.FlashMode = .off
    @Published private(set) var isRecording = false

    let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "com.xpx.vault.camera.session")
    private var photoOutput = AVCapturePhotoOutput()
    private var movieOutput = AVCaptureMovieFileOutput()
    private var currentInput: AVCaptureDeviceInput?
    private var currentPosition: AVCaptureDevice.Position = .back
    private var recordingURL: URL?
    private var photoCompletion: ((Result<URL, Error>) -> Void)?

    func configure() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            startSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                Task { @MainActor in
                    if granted {
                        self?.startSession()
                    } else {
                        self?.permissionDenied = true
                    }
                }
            }
        default:
            permissionDenied = true
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            self?.session.stopRunning()
            Task { @MainActor in self?.isRunning = false }
        }
    }

    func attachPreview(to view: UIView) {
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.sublayers?.forEach { $0.removeFromSuperlayer() }
        view.layer.addSublayer(layer)
    }

    func flipCamera() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            let next: AVCaptureDevice.Position = currentPosition == .back ? .front : .back
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: next),
                  let input = try? AVCaptureDeviceInput(device: device) else { return }
            session.beginConfiguration()
            if let currentInput { session.removeInput(currentInput) }
            if session.canAddInput(input) {
                session.addInput(input)
                currentInput = input
                currentPosition = next
            }
            session.commitConfiguration()
        }
    }

    func cycleFlash() {
        flashMode = switch flashMode {
        case .off: .on
        case .on: .auto
        default: .off
        }
    }

    func capturePhoto(completion: @escaping (Result<URL, Error>) -> Void) {
        photoCompletion = completion
        let settings = AVCapturePhotoSettings()
        if photoOutput.supportedFlashModes.contains(flashMode) {
            settings.flashMode = flashMode
        }
        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    func toggleRecording(completion: @escaping (Result<URL, Error>) -> Void) {
        if isRecording {
            photoCompletion = { result in completion(result) }
            movieOutput.stopRecording()
            return
        }
        AVCaptureDevice.requestAccess(for: .audio) { _ in }
        Task { @MainActor in
            do {
                let url = try VaultStore.shared.reserveCameraTempFile(extension: "mov")
                recordingURL = url
                photoCompletion = { result in completion(result) }
                movieOutput.startRecording(to: url, recordingDelegate: self)
                isRecording = true
            } catch {
                completion(.failure(error))
            }
        }
    }

    private func startSession() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            session.beginConfiguration()
            session.sessionPreset = .high
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device) else {
                session.commitConfiguration()
                return
            }
            if session.canAddInput(input) {
                session.addInput(input)
                currentInput = input
            }
            if session.canAddOutput(photoOutput) { session.addOutput(photoOutput) }
            if session.canAddOutput(movieOutput) { session.addOutput(movieOutput) }
            session.commitConfiguration()
            session.startRunning()
            Task { @MainActor in self.isRunning = true }
        }
    }
}

extension CameraSessionController: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        Task { @MainActor in
            if let error {
                photoCompletion?(.failure(error))
                photoCompletion = nil
                return
            }
            guard let data = photo.fileDataRepresentation() else {
                photoCompletion?(.failure(CameraError.noData))
                photoCompletion = nil
                return
            }
            do {
                let url = try VaultStore.shared.reserveCameraTempFile(extension: "jpg")
                try data.write(to: url, options: .atomic)
                photoCompletion?(.success(url))
            } catch {
                photoCompletion?(.failure(error))
            }
            photoCompletion = nil
        }
    }
}

extension CameraSessionController: AVCaptureFileOutputRecordingDelegate {
    nonisolated func fileOutput(
        _ output: AVCaptureFileOutput,
        didFinishRecordingTo outputFileURL: URL,
        from connections: [AVCaptureConnection],
        error: Error?
    ) {
        Task { @MainActor in
            isRecording = false
            if let error {
                photoCompletion?(.failure(error))
            } else {
                photoCompletion?(.success(outputFileURL))
            }
            photoCompletion = nil
        }
    }
}

enum CameraError: LocalizedError {
    case noData
    var errorDescription: String? { L10n.tr("camera_capture_failed") }
}
