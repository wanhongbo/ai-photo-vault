import Combine
import Foundation
import UIKit

struct LastCameraCapture: Equatable {
    let path: String
    let isVideo: Bool
}

@MainActor
final class PrivateCameraViewModel: ObservableObject {
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

    var isBusy: Bool {
        isSaving || isCapturing || countdownRemaining != nil
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
                        await self?.saveToVault(tempURL: url, isVideo: false)
                    case .failure:
                        UINotificationFeedbackGenerator().notificationOccurred(.error)
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
                    startRecordingTimer()
                },
                completion: { [weak self] result in
                    Task { @MainActor in
                        self?.stopRecordingTimer()
                        switch result {
                        case .success(let url):
                            await self?.saveToVault(tempURL: url, isVideo: true)
                        case .failure:
                            UINotificationFeedbackGenerator().notificationOccurred(.error)
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

    private func saveToVault(tempURL: URL, isVideo: Bool) async {
        isSaving = true
        defer { isSaving = false }
        if let path = await VaultStore.shared.finalizeCameraCapture(tempURL: tempURL) {
            lastCapture = LastCameraCapture(path: path, isVideo: isVideo)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } else {
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

    private static func formatDuration(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainder = seconds % 60
        return String(format: "%02d:%02d", minutes, remainder)
    }
}
