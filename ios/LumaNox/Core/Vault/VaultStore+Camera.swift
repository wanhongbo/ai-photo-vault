import Foundation

extension VaultStore {
    /// Plaintext temp path for AVFoundation capture (Android [reserveCameraTarget]).
    func reserveCameraTempFile(extension ext: String, albumName: String = vaultDefaultAlbumName) throws -> URL {
        let safeExt = ext.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ".", with: "")
        let album = sanitizeAlbumName(albumName)
        let name = "cam_\(album)_\(Int64(Date().timeIntervalSince1970 * 1000)).\(safeExt.isEmpty ? "bin" : safeExt)"
        return try PlaintextTempFileManager.shared.makeFileURL(
            for: .camera,
            preferredBaseName: URL(fileURLWithPath: name).deletingPathExtension().lastPathComponent,
            fileExtension: safeExt
        )
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
            let hash = try sha256Hex(of: tempURL)
            try cipher.encryptFileFromChunks(to: dest) { sink in
                let handle = try FileHandle(forReadingFrom: tempURL)
                defer { try? handle.close() }
                while let chunk = try handle.read(upToCount: 64 * 1024), !chunk.isEmpty {
                    try sink(chunk)
                }
            }
            try metadataStore.recordImportedMedia(
                encryptedURL: dest,
                albumName: safeAlbum,
                plainURL: tempURL,
                plainSha256Hex: hash,
                source: .camera,
                originalFileName: tempURL.lastPathComponent
            )
            PlaintextTempFileManager.shared.removeItem(tempURL)
            invalidateCache()
            await loadSnapshot()
            return dest.path
        } catch {
            PlaintextTempFileManager.shared.removeItem(tempURL)
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
