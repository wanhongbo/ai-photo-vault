import CryptoKit
import Foundation

enum BackupKeyRefresh {
    /// Derive backup key from vault PIN and cache it (Android `launchRefreshBackupKey`).
    static func refresh(pin: String, force: Bool = false, triggerAutoBackup: Bool = false) {
        Task.detached(priority: .utility) {
            if !force, BackupSecretsStore.hasCached {
                if triggerAutoBackup {
                    await AutoBackupScheduler.runOnceNow(reason: .passwordChanged)
                }
                return
            }
            do {
                let km = BackupKeyManager()
                let params = km.getOrCreateKdfParams()
                let material = try km.deriveKey(password: pin, params: params)
                try BackupSecretsStore.cache(backupKey: material.key)
                if triggerAutoBackup {
                    await AutoBackupScheduler.runOnceNow(reason: .passwordChanged)
                }
            } catch {
                // Best-effort; backup UI will prompt unlock if missing.
            }
        }
    }
}

struct BackupExecutionResult {
    let success: Bool
    let backupId: String?
    let assetCount: Int
    let outputSizeBytes: Int64
    let message: String
    let cancelled: Bool

    static func success(backupId: String, assetCount: Int, bytes: Int64) -> BackupExecutionResult {
        BackupExecutionResult(success: true, backupId: backupId, assetCount: assetCount, outputSizeBytes: bytes, message: "", cancelled: false)
    }

    static func failure(_ message: String) -> BackupExecutionResult {
        BackupExecutionResult(success: false, backupId: nil, assetCount: 0, outputSizeBytes: 0, message: message, cancelled: false)
    }

    static func cancelled() -> BackupExecutionResult {
        BackupExecutionResult(success: false, backupId: nil, assetCount: 0, outputSizeBytes: 0, message: L10n.tr("long_task_cancelled"), cancelled: true)
    }
}

struct RestoreExecutionResult {
    let success: Bool
    let restored: Int
    let skipped: Int
    let failed: Int
    let backupId: String?
    let message: String
    let cancelled: Bool

    static func success(restored: Int, skipped: Int, failed: Int, backupId: String) -> RestoreExecutionResult {
        RestoreExecutionResult(success: true, restored: restored, skipped: skipped, failed: failed, backupId: backupId, message: "", cancelled: false)
    }

    static func failure(_ message: String) -> RestoreExecutionResult {
        RestoreExecutionResult(success: false, restored: 0, skipped: 0, failed: 0, backupId: nil, message: message, cancelled: false)
    }

    static func cancelled() -> RestoreExecutionResult {
        RestoreExecutionResult(success: false, restored: 0, skipped: 0, failed: 0, backupId: nil, message: L10n.tr("long_task_cancelled"), cancelled: true)
    }
}

/// Manual backup / restore — mirrors Android [LocalBackupMvpService] (MANUAL path).
final class LocalBackupService: @unchecked Sendable {
    static let shared = LocalBackupService()

    private let keyManager = BackupKeyManager()
    private let cipher = VaultCipher.shared
    private let lock = NSLock()
    private var isRunning = false

