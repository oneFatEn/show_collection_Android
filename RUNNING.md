# MVP 编译运行说明

## 当前状态

本仓库已经包含一个 Android MVP 工程：

- Kotlin + Jetpack Compose。
- WebView 小红书登录入口。
- 本地 SQLite 核心存储。
- 轻量展示缓存。
- 规则分类 + 待整理兜底。
- 批量待确认分类。
- 设置页数据清理入口。

当前 MVP 的首页同步入口已接入真实收藏同步尝试。登录态确认后，在首页点击「同步」，App 会挂载隐藏 WebView，在小红书页面上下文中请求当前用户和收藏分页，并通过 JSBridge 把收藏 JSON 传给 Native：

```text
window.JiShiBridge.postFavoritesJson(json)
```

如果小红书接口要求额外签名头导致失败，页面会显示接口错误；这时需要继续接入网页签名环境。

## Android Studio 运行

1. 用 Android Studio 打开项目根目录：

```text
/Users/hukeming.5/my_coding/show_collection_Android
```

2. 等待 Gradle Sync 完成。

3. 安装 Android SDK Platform 35、Build-Tools、Platform-Tools。

4. 连接真机或创建模拟器。

5. 选择 `app` 配置并点击 Run。

## 命令行编译

本仓库已包含 Gradle Wrapper。首次执行会自动下载项目声明的 Gradle 版本和 Android/Kotlin 依赖：

```bash
./gradlew assembleDebug
```

生成 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装：

```bash
./gradlew installDebug
```

## MVP 验证流程

```text
打开 App
  -> 点击“同步”
  -> 如果未登录，进入“登录”页在 WebView 内完成小红书登录
  -> 返回首页再次点击“同步”
  -> 首页出现分类和数量
  -> 进入分类查看卡片
  -> 回到首页，进入“待确认分类”
  -> 创建新分类或放入待整理
  -> 设置页测试清理登录态/缓存/本地索引
```

## 当前限制

- 还没有接入真实小红书收藏接口脚本。
- 本地存储 MVP 暂用 Android SQLiteOpenHelper，后续可以按 `spec/001-rednote-collection-app/plan.md` 迁移 Room。
