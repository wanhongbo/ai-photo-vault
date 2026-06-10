import Combine
import Foundation
import Network

/// 持续监听系统网络路径；`NWPathMonitor.status == .satisfied` 仅表示有可用接口，不代表一定能连上 App Store。
@MainActor
final class NetworkReachability: ObservableObject {
    static let shared = NetworkReachability()

    @Published private(set) var isOnline = true

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.xpx.vault.network-reachability")

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = Self.isPathOnline(path)
            Task { @MainActor in
                self?.isOnline = online
            }
        }
        monitor.start(queue: queue)
        isOnline = Self.isPathOnline(monitor.currentPath)
    }

    static func isReachable() async -> Bool {
        await MainActor.run { shared.isOnline }
    }

    private nonisolated static func isPathOnline(_ path: NWPath) -> Bool {
        guard path.status == .satisfied else { return false }
        return path.usesInterfaceType(.wifi)
            || path.usesInterfaceType(.cellular)
            || path.usesInterfaceType(.wiredEthernet)
    }
}
