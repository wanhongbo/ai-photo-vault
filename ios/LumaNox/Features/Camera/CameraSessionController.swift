import AVFoundation
import UIKit

enum CameraCaptureMode: String, CaseIterable {
    case photo
    case video
}

enum CameraFlashMode: String, CaseIterable {
    case off
    case auto
    case on
}

enum CameraTimerOption: Int, CaseIterable {
    case off = 0
    case three = 3
    case ten = 10
}

enum CameraVideoResolution: String, CaseIterable {
    case fhd
    case uhd4K
}

enum CameraVideoFPS: Int, CaseIterable {
    case thirty = 30
    case sixty = 60
}

struct CameraCapabilities: Equatable {
    var hasFlash = false
    var hasTorch = false
    var minZoom: CGFloat = 1
    var maxZoom: CGFloat = 1
    var minExposureBias: Float = 0
    var maxExposureBias: Float = 0
    var supports4K = false
    var supports60FPS = false

    static let unavailable = CameraCapabilities()
}

@MainActor
final class CameraSessionController: NSObject, ObservableObject {
    @Published private(set) var isRunning = false
    @Published private(set) var permissionDenied = false
    @Published private(set) var microphoneDenied = false
    @Published private(set) var isRecording = false
    @Published private(set) var capabilities = CameraCapabilities.unavailable
    @Published private(set) var currentPosition: AVCaptureDevice.Position = .back
    @Published var flashMode: CameraFlashMode = .off
    @Published var videoResolution: CameraVideoResolution = .fhd
    @Published var videoFPS: CameraVideoFPS = .thirty
    @Published var zoomFactor: CGFloat = 1
    @Published var exposureBias: Float = 0

    let session = AVCaptureSession()

