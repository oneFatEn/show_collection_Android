# Show Collection Android 技术与产品讨论沉淀

日期：2026-06-22

## 1. 项目目标

目标是做一个面向个人的小红书收藏管理工具：

- 获取用户自己的小红书收藏笔记。
- 基于笔记标题、文案、标签、封面等内容进行自动分类。
- 使用 LLM 或 embedding 模型把收藏划分为旅游、美食、做饭、礼物、穿搭、学习等分类。
- 在移动端提供一个分类 tab，方便用户按主题浏览自己的收藏。
- 后续可支持语义搜索、笔记详情预览、手动修正分类、导出到 Markdown/Obsidian。

## 2. 已验证的桌面 Electron 原型

当前已经在 `~/my_coding/rednote-login-favorites-demo` 下做过一个 Electron 原型，用来验证小红书登录态和收藏列表读取。

原型能力包括：

- 打开真实小红书网页登录页。
- 检查登录态。
- 获取当前登录用户。
- 获取收藏笔记列表。
- 支持分页加载更多。
- 页面显示当前列表中的笔记数量。

这个原型验证了整体技术链路是可跑通的，但它依赖小红书 Web 接口和网页签名环境，属于非官方 API 路线。

## 3. 小红书登录态如何获取

### 3.1 Electron 原型中的登录态获取

Electron 方案中使用一个持久化 session：

```text
persist:rednote
```

流程：

```text
Electron 打开 https://www.xiaohongshu.com
  -> 用户在小红书网页中登录
  -> 小红书写入 Web cookie
  -> Electron session 保存 cookie
  -> 主进程读取 session 中的 a1 cookie
```

关键 cookie：

```text
a1
```

检测到 `a1` 基本说明该 Electron Web session 中已有小红书登录态。

### 3.2 当前用户接口

登录后通过当前用户接口确认账号身份：

```text
/api/sns/web/v2/user/me
```

该接口返回里关注：

```text
data.user_id
data.nickname
```

其中 `user_id` 后续用于请求收藏列表。

### 3.3 移动端登录态获取方案

移动端不建议通过 openapp / scheme 拉起小红书官方 App 登录。

原因：

- 小红书官方 App 的 cookie/token 存在官方 App 自己的沙盒中。
- 第三方 App 无法读取另一个 App 的私有 cookie。
- openapp / universal link 只能拉起 App，不能把官方 App 的登录态回传给自己的 App。

推荐移动端方案：

```text
自己的 App
  -> 内嵌 WebView
  -> 打开 https://www.xiaohongshu.com
  -> 用户在 WebView 内登录
  -> cookie 存在本 App 的 WebView CookieStore
  -> 后续仍在这个 WebView 上下文中请求小红书接口
```

重点：不一定要把 cookie 回传给 Native。更稳的做法是让 WebView 自己携带 cookie 发请求，Native 通过 JSBridge 接收结果。

## 4. 小红书收藏夹内容如何获取

### 4.1 收藏列表接口

已验证收藏列表接口：

```text
https://edith.xiaohongshu.com/api/sns/web/v2/note/collect/page
```

路径：

```text
/api/sns/web/v2/note/collect/page
```

### 4.2 收藏接口必要参数

从原插件和 DevTools 抓包中确认，收藏接口参数需要包含：

```text
num=10
user_id=<当前账号 user_id>
image_formats=jpg,webp,avif
xsec_token=
xsec_source=
```

第一页请求不带 cursor。

后续分页追加：

```text
cursor=<上一页返回的 cursor>
```

示例：

```text
/api/sns/web/v2/note/collect/page?num=10&user_id=xxx&image_formats=jpg,webp,avif&xsec_token=&xsec_source=
```

加载更多：

```text
/api/sns/web/v2/note/collect/page?num=10&user_id=xxx&image_formats=jpg,webp,avif&xsec_token=&xsec_source=&cursor=yyy
```

如果缺少 `user_id`，会返回：

```text
code=-9109
message=参数错误
```

### 4.3 收藏接口返回结构

关键字段：

```text
data.cursor
data.has_more
data.notes
```

含义：

- `notes`：当前页收藏笔记列表。
- `cursor`：下一页游标。
- `has_more`：是否还有下一页。

分页逻辑：

```text
首次请求
  -> 读取 notes
  -> 保存 cursor
  -> 如果 has_more=true，允许加载更多

加载更多
  -> 带上 cursor 再请求
  -> 追加 notes
  -> 更新 cursor 和 has_more
```

## 5. 为什么不能只用 URL 和 cookie

小红书 Web API 不只依赖登录 cookie，还需要网页端签名头：

