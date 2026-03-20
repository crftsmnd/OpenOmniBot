# OpenOmniBot 🤖

OpenOmniBot 是一个基于 Android 原生与 Flutter 混合架构的智能机器人助手应用。它利用端侧 AI 智能模型能力，结合无障碍服务和状态机管理系统，通过学习模式、陪伴模式等为用户提供自动化的操作和智能辅助体验。

## 🏗️ 架构图

![项目架构图](architecture_diagram.svg)

## 🚀 快速开始

### 环境要求

- Android Studio (推荐最新版)
- Flutter SDK (3.9.2+)
- JDK 11+
- Kotlin & Android SDK (Min SDK: 30, Target SDK: 34)

### 📦 项目获取

本项目依赖于独立的 OmniIntelligence 模型库模块，请一并克隆并导入：

```bash
git clone https://github.com/omnimind-ai/OpenOmniBot
# 克隆模型通讯库
git clone https://github.com/omnimind-ai/OpenOmniBot
cd OpenOmniBot
```

使用 Android Studio 打开 `OpenOmniBot` 项目，并通过 `import module` 的方式，将 `OmniIntelligence` `OmniIntelligence` 目导入到当前项目中。

### ▶️ 编译与运行

项目提供 `develop` 与 `production` 两个变体环境 (Flavors)。开发期建议使用 `develop`：

```bash
# 全量构建
./gradlew build

# 构建并安装开发版 Debug APK
./gradlew assembleDevelopDebug
./gradlew installDevelopDebug
```

**Flutter 模块说明**：
Flutter UI 作为 Android 原生的 AAR 嵌套其中。如遇到 Flutter 层构建报错：
```bash
cd ui
flutter clean
flutter pub get
# 之后再尝试重新同步并构建原生工程
```

### ⚙️ 开源版配置说明

- 开源主线默认不绑定官方后端，`gradle.properties` 中使用 `OMNIBOT_BASE_URL=`（留空即禁用后端依赖）。
- 如需接入自建后端，可在本地 `~/.gradle/gradle.properties` 增加如下配置：

```properties
OMNIBOT_BASE_URL=https://your-self-hosted-api.example.com
```

- Release 构建要求签名文件，需在 CI 或本地配置中注入对应属性参数：
  - `OMNI_RELEASE_STORE_FILE`
  - `OMNI_RELEASE_STORE_PWD`
  - `OMNI_RELEASE_KEY_ALIAS`
  - `OMNI_RELEASE_KEY_PWD`

### ✅ Commit 前快速检查（推荐）

为避免每次提交都跑全量 `lint`，仓库提供了轻量级 `pre-commit` hook（秒级）：

```bash
./scripts/setup-git-hooks.sh
```

该检查会在 commit 前执行：
- 拦截 `.jks/.keystore` 被提交
- 拦截已禁用的商业残留常量/域名/AMap key 字符串
- 提交包含 Kotlin/Java/Gradle 变更时，执行 `:app:compileDevelopDebugKotlin`
- 仅对本次提交涉及的 Dart 文件执行 `flutter analyze --no-fatal-warnings`

## 📁 目录结构

项目采用基于多模块的演进式组件化架构：

```
OpenOmniBot/
├── app/                 # 主应用程序模块 (App入口)
├── ui/                  # Flutter 跨平台 UI 模块 (通过 Riverpod 管理状态)
├── baselib/             # 基础核心库模块 (数据库、网络、鉴权、基建)
├── assists/             # 操作引擎与状态机功能 (任务管理系统)
├── omniintelligence/    # 端侧核心智能模型层 (由外部仓库依赖导入)
├── overlay/             # 浮窗覆盖层相关功能实现
├── accessibility/       # Android UI无障碍捕捉服务
└── testbot/             # 仅用于开发风味的测试挂载工具（可选）
```

## 🧩 核心架构与原理

### 1. 任务流与状态机机制 (State Machine)
- **调度中枢**：位于 [assists/StateMachine.kt](assists/src/main/java/cn/com/omnimind/assists/StateMachine.kt)。 
- **任务模型**：统一管理陪伴模式 (`Companion`)、学习模式 (`Learning`)、自动化定时任务 (`Scheduled`) 的运行生命周期。
- **后台执行保障**：使用 Kotlin 协程调度实现系统级挂载后台服务的平滑切换运转。

### 2. 混合路由与交互 (Flutter-Native Embedding)
- 利用 `FlutterEngineGroup` 高效内嵌 Flutter 独立 UI 视图与动画弹窗。
- 业务界面和视觉交互由单独的 [ui](ui/) 层托管处理。
- 业务与底层的通信通过在 [AssistsCore.kt](assists/src/main/java/cn/com/omnimind/assists/AssistsCore.kt) 封装的 Channel 互通。

### 3. 数据层持久化管理 (Data Storage)
- 依赖于 Room Database 进行底层本地历史管理：
  - [ConversationDao](baselib/src/main/java/cn/com/omnimind/baselib/database/ConversationDao.kt): 对话与场景分片
  - [MessageDao](baselib/src/main/java/cn/com/omnimind/baselib/database/MessageDao.kt): 历史任务指令留存
- 对非结构简易配置使用 MMKV 实线高性能轻量级存储。

### 4. 自动化视觉流 (Accessibility & Overlay)
- `accessibility` 持续监听设备视图结构，用于提取自动化动作执行时的树形 UI 解析节点。
- `overlay` 通过悬浮模式承载系统的常驻状态及执行反馈弹窗提示。

### 5. 跨界前沿技术集成支持
- **微信鉴权互联**: 集成用于特定渠道的免密登录模块。
- **ML Kit**: 支持原生的端区截屏OCR图像特征挖掘与文字分析。
- **外部 MCP 融合**: 内置 Model Context Protocol，支持与周边扩展端进行通信能力增强。

## 🛠️ 核心开发技术栈

- **原生模块开发**: Kotlin, Coroutines/Flow, Room
- **跨平台视窗开发**: Flutter 3.9+, Riverpod, Go Router, Material 3 
- **底层架构交互**: JNI/NDK, MethodChannel, MMKV
