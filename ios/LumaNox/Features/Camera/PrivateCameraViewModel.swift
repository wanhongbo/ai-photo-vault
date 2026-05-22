import Combine
import Foundation

@MainActor
final class PrivateCameraViewModel: ObservableObject {
    @Published var message: String?
    @Published var isSaving = false
    @Published var captureMode: CameraCaptureMode = .photo
    @Published var timerOption: CameraTimerOption = .off
    @Published var showGrid = false
    @Published var showSettingsPanel = false
    @Published var countdownRemaining: Int?
    @Published var focusMarker: CGPoint?
    @Published var recordingDurationText = "00:00"

    let controller = CameraSessionController()

    private var controllerCancellable: AnyCancellable?
    private var countdownTask: Task<Void, Never>?
    private var focusClearTask: Task<Void, Never>?
    private var recordingTimerTask: Task<Void, Never>?

    init() {
        controllerCancellable = controller.objectWillChange.sink { [weak self] _ in
            self?.objectWillChange.send()
        }
    }

    func onAppear() {
        controller.configure()
    }

    func onDisappear() {
        countdownTask?.cancel()
        focusClearTask?.cancel()
        recordingTimerTask?.cancel()
        controller.stop()
    }

    func triggerShutter() {
        guard !isSaving else { return }
        switch captureMode {
        case .photo:
            capturePhoto()
        case .video:
            if controller.isRecording {
                stopVideo()
            } else {
                startVideo()
            }
        }
    }

    func capturePhoto() {
        guard !isSaving else { return }
        countdownTask?.cancel()
        countdownTask = Task { [weak self] in
            guard let self else { return }
            guard await runCountdownIfNeeded() else { return }
            controller.capturePhoto { [weak self] result in
                Task { @MainActor in
                    switch result {
                    case .success(let url):
                        await self?.saveToVault(tempURL: url, successKey: "camera_photo_saved")
                    case .failure:
                        self?.message = L10n.tr("camera_capture_failed")
                    }
                }
            }
        }
    }

    func startVideo() {
        guard !isSaving, !controller.isRecording else { return }
        countdownTask?.cancel()
        countdownTask = Task { [weak self] in
            guard let self else { return }
            guard await runCountdownIfNeeded() else { return }
            controller.startRecording(
                onStarted: { [weak self] in
                    guard let self else { return }
                    if controller.microphoneDenied {
                        message = L10n.tr("camera_microphone_denied")
                    }
                    startRecordingTimer()
                },
                completion: { [weak self] result in
                    Task { @MainActor in
                        self?.stopRecordingTimer()
                        switch result {
                        case .success(let url):
                            await self?.saveToVault(tempURL: url, successKey: "camera_video_saved")
                        case .failure:
                            self?.message = L10n.tr("camera_video_import_failed")
                        }
                    }
                }
            )
        }
    }

    func stopVideo() {
        controller.stopRecording()
    }

    func flipCamera() {
        controller.flipCamera()
    }

    func setFlashMode(_ mode: CameraFlashMode) {
        controller.setFlashMode(mode)
    }

    func setTimerOption(_ option: CameraTimerOption) {
        timerOption = option
    }

    func setVideoResolution(_ resolution: CameraVideoResolution) {
        controller.setVideoResolution(resolution)
    }

    func setVideoFPS(_ fps: CameraVideoFPS) {
        controller.setVideoFPS(fps)
    }

    func setZoom(_ value: CGFloat) {
        controller.setZoomFactor(value)
    }

    func setExposure(_ value: Float) {
        controller.setExposureBias(value)
    }

    func focus(at normalizedPoint: CGPoint) {
        focusMarker = normalizedPoint
        controller.focusAndExpose(at: normalizedPoint)
        focusClearTask?.cancel()
        focusClearTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 750_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.focusMarker = nil
            }
        }
    }

    private func runCountdownIfNeeded() async -> Bool {
        guard timerOption.rawValue > 0 else { return true }
        for second in stride(from: timerOption.rawValue, through: 1, by: -1) {
            countdownRemaining = second
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            if Task.isCancelled {
                countdownRemaining = nil
                return false
            }
        }
        countdownRemaining = nil
        return true
    }

    private func startRecordingTimer() {
        recordingTimerTask?.cancel()
        recordingDurationText = "00:00"
        recordingTimerTask = Task { [weak self] in
            var elapsed = 0
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                elapsed += 1
                await MainActor.run {
                    self?.recordingDurationText = Self.formatDuration(elapsed)
                }
            }
        }
    }

    private func stopRecordingTimer() {
        recordingTimerTask?.cancel()
        recordingTimerTask = nil
    }

    private func saveToVault(tempURL: URL, successKey: String) async {
        isSaving = true
        defer { isSaving = false }
        if let _ = await VaultStore.shared.finalizeCameraCapture(tempURL: tempURL) {
            message = L10n.tr(successKey)
        } else {
            message = L10n.tr("camera_video_import_failed")
        }
    }

    private static func formatDuration(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainder = seconds % 60
        return String(format: "%02d:%02d", minutes, remainder)
    }
}