    func createAutoBackup(progress: LongRunningTaskProgressHandler? = nil) async -> BackupExecutionResult {
        lock.lock()
        guard !isRunning else {
            lock.unlock()
            return .failure(L10n.tr("backup_error_running"))
        }
        isRunning = true
        lock.unlock()
        defer {
            lock.lock()
            isRunning = false
            lock.unlock()
        }

        let work: Task<BackupExecutionResult, Never> = Task.detached(priority: .utility) { [self] in
            do {
                publishProgress(.initial(phase: .preparing), to: progress)
                try Task.checkCancellation()
                guard ExternalBackupLocation.isWritable() else {
                    throw BackupError.io(L10n.tr("backup_error_no_saf_dir"))
                }
                guard let backupKey = BackupSecretsStore.loadCached() else {
                    throw BackupError.noBackupKey
                }
                let params = keyManager.getOrCreateKdfParams()
                let fingerprint = keyManager.fingerprint(key: backupKey)
                let vaultRoot = try vaultRootURL()
                publishProgress(.initial(phase: .scanning), to: progress)
                let assets = try scanVaultAssets(vaultRoot: vaultRoot)
                guard !assets.isEmpty else { throw BackupError.vaultEmpty }

                let estimated = assets.reduce(Int64(0)) { $0 + $1.sizeBytes }
                guard hasEnoughSpace(needBytes: estimated * 2) else {
                    throw BackupError.io(L10n.tr("backup_error_no_space"))
                }

                let backupId = newBackupId()
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                let tmpDir = try tmpDirectory()
                let bodyFile = tmpDir.appendingPathComponent("auto_body_\(backupId).bin")
                let writingFile = tmpDir.appendingPathComponent("auto_\(backupId).writing")
                let bytes: Int64
                do {
                    bytes = try writeBodyAndAssemble(
                        vaultRoot: vaultRoot,
                        bodyFile: bodyFile,
                        writingFile: writingFile,
                        backupKey: backupKey,
                        headerBase: BackupPackageV1.HeaderBase(
                            backupId: backupId,
                            createdAtMs: now,
                            kind: .AUTO,
                            kdfAlgorithm: params.algorithm,
                            kdfSaltHex: params.saltHex,
                            kdfIterations: params.iterations,
                            kdfMemoryKb: params.memoryKb,
                            kdfParallelism: params.parallelism,
                            keyFingerprintHex: fingerprint
                        ),
                        assets: assets,
                        progress: progress
                    )

                    publishProgress(LongRunningTaskProgress(
                        phase: .assembling,
                        current: assets.count,
                        total: assets.count,
                        currentFileName: ExternalBackupLocation.autoFileName,
                        bytesWritten: estimated,
                        totalBytes: estimated,
                        cancellable: true
                    ), to: progress)
                    try Task.checkCancellation()
                    try ExternalBackupLocation.atomicReplaceAuto(tmpLocal: writingFile)
                    try? FileManager.default.removeItem(at: bodyFile)
                    try? FileManager.default.removeItem(at: writingFile)
                } catch {
                    try? FileManager.default.removeItem(at: bodyFile)
                    try? FileManager.default.removeItem(at: writingFile)
                    throw error
                }

                let externalPath = try ExternalBackupLocation.autoBackupFileURL()?.path
                BackupMeta.updateAuto(
                    BackupMeta.AutoMeta(
                        lastBackupId: backupId,
                        lastBackupAtMs: now,
                        keyFingerprintHex: fingerprint,
                        kdfParams: params,
                        externalPath: externalPath,
                        assetIndex: assets.map {
                            BackupMeta.AssetIndexEntry(
                                relativePath: $0.relativePath,
                                sha256Hex: $0.sha256Hex,
                                sizeBytes: $0.sizeBytes
                            )
                        }
                    )
                )

                await MainActor.run { QuotaManager.shared.recordSuccessfulBackup() }
                publishProgress(LongRunningTaskProgress(
                    phase: .completed,
                    current: assets.count,
                    total: assets.count,
                    currentFileName: ExternalBackupLocation.autoFileName,
                    bytesWritten: estimated,
                    totalBytes: estimated,
                    cancellable: false
                ), to: progress)
                return .success(backupId: backupId, assetCount: assets.count, bytes: bytes)
            } catch is CancellationError {
                publishProgress(.initial(phase: .cancelled, cancellable: false), to: progress)
                return .cancelled()
            } catch let e as BackupError {
                return .failure(e.localizedDescription ?? L10n.tr("backup_error_failed_fmt", ""))
            } catch {
                return .failure(L10n.tr("backup_error_failed_fmt", error.localizedDescription))
            }
        }
        return await withTaskCancellationHandler {
            await work.value
        } onCancel: {
            work.cancel()
        }
    }