    private let sessionQueue = DispatchQueue(label: "com.xpx.vault.camera.session")
    private var photoOutput = AVCapturePhotoOutput()
    private var movieOutput = AVCaptureMovieFileOutput()
    private var currentInput: AVCaptureDeviceInput?
    private var currentAudioInput: AVCaptureDeviceInput?
    private var currentDevice: AVCaptureDevice?
    private var recordingURL: URL?
    private var captureCompletion: ((Result<URL, Error>) -> Void)?

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
        stopRecording()
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
        let next: AVCaptureDevice.Position = currentPosition == .back ? .front : .back
        reconfigure(position: next)
    }

    func setFlashMode(_ mode: CameraFlashMode) {
        if !capabilities.hasFlash && !capabilities.hasTorch {
            flashMode = .off
            return
        }
        flashMode = mode
        if isRecording {
            setTorchEnabled(mode == .on)
        }
    }

    func setVideoResolution(_ resolution: CameraVideoResolution) {
        guard resolution != .uhd4K || capabilities.supports4K else {
            videoResolution = .fhd
            return
        }
        videoResolution = resolution
        reconfigure(position: currentPosition)
    }

    func setVideoFPS(_ fps: CameraVideoFPS) {
        guard fps != .sixty || capabilities.supports60FPS else {
            videoFPS = .thirty
            return
        }
        videoFPS = fps
        applyVideoFrameRate()
    }

    func setZoomFactor(_ value: CGFloat) {
        let target = min(max(value, capabilities.minZoom), capabilities.maxZoom)
        zoomFactor = target
        sessionQueue.async { [weak self] in
            guard let self, let device = currentDevice else { return }
            do {
                try device.lockForConfiguration()
                device.videoZoomFactor = min(max(target, device.minAvailableVideoZoomFactor), device.maxAvailableVideoZoomFactor)
                device.unlockForConfiguration()
            } catch {}
        }
    }

    func setExposureBias(_ value: Float) {
        let target = min(max(value, capabilities.minExposureBias), capabilities.maxExposureBias)
        exposureBias = target
        sessionQueue.async { [weak self] in
            guard let self, let device = currentDevice, device.isExposureModeSupported(.continuousAutoExposure) else { return }
            do {
                try device.lockForConfiguration()
                device.setExposureTargetBias(target)
                device.unlockForConfiguration()
            } catch {}
        }
    }

    func focusAndExpose(at normalizedPoint: CGPoint) {
        sessionQueue.async { [weak self] in
            guard let device = self?.currentDevice else { return }
            let point = CGPoint(
                x: min(max(normalizedPoint.x, 0), 1),
                y: min(max(normalizedPoint.y, 0), 1)
            )
            do {
                try device.lockForConfiguration()
                if device.isFocusPointOfInterestSupported {
                    device.focusPointOfInterest = point
                    if device.isFocusModeSupported(.autoFocus) {
                        device.focusMode = .autoFocus
                    }
                }
                if device.isExposurePointOfInterestSupported {
                    device.exposurePointOfInterest = point
                    if device.isExposureModeSupported(.continuousAutoExposure) {
                        device.exposureMode = .continuousAutoExposure
                    }
                }
                device.unlockForConfiguration()
            } catch {}
        }
    }

    func capturePhoto(completion: @escaping (Result<URL, Error>) -> Void) {
        captureCompletion = completion
        let settings = AVCapturePhotoSettings()
        let avFlashMode = avCaptureFlashMode(for: flashMode)
        if photoOutput.supportedFlashModes.contains(avFlashMode) {
            settings.flashMode = avFlashMode
        }
        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    func startRecording(
        onStarted: @escaping () -> Void = {},
        completion: @escaping (Result<URL, Error>) -> Void
    ) {
        guard !isRecording else { return }
        prepareAudioInputForRecording { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                do {
                    let url = try VaultStore.shared.reserveCameraTempFile(extension: "mov")
                    self.recordingURL = url
                    self.captureCompletion = completion
                    self.setTorchEnabled(self.flashMode == .on)
                    self.movieOutput.startRecording(to: url, recordingDelegate: self)
                    self.isRecording = true
                    onStarted()
                } catch {
                    completion(.failure(error))
                }
            }
        }
    }

    func stopRecording() {
        guard isRecording else { return }
        movieOutput.stopRecording()
    }

    private func startSession() {
        reconfigure(position: currentPosition, startAfterConfigure: true)
    }

    private func reconfigure(position: AVCaptureDevice.Position, startAfterConfigure: Bool = false) {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            session.beginConfiguration()
            session.inputs.forEach { self.session.removeInput($0) }
            session.outputs.forEach { self.session.removeOutput($0) }

            let requestedDevice = Self.preferredDevice(position: position) ?? Self.preferredDevice(position: .back) ?? Self.preferredDevice(position: .front)
            guard let device = requestedDevice, let input = try? AVCaptureDeviceInput(device: device) else {
                session.commitConfiguration()
                Task { @MainActor in
                    self.capabilities = .unavailable
                    self.isRunning = false
                }
                return
            }

            if session.canAddInput(input) {
                session.addInput(input)
                currentInput = input
                currentDevice = device
            }
            if AVCaptureDevice.authorizationStatus(for: .audio) == .authorized {
                addAudioInputLocked()
            } else {
                currentAudioInput = nil
            }

            photoOutput = AVCapturePhotoOutput()
            movieOutput = AVCaptureMovieFileOutput()
            if session.canAddOutput(photoOutput) { session.addOutput(photoOutput) }
            if session.canAddOutput(movieOutput) { session.addOutput(movieOutput) }
            configureSessionPreset()
            applyVideoFrameRateLocked()
            session.commitConfiguration()

            if startAfterConfigure || !session.isRunning {
                session.startRunning()
            }

            let nextCapabilities = Self.capabilities(for: device)
            Task { @MainActor in
                self.currentPosition = device.position
                self.capabilities = nextCapabilities
                if !nextCapabilities.hasFlash && !nextCapabilities.hasTorch {
                    self.flashMode = .off
                }
                if self.videoResolution == .uhd4K && !nextCapabilities.supports4K {
                    self.videoResolution = .fhd
                }
                if self.videoFPS == .sixty && !nextCapabilities.supports60FPS {
                    self.videoFPS = .thirty
                }
                self.zoomFactor = min(max(self.zoomFactor, nextCapabilities.minZoom), nextCapabilities.maxZoom)
                self.exposureBias = min(max(self.exposureBias, nextCapabilities.minExposureBias), nextCapabilities.maxExposureBias)
                self.isRunning = self.session.isRunning
            }
        }
    }

    private func configureSessionPreset() {
        let preset: AVCaptureSession.Preset = videoResolution == .uhd4K ? .hd4K3840x2160 : .high
        if session.canSetSessionPreset(preset) {
            session.sessionPreset = preset
        } else if session.canSetSessionPreset(.high) {
            session.sessionPreset = .high
        }
    }

    private func applyVideoFrameRate() {
        sessionQueue.async { [weak self] in
            self?.applyVideoFrameRateLocked()
        }
    }

    private func applyVideoFrameRateLocked() {
        guard let device = currentDevice else { return }
        let targetFPS = Double(videoFPS.rawValue)
        guard let format = Self.bestFormat(for: device, resolution: videoResolution, fps: targetFPS) else { return }
        do {
            try device.lockForConfiguration()
            device.activeFormat = format
            let duration = CMTime(value: 1, timescale: CMTimeScale(Int32(targetFPS)))
            device.activeVideoMinFrameDuration = duration
            device.activeVideoMaxFrameDuration = duration
            device.unlockForConfiguration()
        } catch {}
    }

    private func prepareAudioInputForRecording(completion: @escaping () -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .authorized:
            microphoneDenied = false
            addAudioInputIfPossible(completion: completion)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .audio) { [weak self] granted in
                Task { @MainActor in
                    self?.microphoneDenied = !granted
                }
                if granted {
                    self?.addAudioInputIfPossible(completion: completion)
                } else {
                    DispatchQueue.main.async(execute: completion)
                }
            }
        case .denied, .restricted:
            microphoneDenied = true
            DispatchQueue.main.async(execute: completion)
        @unknown default:
            microphoneDenied = true
            DispatchQueue.main.async(execute: completion)
        }
    }

    private func addAudioInputIfPossible(completion: @escaping () -> Void) {
        sessionQueue.async { [weak self] in
            guard let self else {
                DispatchQueue.main.async(execute: completion)
                return
            }
            if currentAudioInput != nil {
                DispatchQueue.main.async(execute: completion)
                return
            }
            session.beginConfiguration()
            addAudioInputLocked()
            session.commitConfiguration()
            DispatchQueue.main.async(execute: completion)
        }
    }

    private func addAudioInputLocked() {
        guard currentAudioInput == nil,
              let microphone = AVCaptureDevice.default(for: .audio),
              let audioInput = try? AVCaptureDeviceInput(device: microphone),
              session.canAddInput(audioInput)
        else { return }
        session.addInput(audioInput)
        currentAudioInput = audioInput
    }

    private func setTorchEnabled(_ enabled: Bool) {
        sessionQueue.async { [weak self] in
            guard let device = self?.currentDevice, device.hasTorch else { return }
            do {
                try device.lockForConfiguration()
                if enabled {
                    try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
                } else {
                    device.torchMode = .off
                }
                device.unlockForConfiguration()
            } catch {}
        }
    }

    private func avCaptureFlashMode(for mode: CameraFlashMode) -> AVCaptureDevice.FlashMode {
        switch mode {
        case .off: return .off
        case .auto: return .auto
        case .on: return .on
        }
    }

    private static func preferredDevice(position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        let types: [AVCaptureDevice.DeviceType] = [
            .builtInTripleCamera,
            .builtInDualWideCamera,
            .builtInDualCamera,
            .builtInWideAngleCamera
        ]
        for type in types {
            if let device = AVCaptureDevice.default(type, for: .video, position: position) {
                return device
            }
        }
        return nil
    }

    private static func capabilities(for device: AVCaptureDevice) -> CameraCapabilities {
        let maxZoom = min(device.maxAvailableVideoZoomFactor, 10)
        return CameraCapabilities(
            hasFlash: device.hasFlash,
            hasTorch: device.hasTorch,
            minZoom: max(0.5, device.minAvailableVideoZoomFactor),
            maxZoom: max(1, maxZoom),
            minExposureBias: device.minExposureTargetBias,
            maxExposureBias: device.maxExposureTargetBias,
            supports4K: supportsResolution(device, minWidth: 3840, minHeight: 2160),
            supports60FPS: supportsFPS(device, fps: 60)
        )
    }

    private static func supportsResolution(_ device: AVCaptureDevice, minWidth: Int32, minHeight: Int32) -> Bool {
        device.formats.contains { format in
            let dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
            return dimensions.width >= minWidth && dimensions.height >= minHeight
        }
    }

    private static func supportsFPS(_ device: AVCaptureDevice, fps: Double) -> Bool {
        device.formats.contains { format in
            format.videoSupportedFrameRateRanges.contains { $0.maxFrameRate >= fps }
        }
    }

    private static func bestFormat(
        for device: AVCaptureDevice,
        resolution: CameraVideoResolution,
        fps: Double
    ) -> AVCaptureDevice.Format? {
        let minimumWidth: Int32 = resolution == .uhd4K ? 3840 : 1920
        return device.formats
            .filter { format in
                let dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
                let supportsResolution = dimensions.width >= minimumWidth
                let supportsFPS = format.videoSupportedFrameRateRanges.contains { $0.maxFrameRate >= fps }
                return supportsResolution && supportsFPS
            }
            .sorted { lhs, rhs in
                let l = CMVideoFormatDescriptionGetDimensions(lhs.formatDescription)
                let r = CMVideoFormatDescriptionGetDimensions(rhs.formatDescription)
                return (l.width * l.height) < (r.width * r.height)
            }
            .first
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
                captureCompletion?(.failure(error))
                captureCompletion = nil
                return
            }
            guard let data = photo.fileDataRepresentation() else {
                captureCompletion?(.failure(CameraError.noData))
                captureCompletion = nil
                return
            }
            do {
                let url = try VaultStore.shared.reserveCameraTempFile(extension: "jpg")
                try data.write(to: url, options: .atomic)
                captureCompletion?(.success(url))
            } catch {
                captureCompletion?(.failure(error))
            }
            captureCompletion = nil
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
            setTorchEnabled(false)
            if let error {
                captureCompletion?(.failure(error))
            } else {
                captureCompletion?(.success(outputFileURL))
            }
            captureCompletion = nil
        }
    }
}

enum CameraError: LocalizedError {
    case noData
    var errorDescription: String? { L10n.tr("camera_capture_failed") }
}
