import Foundation
import Photos
@testable import LumaNox
import UIKit
import XCTest

@MainActor
final class NetworkAIDatasetPhotoLibrarySeedTests: XCTestCase {
    private let enabledEnvironmentKey = "LUMANOX_ENABLE_NETWORK_AI_PHOTO_SEED"
    private let enabledMarkerPath = ".tmp/enable-network-ai-photo-seed"
    private let photoAlbumName = "LumaNox AI Network Fixtures"

    func testDownloadNetworkFixturesSeedSimulatorPhotosAndScanVault() async throws {
        guard networkSeedEnabled() else {
            throw XCTSkip("Set \(enabledEnvironmentKey)=1 in the test scheme or create \(enabledMarkerPath) to download network fixtures and seed the simulator Photos library.")
        }

        let authorization = await requestPhotoAuthorization()
        guard authorization == .authorized || authorization == .limited else {
            throw XCTSkip("Grant Photos access first: xcrun simctl privacy booted grant photos com.xpx.vault")
        }

        let downloaded = try await downloadSamples(Self.samples)
        let photoAlbum = try await ensurePhotoAlbum(named: photoAlbumName)
        try await addToPhotoLibrary(downloaded, album: photoAlbum)

        let vaultAlbumName = "AI_Network_\(Int(Date().timeIntervalSince1970))"
        var importSummary = VaultImportSummary()
        for sample in downloaded {
            let result = await VaultStore.shared.importPlainData(
                sample.data,
                fileExtension: sample.fileExtension,
                albumName: vaultAlbumName,
                originalFileName: sample.fileName
            )
            switch result {
            case .added: importSummary.added += 1
            case .duplicate: importSummary.duplicate += 1
            case .failed: importSummary.failed += 1
            }
        }

        await VaultStore.shared.loadSnapshot(recentLimit: downloaded.count + 20)
        XCTAssertEqual(importSummary.failed, 0)
        XCTAssertEqual(importSummary.added, downloaded.count)

        await VaultAIAnalysisService.shared.scanVault()
        let records = VaultAIAnalysisService.shared.records.filter { $0.albumName == vaultAlbumName }
        XCTAssertEqual(records.count, downloaded.count)
        XCTAssertEqual(records.filter { $0.ai.scannedAtMs != nil }.count, downloaded.count)

        let categories = Dictionary(grouping: records, by: { $0.ai.category ?? "nil" }).mapValues(\.count)
        print("AI_NETWORK_SEED_PHOTOS album=\(photoAlbumName) vaultAlbum=\(vaultAlbumName) added=\(importSummary.added)")
        print("AI_NETWORK_SEED_CATEGORIES \(formatCounts(categories))")

        XCTAssertGreaterThanOrEqual(categories[VaultAICategory.people, default: 0], 1)
        XCTAssertGreaterThanOrEqual(categories[VaultAICategory.food, default: 0], 1)
        XCTAssertGreaterThanOrEqual(categories[VaultAICategory.nature, default: 0], 1)
        XCTAssertGreaterThanOrEqual(categories[VaultAICategory.documents, default: 0], 2)
        XCTAssertGreaterThanOrEqual(categories[VaultAICategory.screenshots, default: 0], 1)
    }

