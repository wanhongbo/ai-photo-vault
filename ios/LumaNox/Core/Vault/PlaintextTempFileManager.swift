import Foundation

enum PlaintextTempScene: String, CaseIterable {
    case camera
    case videoPlayback
    case videoThumbnail
    case share
    case export
    case importStaging
    case backup
    case restore

    var ttl: TimeInterval {
        switch self {
        case .camera, .videoPlayback, .videoThumbnail, .share, .importStaging:
            return 60 * 60
        case .export, .backup, .restore:
            return 24 * 60 * 60
        }
    }

    var baseDirectory: URL {
        let fm = FileManager.default
        switch self {
        case .videoPlayback, .videoThumbnail:
            return fm.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("LumaNox/plaintext", isDirectory: true)
        case .camera, .share, .export, .importStaging, .backup, .restore:
            return fm.temporaryDirectory
                .appendingPathComponent("LumaNox/plaintext", isDirectory: true)
        }
    }
}

final class PlaintextTempFileManager: @unchecked Sendable {
    static let shared = PlaintextTempFileManager()

    private let fileManager = FileManager.default

    private init() {}

    func sessionDirectory(for scene: PlaintextTempScene) throws -> URL {
        let directory = scene.baseDirectory
            .appendingPathComponent(scene.rawValue, isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try createProtectedDirectory(directory)
        return directory
    }

    func makeFileURL(
        for scene: PlaintextTempScene,
        preferredBaseName: String? = nil,
        fileExtension: String
    ) throws -> URL {
        let directory = try sessionDirectory(for: scene)
        let ext = sanitizedExtension(fileExtension)
        let base = sanitizedBaseName(preferredBaseName)
        return directory.appendingPathComponent("\(base).\(ext)")
    }

    func decryptVaultFileToTemporary(
        sourceURL: URL,
        scene: PlaintextTempScene,
        preferredName: String? = nil
    ) throws -> URL {
        let directory = try sessionDirectory(for: scene)
        let name = sanitizedFileName(
            preferredName ?? sourceURL.lastPathComponent,
            fallbackExtension: sourceURL.pathExtension
        )
        return try VaultCipher.shared.decryptToTempFile(
            sourceURL: sourceURL,
            cacheDirectory: directory,
            fileName: name
        )
    }

    func copyFileToTemporary(
        sourceURL: URL,
        scene: PlaintextTempScene,
        preferredName: String? = nil
    ) throws -> URL {
        let directory = try sessionDirectory(for: scene)
        let name = sanitizedFileName(
            preferredName ?? sourceURL.lastPathComponent,
            fallbackExtension: sourceURL.pathExtension
        )
        let target = directory.appendingPathComponent(name)
        if fileManager.fileExists(atPath: target.path) {
            try fileManager.removeItem(at: target)
        }
        try fileManager.copyItem(at: sourceURL, to: target)
        return target
    }

    func removeItem(_ url: URL?) {
        guard let url else { return }
        try? fileManager.removeItem(at: url)
        removeEmptyParents(startingAt: url.deletingLastPathComponent())
    }

    func cleanupExpired(now: Date = Date()) {
        for scene in PlaintextTempScene.allCases {
            cleanupExpired(in: scene, now: now)
        }
    }

    func cleanupAll(for scene: PlaintextTempScene) {
        let sceneRoot = scene.baseDirectory.appendingPathComponent(scene.rawValue, isDirectory: true)
        try? fileManager.removeItem(at: sceneRoot)
    }

    private func cleanupExpired(in scene: PlaintextTempScene, now: Date) {
        let sceneRoot = scene.baseDirectory.appendingPathComponent(scene.rawValue, isDirectory: true)
        guard let entries = try? fileManager.contentsOfDirectory(
            at: sceneRoot,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else { return }

        for entry in entries {
            let date = (try? entry.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if now.timeIntervalSince(date) > scene.ttl {
                try? fileManager.removeItem(at: entry)
            }
        }
    }

    private func createProtectedDirectory(_ url: URL) throws {
        try fileManager.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableURL = url
        try? mutableURL.setResourceValues(values)
        try? fileManager.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
    }

    private func sanitizedFileName(_ name: String, fallbackExtension: String) -> String {
        let ext = sanitizedExtension(URL(fileURLWithPath: name).pathExtension.isEmpty ? fallbackExtension : URL(fileURLWithPath: name).pathExtension)
        let base = sanitizedBaseName(URL(fileURLWithPath: name).deletingPathExtension().lastPathComponent)
        return "\(base).\(ext)"
    }

    private func sanitizedBaseName(_ name: String?) -> String {
        let raw = (name?.isEmpty == false ? name : UUID().uuidString) ?? UUID().uuidString
        let safe = raw.map { char -> Character in
            if char.isLetter || char.isNumber || char == "_" || char == "-" {
                return char
            }
            return "_"
        }
        let cleaned = String(safe).trimmingCharacters(in: CharacterSet(charactersIn: "._-"))
        return cleaned.isEmpty ? UUID().uuidString : cleaned
    }

    private func sanitizedExtension(_ ext: String) -> String {
        let cleaned = ext
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ".", with: "")
            .filter { $0.isLetter || $0.isNumber }
            .lowercased()
        return cleaned.isEmpty ? "bin" : cleaned
    }

    private func removeEmptyParents(startingAt directory: URL) {
        var current = directory
        let roots = PlaintextTempScene.allCases.map {
            $0.baseDirectory.appendingPathComponent($0.rawValue, isDirectory: true).path
        }

        while roots.contains(where: { current.path.hasPrefix($0) }) {
            guard (try? fileManager.contentsOfDirectory(atPath: current.path).isEmpty) == true else { return }
            try? fileManager.removeItem(at: current)
            let parent = current.deletingLastPathComponent()
            if parent.path == current.path { return }
            current = parent
        }
    }
}