    func createManualBackup(
        to outputURL: URL,
        progress: LongRunningTaskProgressHandler? = nil
    ) async -> BackupExecutionResult {
        lock.lock()
        guard !isRunning else {
            lock.unlock()
            return .failure(L10n.tr("backup_error_running"))
        }
        isRunning = true
        lock.unlock()
        defer {
            lock.lock()
            isRunning = false
            lock.unlock()
        }

        let work: Task<BackupExecutionResult, Never> = Task.detached(priority: .userInitiated) { [self] in
            do {
                publishProgress(.initial(phase: .preparing), to: progress)
                try Task.checkCancellation()
                guard let backupKey = BackupSecretsStore.loadCached() else { throw BackupError.noBackupKey }
                let params = keyManager.getOrCreateKdfParams()
                let fingerprint = keyManager.fingerprint(key: backupKey)

                let vaultRoot = try vaultRootURL()
                publishProgress(.initial(phase: .scanning), to: progress)
                let assets = try scanVaultAssets(vaultRoot: vaultRoot)
                guard !assets.isEmpty else { throw BackupError.vaultEmpty }
                let totalBytes = assets.reduce(Int64(0)) { $0 + $1.sizeBytes }

                let backupId = newBackupId()
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                let tmpDir = try tmpDirectory()
                let bodyFile = tmpDir.appendingPathComponent("manual_body_\(backupId).bin")
                let writingFile = tmpDir.appendingPathComponent("manual_\(backupId).writing")
                let bytes: Int64
                do {
                    bytes = try writeBodyAndAssemble(
                        vaultRoot: vaultRoot,
                        bodyFile: bodyFile,
                        writingFile: writingFile,
                        backupKey: backupKey,
                        headerBase: BackupPackageV1.HeaderBase(
                            backupId: backupId,
                            createdAtMs: now,
                            kind: .MANUAL,
                            kdfAlgorithm: params.algorithm,
                            kdfSaltHex: params.saltHex,
                            kdfIterations: params.iterations,
                            kdfMemoryKb: params.memoryKb,
                            kdfParallelism: params.parallelism,
                            keyFingerprintHex: fingerprint
                        ),
                        assets: assets,
                        progress: progress
                    )

                    publishProgress(LongRunningTaskProgress(
                        phase: .assembling,
                        current: assets.count,
                        total: assets.count,
                        currentFileName: outputURL.lastPathComponent,
                        bytesWritten: totalBytes,
                        totalBytes: totalBytes,
                        cancellable: true
                    ), to: progress)
                    try Task.checkCancellation()
                    if FileManager.default.fileExists(atPath: outputURL.path) {
                        try FileManager.default.removeItem(at: outputURL)
                    }
                    try FileManager.default.copyItem(at: writingFile, to: outputURL)

                    try? FileManager.default.removeItem(at: bodyFile)
                    try? FileManager.default.removeItem(at: writingFile)
                } catch {
                    try? FileManager.default.removeItem(at: bodyFile)
                    try? FileManager.default.removeItem(at: writingFile)
                    try? FileManager.default.removeItem(at: outputURL)
                    throw error
                }

                await MainActor.run { QuotaManager.shared.recordSuccessfulBackup() }
                publishProgress(LongRunningTaskProgress(
                    phase: .completed,
                    current: assets.count,
                    total: assets.count,
                    currentFileName: outputURL.lastPathComponent,
                    bytesWritten: totalBytes,
                    totalBytes: totalBytes,
                    cancellable: false
                ), to: progress)
                return .success(backupId: backupId, assetCount: assets.count, bytes: bytes)
            } catch is CancellationError {
                publishProgress(.initial(phase: .cancelled, cancellable: false), to: progress)
                try? FileManager.default.removeItem(at: outputURL)
                return .cancelled()
            } catch let e as BackupError {
                return .failure(e.localizedDescription ?? L10n.tr("backup_error_failed_fmt", ""))
            } catch {
                return .failure(L10n.tr("backup_error_failed_fmt", error.localizedDescription))
            }
        }
        return await withTaskCancellationHandler {
            await work.value
        } onCancel: {
            work.cancel()
        }
    }

    /// 从已授权目录的 `backup.dat` 恢复（首启 RestoreLogin）。
    func restoreFromAutoPackage(
        pin: String,
        progress: LongRunningTaskProgressHandler? = nil
    ) async -> RestoreExecutionResult {
        guard ExternalBackupLocation.findAutoBackup() else {
            return .failure(L10n.tr("restore_error_no_auto_backup"))
        }
        let tempURL: URL
        do {
            tempURL = try ExternalBackupLocation.copyAutoBackupToTemporary()
        } catch {
            return .failure(L10n.tr("restore_error_cannot_open_auto"))
        }
        defer { PlaintextTempFileManager.shared.removeItem(tempURL) }
        return await restore(from: tempURL, pin: pin, progress: progress)
    }

