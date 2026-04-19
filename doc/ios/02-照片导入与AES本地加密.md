# 照片导入与 AES 本地加密 — iOS

**平台**：iOS（Swift）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md) · [Android 对应文档](../android/02-照片导入与AES本地加密.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 2 节**。

---

## 技术方案要点

### 流程

- 通过 `PHImageManager` / Picker 得到 `Data` 或临时文件 URL → 写入私有容器（如 `Application Support`）→ **CryptoKit**（或 `CommonCrypto` 若需与 Android CBC 字节级一致）完成加密 → 删除明文临时文件 → Core Data / GRDB 写入元数据。

### 密钥

- 对称密钥、`SecKey` 与 **Keychain**（`kSecAttrAccessible` 按需）管理；禁止将密钥明文写入 UserDefaults 或日志。

### 双端对齐

- 与 Android 共用 **同一套**「文件头 + IV + 密文体」规范及 KDF（若使用口令派生密钥），否则备份跨端无法解密。

---

## 分层落位（参考）

- **Domain**：导入与加密用例；**Data**：加密实现与持久化元数据。
