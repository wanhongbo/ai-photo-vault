import Foundation

private struct CameraFinalizeWorkResult {
    let encryptedPath: String
    let plainSha256Hex: String
    let encryptedSha256Hex: String?
    let metadataDetails: MediaMetadataDetails
}

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
        let safeAlbum = (try? createAlbum(named: album)) ?? vaultDefaultAlbumName
        let albumDir: URL
        do {
            albumDir = try rootDirectory().appendingPathComponent(safeAlbum, isDirectory: true)
        } catch {
            PlaintextTempFileManager.shared.removeItem(tempURL)
            return nil
        }
        let ext = tempURL.pathExtension.isEmpty ? "bin" : tempURL.pathExtension
        let dest = albumDir.appendingPathComponent("camera_\(Int64(Date().timeIntervalSince1970 * 1000)).\(ext)")

        guard let work = await Task.detached(priority: .userInitiated, operation: {
            Self.finalizeCameraCaptureOffMain(tempURL: tempURL, dest: dest)
        }).value else {
            PlaintextTempFileManager.shared.removeItem(tempURL)
            try? self.fileManager.removeItem(at: dest)
            return nil
        }

        do {
            try metadataStore.recordImportedMedia(
                encryptedURL: dest,
                albumName: safeAlbum,
                plainURL: tempURL,
                plainSha256Hex: work.plainSha256Hex,
                source: .camera,
                originalFileName: tempURL.lastPathComponent,
                mediaDetails: work.metadataDetails,
                encryptedSha256Hex: work.encryptedSha256Hex
            )
            PlaintextTempFileManager.shared.removeItem(tempURL)
            refreshSnapshotFromMetadata()
            return work.encryptedPath
        } catch {
            PlaintextTempFileManager.shared.removeItem(tempURL)
            try? fileManager.removeItem(at: dest)
            return nil
        }
    }

    nonisolated private static func finalizeCameraCaptureOffMain(tempURL: URL, dest: URL) -> CameraFinalizeWorkResult? {
        do {
            let plainHash = try streamSHA256Hex(of: tempURL)
            try VaultCipher.shared.encryptFileFromChunks(to: dest) { sink in
                let handle = try FileHandle(forReadingFrom: tempURL)
                defer { try? handle.close() }
                while let chunk = try handle.read(upToCount: 64 * 1024), !chunk.isEmpty {
                    try sink(chunk)
                }
            }
            let encryptedHash = try? streamSHA256Hex(of: dest)
            let details = MediaMetadataExtractor.extract(from: tempURL)
            return CameraFinalizeWorkResult(
                encryptedPath: dest.path,
                plainSha256Hex: plainHash,
                encryptedSha256Hex: encryptedHash,
                metadataDetails: details
            )
        } catch {
            try? FileManager.default.removeItem(at: dest)
            return nil
        }
    }

    nonisolated private static func streamSHA256Hex(of url: URL) throws -> String {
        try VaultFileHasher.sha256Hex(of: url)
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