```text
x-s
x-t
x-s-common
x-b3-traceid
```

Electron 原型中通过隐藏窗口加载小红书网页，并等待网页中的签名函数可用：

```text
window.mnsv2
```

然后在该网页上下文中生成签名，再请求接口。

这也是为什么纯静态 HTML + JS 无法直接完成真实收藏读取：

- 普通浏览器页面不能读取小红书跨域 cookie。
- 不能随意伪造小红书 Web 签名环境。
- 小红书接口对参数和签名都敏感。

## 6. 如何从零发现这些接口

即使没有原插件，也可以通过 Chrome DevTools 抓包发现接口。

步骤：

1. 打开小红书网页版。
2. 登录自己的账号。
3. 打开 DevTools 的 Network 面板。
4. 勾选：
   - `Preserve log / 保留日志`
   - `Disable cache / 停用缓存`
   - `Fetch/XHR`
5. 打开收藏页或滚动收藏列表。
6. 在过滤框搜索：

```text
collect
note/collect
user/me
page
```

收藏列表请求通常类似：

```text
page?num=30&cursor=&user_id=...
```

注意区分：

- `/api/sns/web/v2/note/collect/page` 是收藏列表。
- `https://t2.xiaohongshu.com/api/v2/collect` 更像埋点或行为上报，不是收藏列表数据接口。

## 7. 移动端 WebView 方案

### 7.1 推荐架构

移动端推荐架构：

```text
Native App
  ├─ WebView 登录页
  │   └─ 用户登录小红书网页版
  ├─ WebViewSyncWorker
  │   ├─ 复用 WebView CookieStore
  │   ├─ 检查 a1 cookie
  │   ├─ 加载小红书网页签名环境
  │   ├─ 在 WebView 中 fetch 收藏接口
  │   └─ JSBridge 回传 notes/cursor/has_more
  ├─ 本地数据库
  ├─ 分类模块
  └─ UI 展示层
```

核心原则：

- 登录在 App 自己的 WebView 中完成。
- 请求也尽量在 WebView 中完成。
- Native 不强依赖直接拿 cookie 发请求。
- Native 只接收必要结果，例如 `note_id`、标题、封面、文案、cursor。

### 7.2 后台同步限制

可以在 App 前台但 UI 不可见时，用隐藏 WebView 同步。

适合的触发时机：

- App 启动。
- App 回到前台。
- 用户手动点击同步。
- 用户进入收藏页。

不建议依赖每天凌晨后台静默同步。

原因：

- iOS 后台执行 `WKWebView` JS 不可靠。
- `BGTaskScheduler` 不保证准时，也不适合跑复杂 WebView 分页。
- Android `WorkManager` 可以做后台任务，但后台 WebView 受系统、电池优化、厂商 ROM 影响较大。
- Android 前台服务可以提高成功率，但必须显示通知，不适合“隐蔽”。

推荐策略：

```text
前台隐式同步 + 手动同步 + 尽力而为的后台补充
```

不要把核心体验建立在后台定时任务上。

## 8. 剪切板分享链接解析方案

除了登录后批量读取收藏，也讨论过一个更轻量的方案：

```text
用户复制小红书分享链接
  -> App 检测剪切板中有小红书链接
  -> 明示提示用户是否解析
  -> 打开 WebView 解析分享页
  -> 获取单条笔记内容
```

优点：

- 不需要登录。
- 用户主动复制链接，合规感更好。
- 适合导入少量公开笔记。

限制：

- 只能处理公开可访问的笔记。
- 私密、删除、仅 App 可见、需要登录的笔记不一定能拿到。
- 分享页可能只提供摘要，完整内容仍可能需要 Web API。

不建议静默读取剪切板。产品上应明确提示：

```text
检测到剪切板中有小红书链接，是否解析？
```

## 9. 分类与 embedding/LLM 方案

目标是根据小红书笔记内容自动分类。

可使用字段：

- 标题
- 正文文案
- 标签
- 作者
- 图片 OCR 结果（后续）
- 用户手动修正历史

### 9.1 LLM 分类

MVP 推荐先用 LLM 分类。

输入：

```text
标题 + 正文 + 标签
```

输出：

```text
category
confidence
reason
```

默认分类可包含：

```text
旅游、美食、做饭、礼物、穿搭、学习、装修、摄影、运动、工具、其他
```

### 9.2 Embedding 用途

Embedding 更适合：

- 语义搜索。
- 相似笔记推荐。
- 聚类发现新分类。
- 分类描述与笔记内容做相似度匹配。

单靠 embedding 不会天然知道“旅游、美食”等分类，需要提供分类描述或用 LLM 生成标签。

