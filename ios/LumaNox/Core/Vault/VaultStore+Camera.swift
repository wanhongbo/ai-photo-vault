import Foundation

extension VaultStore {
    private var cameraTmpDir: URL {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("camera_tmp", isDirectory: true)
    }

    /// Plaintext temp path for AVFoundation capture (Android [reserveCameraTarget]).
    func reserveCameraTempFile(extension ext: String, albumName: String = vaultDefaultAlbumName) throws -> URL {
        try fileManager.createDirectory(at: cameraTmpDir, withIntermediateDirectories: true)
        let safeExt = ext.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ".", with: "")
        let album = sanitizeAlbumName(albumName)
        let name = "cam_\(album)_\(Int64(Date().timeIntervalSince1970 * 1000)).\(safeExt.isEmpty ? "bin" : safeExt)"
        return cameraTmpDir.appendingPathComponent(name)
    }

    /// Encrypt camera temp file into vault (Android [finalizeCameraCapture]).
    func finalizeCameraCapture(tempURL: URL) async -> String? {
        guard fileManager.fileExists(atPath: tempURL.path) else {
            try? fileManager.removeItem(at: tempURL)
            return nil
        }
        let attrs = try? fileManager.attributesOfItem(atPath: tempURL.path)
        let size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
        guard size > 0 else {
            try? fileManager.removeItem(at: tempURL)
            return nil
        }

        let album = parseAlbumFromCameraTempName(tempURL.lastPathComponent) ?? vaultDefaultAlbumName
        do {
            let safeAlbum = (try? createAlbum(named: album)) ?? vaultDefaultAlbumName
            let albumDir = try rootDirectory().appendingPathComponent(safeAlbum, isDirectory: true)
            let ext = tempURL.pathExtension.isEmpty ? "bin" : tempURL.pathExtension
            let dest = albumDir.appendingPathComponent("camera_\(Int64(Date().timeIntervalSince1970 * 1000)).\(ext)")
            try cipher.encryptFile(at: tempURL, to: dest)
            try? fileManager.removeItem(at: tempURL)
            invalidateCache()
            await loadSnapshot()
            return dest.path
        } catch {
            try? fileManager.removeItem(at: tempURL)
            return nil
        }
    }

    private func parseAlbumFromCameraTempName(_ name: String) -> String? {
        // cam_{album}_{timestamp}.ext
        guard name.hasPrefix("cam_") else { return nil }
        let rest = name.dropFirst(4)
        guard let lastUnderscore = rest.lastIndex(of: "_") else { return nil }
        let albumPart = rest[..<lastUnderscore]
        return albumPart.isEmpty ? nil : String(albumPart)
    }
}
