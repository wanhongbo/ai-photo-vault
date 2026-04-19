# iOS 分项技术方案（一期）

本目录为《私密相册 App（一期）原生双端架构设计方案》中 **iOS（Swift）** 侧各核心功能的独立说明，与 `doc/android/` 下同名编号文档一一对应，便于双端评审与并行迭代。

**关联总览**：[私密相册 App（一期）原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md)  
**成熟三方库选型参考**：[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)（减少从零实现）

| 编号 | 文档 | 功能 |
|------|------|------|
| 01 | [01-本地图片库-系统相册读取.md](./01-本地图片库-系统相册读取.md) | Photos、权限、PHPicker、缓存 |
| 02 | [02-照片导入与AES本地加密.md](./02-照片导入与AES本地加密.md) | 导入流程、CryptoKit、Keychain、元数据 |
| 03 | [03-解锁与安全模块.md](./03-解锁与安全模块.md) | PIN/图案、LocalAuthentication、场景锁定、备用图标、闯入抓拍 |
| 04 | [04-私密拍照.md](./04-私密拍照.md) | AVFoundation、不入相册、内存预览 |
| 05 | [05-相册管理.md](./05-相册管理.md) | Core Data/GRDB、回收站、导出到相册 |
| 06 | [06-AI打码.md](./06-AI打码.md) | TFLite/Core ML、Core Image、并发 |
| 07 | [07-海外社交分享.md](./07-海外社交分享.md) | UIActivityViewController、临时文件 |
| 08 | [08-多语言国际化.md](./08-多语言国际化.md) | Localizable、String Catalog、Locale |
| 09 | [09-备份与恢复.md](./09-备份与恢复.md) | ZIP、加密、DocumentPicker、iCloud 文件 |
| 10 | [10-内购付费.md](./10-内购付费.md) | RevenueCat（底层 StoreKit） |
| 11 | [11-Firebase监控.md](./11-Firebase监控.md) | **一期暂缓**；后续可选 Crashlytics 等 |
