# 集示

集示是一个动态规划中的收藏内容管理 App，当前设定的核心方向是帮助用户整理、分类和展示自己的收藏内容。

「集示」这个名字有两层含义：

- **集**：表示 collection，代表收藏、收集、聚合。
- **示**：表示展示，把收藏内容重新组织后清晰呈现出来。

同时，「集示」也借用了「集市」的谐音，表达这是一个由多个收藏夹、分类和主题组成的个人内容合集。用户可以像逛集市一样浏览自己的收藏，从不同分类中重新发现有价值的笔记、灵感和资料。

当前项目重点围绕小红书收藏内容展开：

- 获取用户收藏笔记。
- 按文案、标题、标签和封面进行分类。
- 使用 LLM 或 embedding 模型辅助整理。
- 在移动端以分类列表、笔记预览和搜索的形式展示。

项目仍处于探索和规划阶段，具体能力、交互和技术实现会随着验证结果持续调整。

## 初始化项目

### 环境要求

- Android Studio，建议使用当前稳定版。
- JDK 17。
- Android SDK Platform 35。
- Android SDK Build-Tools 和 Platform-Tools。

### 安装依赖

本项目使用 Gradle Wrapper 管理 Gradle 版本，依赖会在首次同步或编译时自动下载到本机 Gradle 缓存，不需要把依赖缓存提交到仓库。

1. 克隆仓库：

```bash
git clone git@github.com:oneFatEn/show_collection_Android.git
cd show_collection_Android
```

2. 用 Android Studio 打开项目根目录，并等待 Gradle Sync 完成。

3. 如果使用命令行，先确认 `JAVA_HOME` 指向 JDK 17，然后执行：

```bash
./gradlew :app:assembleDebug
```

4. 安装到已连接设备或模拟器：

```bash
./gradlew :app:installDebug
```

### 本地配置

Android Studio 通常会自动生成 `local.properties`，其中包含本机 Android SDK 路径，例如：

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

该文件包含本机路径信息，已通过 `.gitignore` 排除，不应提交。

### 不提交的文件

仓库根目录的 `.gitignore` 会排除：

- 本地配置：`local.properties`、`.env*`。
- 依赖和构建缓存：`.gradle/`、`build/`、`**/build/`。
- IDE 本地状态：`.idea/`、`*.iml`。
- 敏感文件：签名证书、keystore、`google-services.json`、`secrets.properties`。
- 调试和崩溃文件：`*.log`、`*.hprof`、`hs_err_pid*`。

## 当前 MVP

当前仓库已经包含 Android MVP 工程：

- Kotlin + Jetpack Compose。
- WebView 小红书登录入口。
- Android SQLite 本地核心存储。
- 笔记 ID、分类关系和轻量展示缓存分离。
- 规则分类 + 「待整理」兜底。
- 批量待确认分类。
- 设置页支持清除登录态、展示缓存、本地索引和分类关系。

运行方式见 [RUNNING.md](RUNNING.md)。
