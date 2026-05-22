import Combine
import Foundation
import UIKit

struct LastCameraCapture: Equatable {
    let path: String
    let isVideo: Bool
}

@MainActor
final class PrivateCameraViewModel: ObservableObject {
    @Published var message: String?
    @Published var transientStatus: String?
    @Published var isSaving = false
    @Published var isCapturing = false
    @Published var shutterFeedback = false
    @Published var captureMode: CameraCaptureMode = .photo
    @Published var timerOption: CameraTimerOption = .off
    @Published var showGrid = false
    @Published var showSettingsPanel = false
    @Published var countdownRemaining: Int?
    @Published var focusMarker: CGPoint?
    @Published var recordingDurationText = "00:00"
    @Published var lastCapture: LastCameraCapture?

    let controller = CameraSessionController()

    private var controllerCancellable: AnyCancellable?
    private var countdownTask: Task<Void, Never>?
    private var focusClearTask: Task<Void, Never>?
    private var recordingTimerTask: Task<Void, Never>?
    private var shutterFeedbackTask: Task<Void, Never>?
    private var statusClearTask: Task<Void, Never>?

    var isBusy: Bool {
        isSaving || isCapturing || countdownRemaining != nil
    }

    var statusText: String? {
        if let transientStatus { return transientStatus }
        if isSaving { return L10n.tr("camera_saving_to_vault") }
        if isCapturing { return captureMode == .video ? L10n.tr("camera_preparing_video") : L10n.tr("camera_capturing_photo") }
        if let message { return message }
        if captureMode == .video && !controller.isRecording { return L10n.tr("camera_video_mode_hint") }
        return nil
    }

    init() {
        controllerCancellable = controller.objectWillChange.sink { [weak self] _ in
            self?.objectWillChange.send()
        }
    }

    func onAppear() {
        controller.configure()
    }

    func startForPresentation() {
        controller.configure()
    }

    func prepareForFastStart() {
        controller.prepareForFastStart()
    }

    func onDisappear() {
        countdownTask?.cancel()
        focusClearTask?.cancel()
        recordingTimerTask?.cancel()
        shutterFeedbackTask?.cancel()
        statusClearTask?.cancel()
        controller.stop()
    }

    func triggerShutter() {
        guard !isSaving else { return }
        flashShutterFeedback()
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
            isCapturing = true
            controller.capturePhoto { [weak self] result in
                Task { @MainActor in
                    self?.isCapturing = false
                    switch result {
                    case .success(let url):
                        await self?.saveToVault(tempURL: url, isVideo: false, successKey: "camera_photo_saved")
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
            isCapturing = true
            controller.startRecording(
                onStarted: { [weak self] in
                    guard let self else { return }
                    isCapturing = false
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
                            await self?.saveToVault(tempURL: url, isVideo: true, successKey: "camera_video_saved")
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

    func setCaptureMode(_ mode: CameraCaptureMode) {
        guard captureMode != mode else { return }
        captureMode = mode
        if mode == .video {
            controller.prepareForVideoMode()
        }
        showTransientStatus(mode == .video ? L10n.tr("camera_video_mode_hint") : L10n.tr("camera_photo_mode_hint"))
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
        UISelectionFeedbackGenerator().selectionChanged()
        showTransientStatus(L10n.tr("camera_zoom_status", Self.formatZoom(value)))
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

    private func saveToVault(tempURL: URL, isVideo: Bool, successKey: String) async {
        isSaving = true
        defer { isSaving = false }
        if let path = await VaultStore.shared.finalizeCameraCapture(tempURL: tempURL) {
            lastCapture = LastCameraCapture(path: path, isVideo: isVideo)
            message = L10n.tr(successKey)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } else {
            message = L10n.tr("camera_video_import_failed")
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        }
    }

    private func flashShutterFeedback() {
        shutterFeedbackTask?.cancel()
        shutterFeedback = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        shutterFeedbackTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 180_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.shutterFeedback = false
            }
        }
    }

    private func showTransientStatus(_ text: String) {
        transientStatus = text
        statusClearTask?.cancel()
        statusClearTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.transientStatus = nil
            }
        }
    }

    private static func formatDuration(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainder = seconds % 60
        return String(format: "%02d:%02d", minutes, remainder)
    }

    private static func formatZoom(_ value: CGFloat) -> String {
        if abs(value - 1) < 0.05 { return "1x" }
        if value < 1 { return String(format: "%.1fx", value) }
        return String(format: "%.0fx", value)
    }
}
