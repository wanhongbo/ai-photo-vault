import Foundation

struct MediaExportFailure: Identifiable, Hashable {
    var id: String { recordId }
    let recordId: String
    let fileName: String
    let reason: String
}

struct MediaExportBatchResult: Hashable {
    let outputDirectory: URL?
    let exportedFiles: [URL]
    let failures: [MediaExportFailure]
    let total: Int
    let cancelled: Bool

    var successCount: Int { exportedFiles.count }
    var failedCount: Int { failures.count }

    static let empty = MediaExportBatchResult(
        outputDirectory: nil,
        exportedFiles: [],
        failures: [],
        total: 0,
        cancelled: false
    )
}

enum MediaExportServiceError: Error {
    case cancelled
}

final class MediaExportService: @unchecked Sendable {
    static let shared = MediaExportService()

    private let fileManager = FileManager.default
    private let cipher = VaultCipher.shared
    private let tempManager = PlaintextTempFileManager.shared

    private init() {}

    func export(
        records: [VaultMediaRecord],
        progress: @escaping LongRunningTaskProgressHandler
    ) async -> MediaExportBatchResult {
        guard !records.isEmpty else { return .empty }

        let outputDirectory: URL
        do {
            outputDirectory = try tempManager.sessionDirectory(for: .export)
        } catch {
            return MediaExportBatchResult(
                outputDirectory: nil,
                exportedFiles: [],
                failures: records.map { failure(for: $0, error: error) },
                total: records.count,
                cancelled: false
            )
        }

        let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        var exported: [URL] = []
        var failures: [MediaExportFailure] = []
        let totalBytes = records.reduce(Int64(0)) { $0 + max(0, $1.encryptedSizeBytes) }
        var processedBytes: Int64 = 0

        await progress(LongRunningTaskProgress(
            phase: .exporting,
            current: 0,
            total: records.count,
            currentFileName: nil,
            bytesWritten: 0,
            totalBytes: totalBytes,
            cancellable: true
        ))

        for (index, record) in records.enumerated() {
            if Task.isCancelled {
                cleanup(outputDirectory)
                return MediaExportBatchResult(
                    outputDirectory: nil,
                    exportedFiles: [],
                    failures: failures,
                    total: records.count,
                    cancelled: true
                )
            }

            let displayName = exportDisplayName(for: record)
            await progress(LongRunningTaskProgress(
                phase: .exporting,
                current: index + 1,
                total: records.count,
                currentFileName: displayName,
                bytesWritten: processedBytes,
                totalBytes: totalBytes,
                cancellable: true
            ))

            do {
                let sourceURL = record.absoluteURL(documentsDirectory: documentsDirectory)
                let outputURL = try uniqueOutputURL(directory: outputDirectory, fileName: displayName)
                try decrypt(recordAt: sourceURL, to: outputURL)
                exported.append(outputURL)
            } catch {
                failures.append(failure(for: record, error: error))
            }
            processedBytes += max(0, record.encryptedSizeBytes)

            await progress(LongRunningTaskProgress(
                phase: .exporting,
                current: index + 1,
                total: records.count,
                currentFileName: displayName,
                bytesWritten: processedBytes,
                totalBytes: totalBytes,
                cancellable: true
            ))
        }

        if exported.isEmpty {
            cleanup(outputDirectory)
        }

        return MediaExportBatchResult(
            outputDirectory: exported.isEmpty ? nil : outputDirectory,
            exportedFiles: exported,
            failures: failures,
            total: records.count,
            cancelled: false
        )
    }

    func cleanup(_ directory: URL?) {
        tempManager.removeItem(directory)
    }

    private func decrypt(recordAt sourceURL: URL, to outputURL: URL) throws {
        try fileManager.createDirectory(
            at: outputURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        fileManager.createFile(atPath: outputURL.path, contents: nil)
        let handle = try FileHandle(forWritingTo: outputURL)
        do {
            try cipher.decryptStream(at: sourceURL) { chunk in
                try Task.checkCancellation()
                try handle.write(contentsOf: chunk)
            }
            try handle.close()
        } catch {
            try? handle.close()
            try? fileManager.removeItem(at: outputURL)
            throw error
        }
    }

    private func uniqueOutputURL(directory: URL, fileName: String) throws -> URL {
        let safeName = sanitizedFileName(fileName)
        let baseURL = directory.appendingPathComponent(safeName)
        if !fileManager.fileExists(atPath: baseURL.path) { return baseURL }

        let ext = baseURL.pathExtension
        let base = baseURL.deletingPathExtension().lastPathComponent
        for index in 2...999 {
            let candidateName = ext.isEmpty ? "\(base)-\(index)" : "\(base)-\(index).\(ext)"
            let candidate = directory.appendingPathComponent(candidateName)
            if !fileManager.fileExists(atPath: candidate.path) { return candidate }
        }

        throw CocoaError(.fileWriteFileExists)
    }

    private func exportDisplayName(for record: VaultMediaRecord) -> String {
        if let original = record.originalFileName,
           !original.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           !isGeneratedVaultName(original) {
            return sanitizedFileName(original)
        }

        let ext = URL(fileURLWithPath: record.fileName).pathExtension
        let tokenSource = record.originalSha256Hex ?? record.encryptedSha256Hex ?? record.id
        let token = String(tokenSource.suffix(8)).filter { $0.isLetter || $0.isNumber }
        let suffix = token.isEmpty ? UUID().uuidString.prefix(8) : Substring(token)
        return sanitizedFileName("LumaNox_\(suffix).\(ext.isEmpty ? "bin" : ext)")
    }

    private func isGeneratedVaultName(_ name: String) -> Bool {
        let base = URL(fileURLWithPath: name).deletingPathExtension().lastPathComponent
        return base.hasPrefix("asset_") || base.hasPrefix("camera_") || base.hasPrefix("tmp_")
    }

    private func sanitizedFileName(_ name: String) -> String {
        let url = URL(fileURLWithPath: name)
        let rawExt = url.pathExtension
        let ext = rawExt.filter { $0.isLetter || $0.isNumber }.lowercased()
        let rawBase = url.deletingPathExtension().lastPathComponent
        let safeBase = rawBase.map { char -> Character in
            if char.isLetter || char.isNumber || char == "_" || char == "-" {
                return char
            }
            return "_"
        }
        let cleanedBase = String(safeBase).trimmingCharacters(in: CharacterSet(charactersIn: "._-"))
        let base = cleanedBase.isEmpty ? "LumaNox_\(UUID().uuidString.prefix(8))" : cleanedBase
        return ext.isEmpty ? base : "\(base).\(ext)"
    }

    private func failure(for record: VaultMediaRecord, error: Error) -> MediaExportFailure {
        MediaExportFailure(
            recordId: record.id,
            fileName: exportDisplayName(for: record),
            reason: error.localizedDescription
        )
    }
}