## 10. 本地数据模型建议

建议使用本地 SQLite。

### notes

保存笔记基础信息：

```text
id
title
desc
author
cover
url
raw_json
created_at
updated_at
synced_at
```

### categories

保存分类：

```text
id
name
description
sort_order
cover_note_ids
created_at
updated_at
```

### note_categories

保存笔记和分类关系：

```text
note_id
category_id
score
source       # auto/manual
created_at
updated_at
```

### sync_state

保存同步状态：

```text
source
cursor
has_more
last_sync_at
last_full_sync_at
```

### embeddings

保存向量：

```text
note_id
embedding
model
updated_at
```

## 11. 移动端 UI 方向

### 11.1 分类 Tab

目标风格：

- 轻量。
- 留白大。
- 私人知识库感。
- 参考 mymind 的大搜索框。
- 参考 Pinterest Boards / Cosmos 的视觉化收藏集合。

页面结构：

```text
顶部：大搜索框
下方：分类数量 + 同步按钮
下方：分类列表
```

分类行结构：

```text
[图片堆叠]  分类名称
             笔记 · N
                         [笔形编辑 icon]
```

图片堆叠：

- 使用 3-4 张该分类下的代表封面。
- 图片轻微错位、重叠，有照片堆叠感。
- 不做普通四宫格。
- 左侧尺寸固定，避免列表跳动。

分类行交互：

- 点击整行进入分类详情页。
- 点击笔 icon 编辑分类名称、封面或分类规则。

顶部同步按钮：

- 放在“分类 · N”同一行右侧。
- 使用轻量刷新 icon + “同步”。
- 状态：
  - 默认：显示刷新 icon + “同步”。
  - 同步中：icon 旋转，文字变成“同步中”。
  - 同步完成：短暂显示“已更新”。
  - 有新增：可显示轻提示，例如 `+12`。

### 11.2 分类详情页

结构：

```text
分类名称
笔记数量
笔记预览列表
```

笔记预览条目：

- 封面。
- 标题。
- 作者。
- 摘要。
- 标签。
- 原始小红书链接入口。

### 11.3 搜索

搜索应支持：

- 标题搜索。
- 文案搜索。
- 标签搜索。
- 分类搜索。
- 后续支持 embedding 语义搜索。

## 12. Figma Make 与开发

已经讨论过：

- `.make` 是 Figma Make 生成的原型/应用项目格式。
- `.fig` 是普通 Figma Design 文件格式。
- 个人项目可以直接用 `.make` 进入开发阶段。
- `.make` 适合确定页面结构、交互和视觉方向。
- 真正开发时不建议完全照搬 Make 生成代码，而是整理为清晰组件。

建议组件拆分：

```text
SearchHeader
CategoryRow
ImageStack
SyncButton
NotePreviewItem
EditCategorySheet
CategoryDetailPage
```

## 13. 合规与风险

需要注意：

- 当前小红书收藏读取方案不是官方开放 API。
- 依赖小红书 Web 接口、cookie 和网页签名环境。
- 接口、签名函数、参数、风控策略都可能变化。
- 应用应明确告知用户会读取其小红书收藏。
- 应提供退出登录、清除本地数据、关闭自动同步能力。
- 不绕过验证码或账号安全机制。
- 不读取其他 App 私有数据。
- 不静默读取剪切板。

## 14. MVP 建议

第一版建议做：

1. App 内 WebView 登录小红书网页版。
2. 检查 `a1` cookie 和当前 `user_id`。
3. 获取收藏列表 note_id、标题、作者、封面。
4. 支持分页加载全部收藏。
5. 本地 SQLite 存储。
6. 使用 LLM 做基础分类。
7. 分类 tab 展示分类列表。
8. 分类详情页展示笔记列表。
9. 支持手动同步。
10. 支持编辑分类名称。

第二版再做：

- 获取更完整的笔记详情。
- embedding 语义搜索。
- 相似笔记推荐。
- 剪切板链接导入。
- 自动同步策略。
- 导出 Markdown / Obsidian。

## 15. 当前最重要的技术验证点

移动端正式开发前，需要先验证：

1. Android WebView 中能否稳定登录小红书网页版。
2. 登录后能否读取或复用 `a1` cookie。
3. WebView 中是否能加载出网页签名环境。
4. WebView 内 fetch 收藏接口是否能返回 `cursor/has_more/notes`。
5. 隐藏 WebView 在 App 前台是否能稳定分页拉取。
6. 小红书是否对移动 WebView 有额外风控。

这些验证通过后，再进入正式 Android MVP 开发。
