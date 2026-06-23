# 初版技术方案

日期：2026-06-22

## 目标

构建一个 Android 本地优先 App，让用户通过 WebView 登录小红书，同步自己的收藏笔记，自动分类并以文件夹式界面浏览，点击笔记可跳回小红书。

## 架构草案

```text
app
  ui
    login
    sync
    folders
    notes
    noteDetail
  domain
    auth
    sync
    classify
    navigation
  data
    rednote
      RednoteWebViewBridge
      RednoteSessionRepository
      RednoteFavoritesRepository
    local
      RoomDatabase
      NoteDao
      CategoryDao
      SyncStateDao
      CacheCleanupDao
    classification
      RuleClassifier
      CategoryMatcher
      PendingCategorySuggestionRepository
    security
      PrivacyDataCleaner
      LogSanitizer
      BackupPolicy
```

## 数据模型草案

### Note

- `id`：本地 ID。
- `rednoteId`：小红书笔记 ID，唯一。
- `noteUrl`：用于跳转的链接。
- `status`：active / removed / unavailable。
- `firstSeenAt`。
- `lastSeenAt`。
- `removedAt`。

约束：

- `rednoteId` 建唯一索引。
- `status`、`lastSeenAt` 建查询索引，用于活跃列表和软删除收敛。
- 第一版本地长期核心只存笔记 ID 和必要同步状态。标题、封面、摘要等如需用于展示或分类，应放入可清理的缓存表。

### NoteMetadataCache

- `rednoteId`。
- `title`。
- `desc`。
- `authorName`。
- `coverUrl`。
- `thumbnailCacheKey`。
- `rawJson`。
- `cachedAt`。
- `expiresAt`。

约束：

- `rednoteId` 建唯一索引。
- `expiresAt` 建索引，用于过期清理。
- 该表服务于分类内卡片预览。已确定采用轻量缓存：标题、摘要、作者、封面 URL 属于可清理展示缓存；图片二进制交给 Coil 等图片库缓存，不进入核心业务表。
- 缓存表清理不得影响 `Note`、`Category`、`NoteCategory`。
- `rawJson` 只允许保存必要调试字段或可清理原始片段；发布版本默认不长期保存完整响应。

### Category

- `id`。
- `name`。
- `type`：system / auto / manual。
- `sortOrder`。
- `createdAt`。
- `updatedAt`。

约束：

- `id` 使用 UUID 或稳定随机 ID，不使用分类名作为主键。
- `name` 可修改，`id` 不可变。

### NoteCategory

- `noteId`。
- `categoryId`。
- `source`：rule / embedding / llm / manual。
- `confidence`。
- `lockedByUser`。

约束：

- 建复合唯一索引：`noteId + categoryId`。
- 建索引：`categoryId`，用于分类内列表分页。
- 建索引：`noteId`，用于软删除和移动分类。
- 采用“分类 ID -> 多个笔记 ID”的关系模型，数据库上用 `NoteCategory` 关系表表达，避免把数组塞进单条 Category 记录。这样便于查询、移动、删除和未来支持一条笔记多个分类。

### SyncState

- `accountUserId`。
- `cursor`。
- `hasMore`。
- `status`：idle / running / failed / completed。
- `lastError`。
- `updatedAt`。

约束：

- `lastError` 只保存脱敏错误码、阶段和简短描述，不保存完整响应。
- 同步状态更新必须允许恢复：App 重启后能判断上次同步是否中断。

### PendingCategorySuggestion

- `id`。
- `rednoteIds`：同一候选分类名下的一组新增笔记 ID。
- `suggestedName`。
- `reason`。
- `status`：pending / accepted / dismissed。
- `createdAt`。

约束：

- `status` 建索引，待确认页面只查询 `pending`。
- `rednoteIds` 如果数量较大，应拆成 suggestion group 表和 item 表，避免单字段过大。

## 性能实现约束

- Room DAO 必须提供分类内分页查询，返回当前分类的 `Note + NoteMetadataCache` 投影，不在 UI 层手动 join。
- 分类首页的每个分类数量通过 SQL `COUNT` 或缓存统计字段获得。
- 同步页按分页结果批量 upsert，单页写入放在同一个数据库事务内。
- 分类匹配只处理新增笔记；已有笔记除非用户手动触发重新分类，否则不重复计算。
- Compose 列表使用稳定 key：`rednoteId` 或 `categoryId`。
- 图片使用 Coil，限制列表缩略图尺寸，启用磁盘缓存，不把图片二进制写入 Room。
- 展示缓存清理分两种：用户手动清理全部缓存；维护任务清理 `expiresAt` 过期缓存。
- 大批量同步时限制 JSBridge 单次 payload 大小，必要时按页或分片传输。

## 数据安全实现约束

- Cookie 不进入 Native 持久化层；Native 只接收收藏列表结构化字段。
- WebView JSBridge 只暴露同步所需方法，校验 action、字段类型、payload 大小和来源页面。
- Release 构建关闭详细网络日志、WebView 调试和完整响应日志。
- 所有日志必须经过脱敏包装，禁止直接打印接口响应、Cookie、签名头、raw JSON。
- 设置页提供四类清理：退出登录、清除本地索引、清除展示缓存、清除图片缓存。
- Android Auto Backup 默认排除数据库、WebView 数据和图片缓存，除非后续设计明确支持加密备份。
- 若未来接入云端 LLM 或云同步，必须新增用户授权、字段白名单、撤回授权和服务端删除机制。

## 第一阶段里程碑

1. 创建 Android 项目骨架。
2. 实现 WebView 登录页和登录态检测。
3. 通过 WebView bridge 获取当前用户。
4. 获取收藏第一页并入库。
5. 实现 Room 数据库、分类关系表和基础列表展示。
6. 实现规则分类、已有分类匹配和文件夹首页。
7. 实现新增类别确认弹窗，候选分类名预填。
8. 实现点击笔记跳转小红书或浏览器。

## 第二阶段里程碑

1. 完整分页同步。
2. WorkManager 后台同步。
3. Embedding/LLM 分类。
4. 用户手动移动分类。
5. 搜索和筛选。
6. 导出 Markdown/CSV。

## 技术决策

- 先做单模块 App，等功能稳定后再考虑多模块。
- 先实现本地优先，避免一开始引入后端。
- 小红书请求优先保留在 WebView 上下文，Native 不主动伪造签名请求。
- 分类第一版只做规则分类 + 「待整理」兜底；embedding/LLM 后置。
- 所有自动分类都必须允许用户修正。
- 按可能上架或一定量网络传播来设计隐私说明、数据清除和失败降级。
- 分类关系使用关系表，不使用 Category 内嵌笔记 ID 数组。
- 只有完整同步成功后才做本地删除收敛，避免接口失败导致误删。
- 取消收藏、下架、隐藏或远端不再返回的笔记使用软删除。
- 新分类确认采用批量确认，不逐条打断用户。
- 分类内卡片预览已确定使用轻量元数据缓存 + 图片库磁盘缓存，核心长期数据仍以笔记 ID 和分类关系为主。

## 测试策略

- 单元测试：分类规则、分页去重、数据映射、URL 构造。
- 集成测试：Room DAO、Repository、同步状态恢复。
- 性能测试：千级/万级笔记下的分类首页加载、分类内分页滚动、批量同步写库耗时。
- 安全测试：日志脱敏、清除数据、Cookie 不落库、Auto Backup 排除规则。
- 手工测试：WebView 登录、验证码、收藏同步、外部 App 跳转。

## 暂不解决

- 多账号隔离。
- 云同步。
- 全量图片离线缓存。
- embedding/LLM 智能分类。
