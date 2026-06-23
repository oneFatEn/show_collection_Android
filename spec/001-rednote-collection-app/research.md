# 技术调研与风险记录

日期：2026-06-22

## Spec Coding 调研

GitHub Spec Kit 的核心观点是把规格作为主产物，代码服务于规格。推荐流程是：

1. 先写 `spec.md`，关注用户场景、需求、验收标准，不写具体技术栈。
2. 再写 `plan.md`，明确技术栈、架构、数据模型和取舍。
3. 再写 `tasks.md`，拆成可执行任务。
4. 最后实现，并在实现后让规格、计划和任务保持一致。

对本项目的落地方式：

- 使用用户指定的 `spec/` 目录，而不是完全照搬 `specs/`。
- 每个大功能一个编号目录，例如 `001-rednote-collection-app/`。
- 先在 `research.md` 中记录事实、风险和选型，再进入具体任务拆分。

## Android 开发建议

Google Android 官方架构建议至少分为 UI layer 和 Data layer，复杂业务再增加 Domain layer。本项目存在登录同步、分页去重、分类和展示等业务逻辑，建议使用三层：

```text
UI layer
  Compose screens + ViewModel + UI state

Domain layer
  SyncFavoritesUseCase
  ClassifyNotesUseCase
  OpenRednoteUseCase
  SearchNotesUseCase

Data layer
  RednoteWebRepository
  NoteRepository
  CategoryRepository
  ClassificationRepository
  Local database / WebView bridge / model provider
```

## 推荐技术栈

- 语言：Kotlin。
- UI：Jetpack Compose + Material 3。
- 架构：ViewModel + Kotlin Coroutines + Flow + Repository + UseCase。
- 本地数据库：Room。
- 简单配置：DataStore，例如首次启动状态、同步偏好、分类偏好。
- 后台同步：WorkManager，用于用户离开页面后仍需可靠完成的同步/分类任务。
- 依赖注入：Hilt。早期也可以手写 DI，等模块变多再引入。
- 图片加载：Coil。
- Web 登录：Android WebView + CookieManager + JSBridge。
- 网络：优先让 WebView 上下文发起小红书请求；Native 只接收结构化结果。其他自有服务可使用 OkHttp/Ktor。

## 性能设计原则

- 数据量按用户有数千到数万条收藏设计，不能假设只有几十条。
- UI 使用 Compose lazy list/grid，列表项保持稳定 key，避免同步时整页重组。
- Room 查询通过 `Flow` 暴露给 ViewModel，UI 只订阅当前页面需要的数据。
- 分类首页的笔记数量、封面预览通过 SQL 聚合或预计算字段获得，避免每次进入首页全量遍历 `NoteCategory`。
- 收藏同步按页处理，每页完成后批量 upsert `Note` 和 `NoteMetadataCache`，再批量写入分类关系。
- 完整同步成功后再做软删除收敛；收敛也应批量更新状态，避免逐条写库。
- 卡片封面只加载缩略图 URL 或指定尺寸请求，列表滚动时不解码大图。
- 图片缓存设置磁盘上限；展示缓存设置 `expiresAt`，由用户清理或维护任务清理过期缓存。

## 数据安全原则

- 最小化存储：长期核心数据只包含笔记 ID、分类 ID、关系、状态和必要时间戳。
- 隔离凭据：Cookie 只在 WebView CookieStore，Native 不持久化 Cookie，不把 Cookie 传给后端。
- 日志脱敏：调试日志只记录阶段、数量、状态码和错误类型，不记录完整响应体。
- 本地删除：设置页必须支持清除登录态、清除索引数据、清除展示缓存、清除图片缓存。
- 备份控制：如果使用 Android Auto Backup，需要明确是否排除数据库和 WebView 数据；默认建议排除敏感数据备份。
- 截屏风险：登录 WebView 和设置隐私页可考虑启用防截屏；至少不在普通页面展示 Cookie 或敏感调试信息。
- JSBridge 最小暴露：只暴露必要方法，只接受预期 JSON schema，拒绝超大 payload 和未知 action。

## 产品分发约束

当前不只按个人自用 APK 设计，需要考虑上架或一定量网络传播。因此第一版必须补齐：

- 首次启动隐私说明：解释 App 会在本机 WebView 中打开小红书、同步用户自己的收藏摘要、数据默认保存在本机。
- 数据删除能力：提供清除 WebView 登录态、清除本地数据库、清除图片缓存的入口。
- 第三方依赖提示：明确小红书 Web 登录和收藏同步依赖第三方页面与接口，可能因平台变化失效。
- 最小权限原则：第一版不申请通讯录、定位、相册等无关权限。
- 日志脱敏：不记录 Cookie、完整接口响应、用户昵称以外的敏感身份标识。
- 自动备份策略：默认不把 WebView 登录态、数据库和图片缓存纳入云端自动备份，除非用户明确开启。
- 发布策略：如果上架受阻，应准备侧载/TestFlight 类似测试分发、官网 APK、内测群等替代路径。

