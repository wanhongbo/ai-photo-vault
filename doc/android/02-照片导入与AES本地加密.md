# 照片导入与 AES 本地加密 — Android

**平台**：Android（Kotlin）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md) · [iOS 对应文档](../ios/02-照片导入与AES本地加密.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 2 节**。

---

## 技术方案要点

### 流程

- 从 Photo Picker 或 `MediaStore` 得到 `Uri` → `ContentResolver.openInputStream` 流式拷贝至应用私有目录（如 `filesDir` 下临时文件）→ 在 **后台协程**（如 `Dispatchers.IO` + `Default` 分阶段）完成 AES 加密写入最终密文路径 → **删除**临时明文 → Room 记录密文路径、相册 ID、时间等元数据。

### 算法与格式

- `javax.crypto.Cipher`（AES/CBC/PKCS7Padding 或团队统一为 GCM 等）与 **固定密文头格式**（魔数、版本、IV、认证标签若用 AEAD）便于双端互认。

### 密钥

- 主密钥经 **Android Keystore** 保护；数据加密密钥可经 Keystore 包装后存 `EncryptedSharedPreferences` 或安全芯片可用时的最佳实践。

### 合规

- 全程本地，无网络上传。

---

## 分层落位（参考）

- **Domain**：`ImportPhotos`、`EncryptAsset` 等用例。
- **Data**：加密管线、Room 事务；与 iOS 共用密文文件格式约定。