    func restore(
        from inputURL: URL,
        pin: String,
        progress: LongRunningTaskProgressHandler? = nil
    ) async -> RestoreExecutionResult {
        lock.lock()
        guard !isRunning else {
            lock.unlock()
            return .failure(L10n.tr("backup_error_running"))
        }
        isRunning = true
        lock.unlock()
        defer {
            lock.lock()
            isRunning = false
            lock.unlock()
        }

        let work: Task<RestoreExecutionResult, Never> = Task.detached(priority: .userInitiated) { [self] in
            do {
                publishProgress(.initial(phase: .verifying), to: progress)
                try Task.checkCancellation()
                let header = try readHeaderOnly(at: inputURL)
                let params = BackupKeyManager.KdfParams(
                    algorithm: header.kdfAlgorithm,
                    saltHex: header.kdfSaltHex,
                    iterations: header.kdfIterations,
                    memoryKb: header.kdfMemoryKb,
                    parallelism: header.kdfParallelism
                )
                let material = try keyManager.deriveKey(password: pin, params: params)
                if material.fingerprintHex != header.keyFingerprintHex {
                    throw BackupError.wrongPin
                }

                let vaultRoot = try vaultRootURL()
                let verifiedHeader = try BackupIntegrityVerifier.verifyPackage(
                    at: inputURL,
                    vaultRoot: vaultRoot
                )
                let totalBytes = verifiedHeader.assets.reduce(Int64(0)) { $0 + max(0, $1.sizeBytes) }
                publishProgress(LongRunningTaskProgress(
                    phase: .restoring,
                    current: 0,
                    total: verifiedHeader.assets.count,
                    currentFileName: nil,
                    bytesWritten: 0,
                    totalBytes: totalBytes,
                    cancellable: true
                ), to: progress)

                guard let bodyStream = InputStream(url: inputURL) else {
                    throw BackupError.io("cannot open backup")
                }
                bodyStream.open()
                defer { bodyStream.close() }
                _ = try bodyStream.readFully(count: BackupPackageV1.magic.count)
                _ = try bodyStream.readInt32BE()
                let headerLen = Int(try bodyStream.readInt32BE())
                _ = try bodyStream.readFully(count: headerLen)

                let reader = BackupPackageV1.newReader(input: bodyStream, backupKey: material.key)
                reader.attachHeader(verifiedHeader)

                var restored = 0
                var skipped = 0
                var failed = 0
                var processedBytes: Int64 = 0

                for (index, asset) in verifiedHeader.assets.enumerated() {
                    try Task.checkCancellation()
                    publishProgress(LongRunningTaskProgress(
                        phase: .restoring,
                        current: index + 1,
                        total: verifiedHeader.assets.count,
                        currentFileName: URL(fileURLWithPath: asset.relativePath).lastPathComponent,
                        bytesWritten: processedBytes,
                        totalBytes: totalBytes,
                        cancellable: true
                    ), to: progress)
                    do {
                        let target = try BackupIntegrityVerifier.resolvedAssetURL(
                            relativePath: asset.relativePath,
                            vaultRoot: vaultRoot
                        )
                        if FileManager.default.fileExists(atPath: target.path),
                           cipher.decryptedSha256Hex(at: target) == asset.sha256Hex {
                            skipped += 1
                            for _ in 0 ..< asset.frameCount {
                                try Task.checkCancellation()
                                _ = try reader.readNextChunk()
                            }
                            processedBytes += max(0, asset.sizeBytes)
                            continue
                        }
                        try FileManager.default.createDirectory(
                            at: target.deletingLastPathComponent(),
                            withIntermediateDirectories: true
                        )
                        var digest = SHA256()
                        try cipher.encryptFileFromChunks(to: target) { emit in
                            for _ in 0 ..< asset.frameCount {
                                try Task.checkCancellation()
                                guard let plain = try reader.readNextChunk() else {
                                    throw BackupError.io("unexpected EOF")
                                }
                                digest.update(data: plain)
                                processedBytes += Int64(plain.count)
                                publishProgress(LongRunningTaskProgress(
                                    phase: .restoring,
                                    current: index + 1,
                                    total: verifiedHeader.assets.count,
                                    currentFileName: URL(fileURLWithPath: asset.relativePath).lastPathComponent,
                                    bytesWritten: processedBytes,
                                    totalBytes: totalBytes,
                                    cancellable: true
                                ), to: progress)
                                try emit(plain)
                            }
                        }
                        let hash = digest.finalize().hexString
                        if hash != asset.sha256Hex {
                            try? FileManager.default.removeItem(at: target)
                            throw BackupError.io("checksum mismatch")
                        }
                        restored += 1
                    } catch is CancellationError {
                        if let target = try? BackupIntegrityVerifier.resolvedAssetURL(
                            relativePath: asset.relativePath,
                            vaultRoot: vaultRoot
                        ) {
                            try? FileManager.default.removeItem(at: target)
                        }
                        throw CancellationError()
                    } catch {
                        failed += 1
                        processedBytes += max(0, asset.sizeBytes)
                    }
                }

                await MainActor.run {
                    VaultStore.shared.invalidateCache()
                }
                publishProgress(LongRunningTaskProgress(
                    phase: .completed,
                    current: verifiedHeader.assets.count,
                    total: verifiedHeader.assets.count,
                    currentFileName: nil,
                    bytesWritten: totalBytes,
                    totalBytes: totalBytes,
                    cancellable: false
                ), to: progress)
                return .success(
                    restored: restored,
                    skipped: skipped,
                    failed: failed,
                    backupId: verifiedHeader.backupId
                )
            } catch is CancellationError {
                publishProgress(.initial(phase: .cancelled, cancellable: false), to: progress)
                return .cancelled()
            } catch BackupError.wrongPin {
                return .failure(L10n.tr("restore_error_wrong_pin"))
            } catch let e as BackupError {
                return .failure(e.localizedDescription ?? "")
            } catch {
                return .failure(error.localizedDescription)
            }
        }
        return await withTaskCancellationHandler {
            await work.value
        } onCancel: {
            work.cancel()
        }
    }