## 小红书接入策略

### 不推荐

- 通过 openapp、scheme 或 universal link 读取小红书官方 App 登录态。
- 在 Native 层直接保存并拼装 Cookie 后请求接口。
- 在 Native 层复刻或绕过小红书 Web 签名机制。

### 推荐第一版

```text
Android WebView 打开小红书
  -> 用户手动登录
  -> WebView 自己携带 Cookie
  -> WebView 页面上下文内调用已验证 Web 接口
  -> JSBridge 把收藏列表 JSON 传给 Native
  -> Native 入库、分类、展示
```

这样做的理由：

- 第三方 App 无法读取另一个 App 沙盒内 Cookie。
- Web API 依赖小红书 Web 签名环境；保留 WebView 上下文比 Native 伪造请求更稳定。
- Native 层不直接处理登录凭据，风险边界更清晰。

## 收藏同步要点

已验证收藏列表接口路径：

```text
/api/sns/web/v2/note/collect/page
```

关键分页字段：

```text
data.notes
data.cursor
data.has_more
```

同步策略：

- 每次请求保存 `cursor`、`has_more`、同步时间和同步状态。
- 用小红书笔记 ID 做唯一键。
- 同步失败保留已成功页，不回滚所有数据。
- 对接口字段做兼容解析，未知字段保存到 raw JSON 以便后续排查。
- 本地长期核心数据是笔记 ID 与分类关系；标题、封面、摘要等只作为必要展示/分类缓存，后续可设置过期或清除。
- 增量同步时只对新增笔记执行分类；存量笔记保留原分类。
- 全量同步成功后，对本地存在但远端收藏列表不存在的笔记执行删除或软删除。
- 为避免误删，删除应只在完整分页同步成功后触发；如果同步中断、接口异常或登录失效，不做远端缺失判断。
- 已确定对取消收藏、下架、隐藏或远端不再返回的笔记使用软删除。活跃视图隐藏，数据清理由设置页或后续维护任务处理。

## 分类策略

第一版已确定从低风险、可解释方案开始：

1. 文本规则兜底：标题、标签、关键词命中常见分类。
2. 未命中规则时归入「待整理」。
3. 人工修正优先：用户移动过的笔记不被自动分类覆盖。
4. 对新增笔记优先匹配已有分类；如果没有明显匹配项，则生成候选分类名，批量进入「待确认分类」页面让用户一次性确认。
5. 新建分类必须生成唯一类别 ID，分类名允许后续修改，但类别 ID 不变。

## 卡片预览数据策略

分类内部需要以卡片形式展示笔记：左侧预览图，右侧标题和简要内容。已确定采用轻量缓存：

- 核心长期数据：`rednoteId`、分类关系、状态、同步时间。
- 轻量展示缓存：标题、摘要、作者、封面 URL、跳转 URL、最后缓存时间。
- 图片缓存：交给图片加载库按磁盘缓存策略管理，可清理、可过期。
- 完整正文/大图：第一版不长期保存，用户打开笔记时跳转小红书查看。

理由：

- 只存笔记 ID 会导致每次进入分类都依赖网络，列表体验差，也无法离线展示基本信息。
- 长期保存完整笔记内容会增加隐私和合规压力。
- 保存标题、摘要、封面 URL 作为缓存，是体验和隐私之间的折中。
- 核心表和缓存表必须分离；用户清除展示缓存后，分类关系、笔记 ID 和软删除状态仍应保留。

Embedding/LLM 作为第二阶段能力。在线 LLM 的隐私风险较高。如果发送笔记标题、正文或图片到云端，需要明确告知用户并提供本地优先/关闭选项。

## 打开小红书笔记

跳转策略：

1. 优先构造小红书 App 可识别的 deep link 或 Web URL。
2. 使用 Android Intent 拉起外部 App。
3. 若无法解析，降级打开浏览器。
4. 若仍失败，允许复制链接。

需要实测不同笔记 URL、不同小红书版本和未登录状态下的表现。

## 主要风险

- 合规风险：小红书没有公开收藏 API，非官方接口可能违反服务条款或随时失效。
- 稳定性风险：Web 页面、签名环境、接口字段、风控策略都可能变化。
- 隐私风险：收藏内容高度个人化，分类模型和日志必须谨慎处理。
- 审核风险：如果未来上架应用商店，非官方接入第三方平台可能被拒。
- 体验风险：WebView 登录可能遇到验证码、风控、页面适配和 Cookie 持久化问题。

## 需要实测

- Android WebView 中小红书登录是否稳定。
- WebView 能否访问收藏页并执行收藏接口请求。
- JSBridge 返回大批量 JSON 是否需要分片。
- 小红书笔记 ID 到 App 跳转 URL 的映射。
- 图片封面缓存策略和磁盘占用。
