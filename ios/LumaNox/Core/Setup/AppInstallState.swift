import Foundation

enum AppInstallState {
    private static let installMarkerKey = "lumanox_install_marker_v1"

    static var isFreshContainerInstall: Bool {
        !UserDefaults.standard.bool(forKey: installMarkerKey)
    }

    static func markInstalled() {
        UserDefaults.standard.set(true, forKey: installMarkerKey)
    }
}
