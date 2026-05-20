import Foundation

@MainActor
final class PrivateCameraViewModel: ObservableObject {
    @Published var message: String?
    @Published var isSaving = false
    let controller = CameraSessionController()

    func onAppear() {
        controller.configure()
    }

    func onDisappear() {
        controller.stop()
    }

    func capturePhoto() {
        guard !isSaving else { return }
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

    func toggleVideo() {
        guard !isSaving else { return }
        controller.toggleRecording { [weak self] result in
            Task { @MainActor in
                switch result {
                case .success(let url):
                    await self?.saveToVault(tempURL: url, successKey: "camera_video_saved")
                case .failure:
                    self?.message = L10n.tr("camera_video_import_failed")
                }
            }
        }
    }

    func flipCamera() { controller.flipCamera() }
    func toggleFlash() { controller.cycleFlash() }

    private func saveToVault(tempURL: URL, successKey: String) async {
        isSaving = true
        defer { isSaving = false }
        if let _ = await VaultStore.shared.finalizeCameraCapture(tempURL: tempURL) {
            message = L10n.tr(successKey)
        } else {
            message = L10n.tr("camera_video_import_failed")
        }
    }
}
