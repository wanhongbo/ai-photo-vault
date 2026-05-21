# ADR 0001: Vault 文件 v2 AEAD 加密协议

## 状态

Accepted for implementation as a PoC in Phase 8. Production migration is staged separately and must keep existing v1 media readable.

## 背景

iOS 当前 Vault 文件使用 `AES-256-CBC + PKCS7`，文件布局为 `16B IV || ciphertext`。该格式与 Android 现有保险箱数据兼容，但文件本身没有认证标签：密文被篡改时只能依赖 padding、媒体解析或上层备份校验发现问题。Phase 8 的目标是在不立即迁移历史数据的前提下，定下 Vault 文件 v2 协议，并验证 v1/v2 混合读写能力。

备份包格式不属于本 ADR 范围。备份仍按明文 chunk 重新封装为 `.aivb` / `backup.dat`，因此 Vault 文件 v1 或 v2 不改变跨端备份包协议。

## 决策

Vault 文件 v2 使用 `AES-256-GCM` 分块 AEAD。iOS `VaultCipher` 实现双读单写：

- v1 CBC 文件继续可读。
- 新写入的 Vault 文件默认使用 v2 AEAD。
- 测试与未来迁移工具仍可显式写 v1。
- metadata 的 `cipherVersion` 通过文件头探测得到。

## 备选方案

### AES-256-GCM

优点：

- CryptoKit 原生支持，iOS 16+ 可用。
- Android Keystore / Tink / Conscrypt 生态支持成熟，跨端实现成本低。
- AEAD 同时提供机密性和完整性，适合分块认证。

缺点：

- Nonce 复用风险高，必须由协议固定 nonce 派生规则。
- 标准 GCM nonce 为 96 bit，不提供 XChaCha 那种超大随机 nonce 空间。

结论：采用。

### ChaCha20-Poly1305 / XChaCha20-Poly1305

优点：

- 软件性能稳定，低端设备上通常表现好。
- XChaCha20-Poly1305 的 nonce 空间更宽，随机 nonce 风险更低。

缺点：

- CryptoKit 只提供 ChaChaPoly，不提供 XChaCha20-Poly1305。
- Android 跨端一致实现需要额外依赖或自维护协议胶水。

结论：暂不采用。若未来 Android 与 iOS 都引入统一加密库，可重新评估。

### v1 CBC + HMAC 过渡

优点：

- 对 v1 结构改动较小。
- 可补上认证能力。

缺点：

- 仍保留 CBC 复杂性，需要定义 encrypt-then-MAC、key separation、尾部 tag 和迁移规则。
- 新老 v1 文件容易混淆，长期维护成本高。

结论：不作为目标协议，仅作为紧急兼容方案保留在设计讨论中。

## v2 文件格式

固定头：

```text
magic      8 bytes   "LNVLT2\0\0"
version    1 byte    0x02
algorithm  1 byte    0x01 = AES-256-GCM
chunkSize  4 bytes   big-endian UInt32, current default 65536
baseNonce 12 bytes   random per file
aadLen     2 bytes   big-endian UInt16
aad        aadLen bytes, currently empty
```

每个 chunk frame：

```text
plainLen   4 bytes   big-endian UInt32
sealedLen  4 bytes   big-endian UInt32, ciphertext + 16B tag
sealed     sealedLen bytes
```

Nonce 派生：

```text
nonce = baseNonce[0..<4] || bigEndianUInt64(chunkIndex)
```

每个 frame 的 authenticated data：

```text
header || bigEndianUInt64(chunkIndex) || bigEndianUInt32(plainLen)
```

这使文件头、chunk 顺序和明文长度都参与认证。解密时任何 header、body 或 tag 篡改都会失败。

## 双读单写与迁移

读取：

- 读取前 8 bytes。
- 若等于 v2 magic，按 v2 AEAD frame 解密。
- 否则按 v1 `IV || AES-CBC ciphertext` 解密。

写入：

- App 新导入、相机入库、恢复重写 Vault 文件时默认写 v2。
- 测试和迁移工具可以显式指定写 v1，用于兼容夹具和回归。

迁移：

- 用户解锁后后台低优先级扫描 metadata 中 `cipherVersion == 1` 的文件。
- 每个文件解密到受控临时 session，再以 v2 写入同路径临时文件，最后原子替换。
- 单文件失败只记录并重试，不阻塞列表、备份、缩略图或查看器。
- 迁移过程中备份读取统一走 `decryptStream`，因此 v1/v2 混合 Vault 可正常备份。

## 安全与隐私说明

- Data key 仍来自现有 Keychain master key，不在文件头中存储。
- v2 header 不包含用户可识别文件名、相册名或媒体属性。
- 缩略图、导出、分享、视频播放仍必须通过受控临时明文 manager 管理生命周期。
- 日志不得输出 PIN、key、nonce、完整明文路径或解密失败文件内容。

## Phase 8 PoC 验收

- `VaultCipher` 可写 v2 并读回原文。
- `VaultCipher` 可同时读取显式写入的 v1 和 v2 文件。
- v2 密文被篡改时解密失败。
- metadata `cipherVersion` 能标识 v2 新文件，并保留 v1 兼容。
