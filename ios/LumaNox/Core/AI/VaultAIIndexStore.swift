import Foundation

@MainActor
final class VaultAIIndexStore {
    static let shared = VaultAIIndexStore()

    private let fileManager = FileManager.default
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private var cached: VaultAIIndexSnapshot?

    private init() {
        encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        decoder = JSONDecoder()
    }

    func load() -> VaultAIIndexSnapshot {
        if let cached { return cached }
        guard fileManager.fileExists(atPath: indexURL().path),
              let encrypted = try? VaultCipher.shared.decryptFile(at: indexURL()),
              let snapshot = try? decoder.decode(VaultAIIndexSnapshot.self, from: encrypted),
              snapshot.schemaVersion == 1 else {
            cached = .empty
            return .empty
        }
        cached = snapshot
        return snapshot
    }

    func save(_ snapshot: VaultAIIndexSnapshot) throws {
        let url = indexURL()
        try fileManager.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let data = try encoder.encode(snapshot)
        try VaultCipher.shared.encryptFileFromChunks(to: url) { sink in
            try sink(data)
        }
        cached = snapshot
    }

    func replace(records: [VaultAIIndexRecord], subjectClusters: [VaultAISubjectCluster]) throws {
        let previousClusters = Dictionary(uniqueKeysWithValues: load().subjectClusters.map { ($0.id, $0) })
        let now = vaultAINowMs()
        let clusters = subjectClusters.map { cluster in
            var next = cluster
            if let previous = previousClusters[cluster.id] {
                next.name = previous.name
                next.createdAtMs = previous.createdAtMs
            }
            next.updatedAtMs = now
            return next
        }
        try save(VaultAIIndexSnapshot(
            schemaVersion: 1,
            analyzerVersion: VaultAIAnalysisService.currentAnalyzerVersion,
            records: records,
            subjectClusters: clusters,
            updatedAtMs: now
        ))
    }

    func updateSubjectClusterName(id: String, name: String?) throws {
        var snapshot = load()
        guard let index = snapshot.subjectClusters.firstIndex(where: { $0.id == id }) else { return }
        snapshot.subjectClusters[index].name = name?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        snapshot.subjectClusters[index].updatedAtMs = vaultAINowMs()
        snapshot.updatedAtMs = snapshot.subjectClusters[index].updatedAtMs
        try save(snapshot)
    }

    func mergeSubjectClusters(ids: Set<String>, name: String? = nil) throws {
        guard ids.count > 1 else { return }
        var snapshot = load()
        let selected = snapshot.subjectClusters.filter { ids.contains($0.id) }
        guard let first = selected.sorted(by: { $0.id < $1.id }).first else { return }
        let members = Array(Set(selected.flatMap(\.memberRecordIDs))).sorted()
        let mergedID = "subject_\(first.kind.rawValue)_\(stableHash(members))"
        let now = vaultAINowMs()
        let merged = VaultAISubjectCluster(
            id: mergedID,
            kind: first.kind,
            name: name?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty ?? selected.compactMap(\.name).first,
            memberRecordIDs: members,
            representativeRecordID: members.first,
            createdAtMs: selected.map(\.createdAtMs).min() ?? now,
            updatedAtMs: now
        )
        snapshot.subjectClusters.removeAll { ids.contains($0.id) }
        snapshot.subjectClusters.append(merged)
        for index in snapshot.records.indices where members.contains(snapshot.records[index].recordID) {
            snapshot.records[index].subjectClusterId = mergedID
        }
        snapshot.updatedAtMs = now
        try save(snapshot)
    }

    func splitSubjectCluster(id: String, moving memberIDs: Set<String>, name: String? = nil) throws {
        guard !memberIDs.isEmpty else { return }
        var snapshot = load()
        guard let index = snapshot.subjectClusters.firstIndex(where: { $0.id == id }) else { return }
        var original = snapshot.subjectClusters[index]
        let moving = original.memberRecordIDs.filter { memberIDs.contains($0) }
        guard !moving.isEmpty, moving.count < original.memberRecordIDs.count else { return }

        original.memberRecordIDs.removeAll { memberIDs.contains($0) }
        original.representativeRecordID = original.memberRecordIDs.first
        original.updatedAtMs = vaultAINowMs()
        let newID = "subject_\(original.kind.rawValue)_\(stableHash(moving))"
        let split = VaultAISubjectCluster(
            id: newID,
            kind: original.kind,
            name: name?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            memberRecordIDs: moving,
            representativeRecordID: moving.first,
            createdAtMs: original.updatedAtMs,
            updatedAtMs: original.updatedAtMs
        )
        snapshot.subjectClusters[index] = original
        snapshot.subjectClusters.append(split)
        for recordIndex in snapshot.records.indices where memberIDs.contains(snapshot.records[recordIndex].recordID) {
            snapshot.records[recordIndex].subjectClusterId = newID
        }
        snapshot.updatedAtMs = original.updatedAtMs
        try save(snapshot)
    }

    private func indexURL() -> URL {
        fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LumaNox", isDirectory: true)
            .appendingPathComponent("ai_results_v1.dat", isDirectory: false)
    }

    private func stableHash(_ ids: [String]) -> String {
        var hash: UInt64 = 0xcbf29ce484222325
        for byte in ids.sorted().joined(separator: "|").utf8 {
            hash ^= UInt64(byte)
            hash &*= 0x100000001b3
        }
        return String(format: "%016llx", hash)
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
