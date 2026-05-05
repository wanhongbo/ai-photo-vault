package com.xpx.vault.ai.algo

import com.xpx.vault.domain.model.AiPerceptualHash

/**
 * 对一批 [AiPerceptualHash] 做近似重复聚类。
 *
 * 算法：贪心聚类。
 *   1. 对每个哈希，与已有簇的种子比较 dHash + pHash；
 *   2. 若 dHash 汉明距离 ≤ [dHashThreshold] 或 pHash ≤ [pHashThreshold]，归入该簇；
 *   3. 否则新建簇；
 *
 * 复杂度 O(N·K)，K 为簇数，实际 K 远小于 N，可满足上万张相册。
 */
object DuplicateClusterer {

    private const val DEFAULT_DHASH_THRESHOLD = 5
    private const val DEFAULT_PHASH_THRESHOLD = 5

    data class Cluster(
        val groupId: Long,
        val photoIds: List<Long>,
    )

    fun cluster(
        hashes: List<AiPerceptualHash>,
        dHashThreshold: Int = DEFAULT_DHASH_THRESHOLD,
        pHashThreshold: Int = DEFAULT_PHASH_THRESHOLD,
    ): List<Cluster> {
        if (hashes.isEmpty()) return emptyList()
        val seeds = mutableListOf<AiPerceptualHash>()        // 每簇种子
        val members = mutableListOf<MutableList<Long>>()     // 对应成员列表
        for (h in hashes) {
            var matched = -1
            for (i in seeds.indices) {
                val s = seeds[i]
                val dd = PerceptualHasher.hamming(h.dhash, s.dhash)
                val dp = PerceptualHasher.hamming(h.phash, s.phash)
                if (dd <= dHashThreshold || dp <= pHashThreshold) {
                    matched = i
                    break
                }
            }
            if (matched >= 0) {
                members[matched].add(h.photoId)
            } else {
                seeds.add(h)
                members.add(mutableListOf(h.photoId))
            }
        }
        // 只保留大小 ≥ 2 的簇作为"重复"；单张归非重复。
        return members.mapIndexedNotNull { idx, ids ->
            if (ids.size >= 2) Cluster(groupId = idx.toLong() + 1, photoIds = ids) else null
        }
    }
}