    // MARK: - Internal

    private struct VaultAsset {
        let relativePath: String
        let sizeBytes: Int64
        let sha256Hex: String
    }

    private func publishProgress(
        _ value: LongRunningTaskProgress,
        to handler: LongRunningTaskProgressHandler?
    ) {
        guard let handler else { return }
        Task { @MainActor in
            handler(value)
        }
    }

    private func vaultRootURL() throws -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let root = docs.appendingPathComponent("vault_albums", isDirectory: true)
        guard FileManager.default.fileExists(atPath: root.path) else { throw BackupError.io("no vault") }
        return root
    }

    private func tmpDirectory() throws -> URL {
        try PlaintextTempFileManager.shared.sessionDirectory(for: .backup)
    }

    private func newBackupId() -> String {
        "bkp_\(Int64(Date().timeIntervalSince1970 * 1000))_\(UUID().uuidString.prefix(8))"
    }

    private func hasEnoughSpace(needBytes: Int64) -> Bool {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        guard let cap = try? docs.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
            .volumeAvailableCapacityForImportantUsage else {
            return true
        }
        return cap >= needBytes
    }

    private func scanVaultAssets(vaultRoot: URL) throws -> [VaultAsset] {
        guard let enumerator = FileManager.default.enumerator(
            at: vaultRoot,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else { return [] }

        var assets: [VaultAsset] = []
        for case let file as URL in enumerator {
            try Task.checkCancellation()
            let name = file.lastPathComponent
            if name == ".vault_encrypted_v1" || name.contains(".enc_tmp_") { continue }
            let values = try file.resourceValues(forKeys: [.isRegularFileKey])
            guard values.isRegularFile == true else { continue }
            do {
                let asset = try buildAsset(vaultRoot: vaultRoot, file: file)
                assets.append(asset)
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                continue
            }
        }
        return assets
    }

    private func buildAsset(vaultRoot: URL, file: URL) throws -> VaultAsset {
        var digest = SHA256()
        var plainSize: Int64 = 0
        try cipher.decryptStream(at: file) { chunk in
            try Task.checkCancellation()
            digest.update(data: chunk)
            plainSize += Int64(chunk.count)
        }
        let rel = file.path.replacingOccurrences(of: vaultRoot.path + "/", with: "")
        return VaultAsset(
            relativePath: rel,
            sizeBytes: plainSize,
            sha256Hex: digest.finalize().hexString
        )
    }

    private func writeBodyAndAssemble(
        vaultRoot: URL,
        bodyFile: URL,
        writingFile: URL,
        backupKey: Data,
        headerBase: BackupPackageV1.HeaderBase,
        assets: [VaultAsset],
        progress: LongRunningTaskProgressHandler?
    ) throws -> Int64 {
        FileManager.default.createFile(atPath: bodyFile.path, contents: nil)
        let bodyStream = OutputStream(url: bodyFile, append: false)!
        bodyStream.open()
        defer { bodyStream.close() }

        let bodyWriter = BackupPackageV1.newBodyWriter(output: bodyStream, backupKey: backupKey)
        var chunkBuf = [UInt8](repeating: 0, count: BackupPackageV1.chunkMaxPlainBytes)
        let totalBytes = assets.reduce(Int64(0)) { $0 + $1.sizeBytes }
        var processedBytes: Int64 = 0

        for (index, asset) in assets.enumerated() {
            try Task.checkCancellation()
            publishProgress(LongRunningTaskProgress(
                phase: .backingUp,
                current: index + 1,
                total: assets.count,
                currentFileName: URL(fileURLWithPath: asset.relativePath).lastPathComponent,
                bytesWritten: processedBytes,
                totalBytes: totalBytes,
                cancellable: true
            ), to: progress)
            bodyWriter.beginAsset(
                relativePath: asset.relativePath,
                sha256Hex: asset.sha256Hex,
                sizeBytes: asset.sizeBytes
            )
            var chunkFill = 0
            let source = vaultRoot.appendingPathComponent(asset.relativePath)
            try cipher.decryptStream(at: source) { data in
                try Task.checkCancellation()
                var offset = 0
                while offset < data.count {
                    let take = min(BackupPackageV1.chunkMaxPlainBytes - chunkFill, data.count - offset)
                    for i in 0 ..< take {
                        chunkBuf[chunkFill + i] = data[data.index(data.startIndex, offsetBy: offset + i)]
                    }
                    chunkFill += take
                    offset += take
                    if chunkFill == BackupPackageV1.chunkMaxPlainBytes {
                        try bodyWriter.writeChunk(Data(chunkBuf[0 ..< chunkFill]))
                        chunkFill = 0
                    }
                }
                processedBytes += Int64(data.count)
                publishProgress(LongRunningTaskProgress(
                    phase: .backingUp,
                    current: index + 1,
                    total: assets.count,
                    currentFileName: URL(fileURLWithPath: asset.relativePath).lastPathComponent,
                    bytesWritten: processedBytes,
                    totalBytes: totalBytes,
                    cancellable: true
                ), to: progress)
            }
            if chunkFill > 0 {
                try bodyWriter.writeChunk(Data(chunkBuf[0 ..< chunkFill]))
            }
            _ = try bodyWriter.endAsset()
        }

        FileManager.default.createFile(atPath: writingFile.path, contents: nil)
        let outStream = OutputStream(url: writingFile, append: false)!
        outStream.open()
        defer { outStream.close() }
        return try BackupPackageV1.finalizePackage(
            bodyFile: bodyFile,
            bodyWriter: bodyWriter,
            headerBase: headerBase,
            finalOutput: outStream
        )
    }

    private func readHeaderOnly(at url: URL) throws -> BackupPackageV1.Header {
        guard let stream = InputStream(url: url) else { throw BackupError.io("open failed") }
        stream.open()
        defer { stream.close() }
        let magicRead = try stream.readFully(count: BackupPackageV1.magic.count)
        guard magicRead == BackupPackageV1.magic else { throw BackupError.badMagic }
        let ver = try stream.readInt32BE()
        guard ver == BackupPackageV1.version else { throw BackupError.unsupportedVersion(Int(ver)) }
        let headerLen = Int(try stream.readInt32BE())
        guard headerLen > 0, headerLen <= 32 * 1024 * 1024 else {
            throw BackupError.invalidHeaderLength(headerLen)
        }
        let headerBytes = try stream.readFully(count: headerLen)
        return try BackupPackageV1.parseHeaderJson(String(decoding: headerBytes, as: UTF8.self))
    }
}

private extension SHA256.Digest {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}

private extension InputStream {
    func readFully(count: Int) throws -> Data {
        var result = Data()
        var buffer = [UInt8](repeating: 0, count: min(count, 64 * 1024))
        while result.count < count {
            let need = count - result.count
            let n = read(&buffer, maxLength: min(need, buffer.count))
            if n < 0 { throw BackupError.io("read failed") }
            if n == 0 { throw BackupError.io("EOF") }
            result.append(contentsOf: buffer.prefix(n))
        }
        return result
    }

    func readInt32BE() throws -> Int32 {
        let b = try readFully(count: 4)
        return (Int32(b[0]) << 24) | (Int32(b[1]) << 16) | (Int32(b[2]) << 8) | Int32(b[3])
    }
}
