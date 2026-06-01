@testable import LumaNox
import XCTest

@MainActor
final class QuotaManagerTests: XCTestCase {
    func testVaultImportQuotaReopensAfterCurrentCountDropsBelowLimit() {
        let quota = QuotaManager.shared

        XCTAssertTrue(
            quota.isVaultFull(
                isPremium: false,
                currentCount: FreeQuota.maxVaultItems
            )
        )

        XCTAssertFalse(
            quota.isVaultFull(
                isPremium: false,
                currentCount: FreeQuota.maxVaultItems - 10
            )
        )
        XCTAssertEqual(quota.vaultCount, FreeQuota.maxVaultItems - 10)
    }
}