    private func requestPhotoAuthorization() async -> PHAuthorizationStatus {
        let current = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard current == .notDetermined else { return current }
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                continuation.resume(returning: status)
            }
        }
    }

    private func networkSeedEnabled() -> Bool {
        if ProcessInfo.processInfo.environment[enabledEnvironmentKey] == "1" {
            return true
        }

        var candidates: [URL] = []
        var sourceCursor = URL(fileURLWithPath: #filePath)
        sourceCursor.deleteLastPathComponent()
        for _ in 0..<8 {
            candidates.append(sourceCursor.appendingPathComponent(enabledMarkerPath))
            sourceCursor.deleteLastPathComponent()
        }

        return candidates.contains { FileManager.default.fileExists(atPath: $0.path) }
    }

    private func downloadSamples(_ samples: [NetworkAISample]) async throws -> [DownloadedNetworkAISample] {
        var downloaded: [DownloadedNetworkAISample] = []
        downloaded.reserveCapacity(samples.count)

        for sample in samples {
            var request = URLRequest(url: sample.url)
            request.setValue("LumaNoxAIRegression/1.0", forHTTPHeaderField: "User-Agent")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw XCTSkip("Network fixture failed: \(sample.url.absoluteString)")
            }
            guard UIImage(data: data) != nil else {
                XCTFail("Downloaded fixture is not a supported image: \(sample.fileName)")
                continue
            }
            downloaded.append(
                DownloadedNetworkAISample(
                    sample: sample,
                    data: data,
                    fileExtension: sample.url.pathExtension.isEmpty ? sample.fallbackExtension : sample.url.pathExtension
                )
            )
        }

        return downloaded
    }

    private func ensurePhotoAlbum(named name: String) async throws -> PHAssetCollection {
        if let existing = fetchPhotoAlbum(named: name) {
            return existing
        }

        var placeholderID: String?
        try await performPhotoChanges {
            let request = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
            placeholderID = request.placeholderForCreatedAssetCollection.localIdentifier
        }

        if let placeholderID {
            let collection = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [placeholderID], options: nil)
            if let album = collection.firstObject {
                return album
            }
        }

        guard let album = fetchPhotoAlbum(named: name) else {
            throw NSError(domain: "LumaNoxTests", code: 1, userInfo: [NSLocalizedDescriptionKey: "Photo album creation failed."])
        }
        return album
    }

    private func fetchPhotoAlbum(named name: String) -> PHAssetCollection? {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "title == %@", name)
        return PHAssetCollection.fetchAssetCollections(with: .album, subtype: .albumRegular, options: options).firstObject
    }

    private func addToPhotoLibrary(_ samples: [DownloadedNetworkAISample], album: PHAssetCollection) async throws {
        try await performPhotoChanges {
            guard let albumRequest = PHAssetCollectionChangeRequest(for: album) else { return }
            let placeholders = samples.compactMap { sample -> PHObjectPlaceholder? in
                guard let image = UIImage(data: sample.data) else { return nil }
                let request = PHAssetChangeRequest.creationRequestForAsset(from: image)
                request.creationDate = Date()
                return request.placeholderForCreatedAsset
            }
            albumRequest.addAssets(placeholders as NSArray)
        }
    }

    private func performPhotoChanges(_ changes: @escaping () -> Void) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges(changes) { success, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: NSError(domain: "LumaNoxTests", code: 2, userInfo: [NSLocalizedDescriptionKey: "Photo library change failed."]))
                }
            }
        }
    }

    private func formatCounts(_ counts: [String: Int]) -> String {
        counts.sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: ",")
    }
}

private struct NetworkAISample {
    let kind: String
    let fileName: String
    let fallbackExtension: String
    let url: URL
}

private struct DownloadedNetworkAISample {
    let sample: NetworkAISample
    let data: Data
    let fileExtension: String

    var fileName: String { sample.fileName }
}

private extension NetworkAIDatasetPhotoLibrarySeedTests {
    static let samples: [NetworkAISample] = [
        NetworkAISample(
            kind: "people",
            fileName: "people_openimages_0007cebe1b2ba653.jpg",
            fallbackExtension: "jpg",
            url: URL(string: "https://open-images-dataset.s3.amazonaws.com/validation/0007cebe1b2ba653.jpg")!
        ),
        NetworkAISample(
            kind: "food",
            fileName: "food_openimages_000595fe6fee6369.jpg",
            fallbackExtension: "jpg",
            url: URL(string: "https://open-images-dataset.s3.amazonaws.com/validation/000595fe6fee6369.jpg")!
        ),
        NetworkAISample(
            kind: "nature",
            fileName: "nature_openimages_001a78754e43abc5.jpg",
            fallbackExtension: "jpg",
            url: URL(string: "https://open-images-dataset.s3.amazonaws.com/validation/001a78754e43abc5.jpg")!
        ),
        NetworkAISample(
            kind: "document",
            fileName: "document_openimages_034f57948233aa89.jpg",
            fallbackExtension: "jpg",
            url: URL(string: "https://open-images-dataset.s3.amazonaws.com/validation/034f57948233aa89.jpg")!
        ),
        NetworkAISample(
            kind: "id_document",
            fileName: "id_document_openimages_03b7950414a5d89d.jpg",
            fallbackExtension: "jpg",
            url: URL(string: "https://open-images-dataset.s3.amazonaws.com/validation/03b7950414a5d89d.jpg")!
        ),
        NetworkAISample(
            kind: "screenshot",
            fileName: "screenshot_openimages_007f71665b0812a7.jpg",
            fallbackExtension: "jpg",
            url: URL(string: "https://open-images-dataset.s3.amazonaws.com/validation/007f71665b0812a7.jpg")!
        ),
        NetworkAISample(
            kind: "qr_barcode",
            fileName: "qr_barcode_network_seed.png",
            fallbackExtension: "png",
            url: URL(string: "https://api.qrserver.com/v1/create-qr-code/?size=640x640&data=LumaNox%20AI%20classification%20test")!
        ),
    ]
}
