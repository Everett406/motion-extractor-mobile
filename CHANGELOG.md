# Motion Extractor Mobile — 变更日志

> 按时间倒序排列，记录主要变更、决策和状态。

## [Unreleased] — 2026-06-15

### 迁移到 Jetpack Compose + iOS Cupertino + Haze

- **UI 框架迁移**
  - 删除 `activity_main.xml`，全面使用 Jetpack Compose。
  - `MainActivity` 从 `AppCompatActivity` 改为 `ComponentActivity`。
  - 新增 `MotionExtractorTheme`，基于 `CupertinoTheme` 强制暗色。

- **新增 Compose UI 组件**
  - `MainScreen`：主屏幕、Haze 背景、状态管理、预览/导出协程。
  - `ParameterPanel`：预设胶囊、输出模式分段器、可折叠参数分组。
  - `PreviewArea`：单帧/多帧 Bitmap 预览或 ExoPlayer 播放。
  - `ActionBar`：选择视频、生成预览、导出、保存、播放按钮。
  - `UiState`：所有 UI 状态和 `toMotionExtractParams()` 转换。
  - `ExoPlayerManager`：ExoPlayer 创建与 `LocalExoPlayer` CompositionLocal。

- **依赖更新**
  - 增加 `androidx.compose:compose-bom:2024.04.00`
  - 增加 `androidx.activity:activity-compose:1.8.2`
  - 增加 `androidx.lifecycle:lifecycle-runtime-compose:2.7.0`
  - 增加 `io.github.alexzhirkevich:cupertino:0.1.0-alpha04`
  - 增加 `io.github.alexzhirkevich:cupertino-icons-extended:0.1.0-alpha04`
  - 增加 `dev.chrisbanes.haze:haze:0.7.0`
  - 增加 `androidx.media3:media3-exoplayer:1.3.1`
  - 增加 `androidx.media3:media3-ui:1.3.1`
  - 移除 Material3 相关依赖

- **已知问题**
  - Haze 模糊位置不正确，整体 UI 排版较乱。
  - 预设胶囊未显示选中状态。
  - 导出进度只有文字，无进度条和取消按钮。
  - 视频方向处理在预览/导出/播放三处可能不一致。
  - Android 13+ 媒体权限未动态申请。
  - 详见 [KNOWN_ISSUES.md](./KNOWN_ISSUES.md)。

- **文档**
  - 新增 [HANDOFF.md](./HANDOFF.md)、[KNOWN_ISSUES.md](./KNOWN_ISSUES.md)、[TODO.md](./TODO.md)、[ARCHITECTURE.md](./ARCHITECTURE.md)、[CHANGELOG.md](./CHANGELOG.md)。

## 1.4 — 2026-06-14

### 修复与优化

- 修复 OpenCV Java 旋转常量名称错误（`Core.ROTATE_90_COUNTER_CLOCKWISE` 改为 `Core.ROTATE_90_COUNTERCLOCKWISE`）。
- 修复竖屏视频旋转、应用内播放、可折叠参数面板。
- 优化 YUV 转换速度。
- 版本号升级到 `1.4`（versionCode 5）。

## 1.3 — 2026-06-13

### 功能增强

- 增加 RGB 通道分离拖影（彩虹尾迹）。
- 增加一键预设：经典、霓虹、高对比、柔和。
- 优化 YUV 转换。
- 预览支持多张帧拼贴。

## 1.2 — 2026-06-12

### 视频编码改进

- 使用 YUV 输入替代 Surface 作为 MediaCodec encoder 输入，提升兼容性。
- 替换 OpenCV video I/O 为 Android MediaCodec。

## 1.1 — 2026-06-11

### OpenCV 与构建改进

- 自动从 NDK 复制 `libc++_shared.so` 以支持 OpenCV 运行时。
- 增加错误报告器，支持复制错误日志。
- 改进 OpenCV 初始化诊断。
- Release APK 通过 GitHub Actions 自动签名并发布。

## 1.0 — 2026-06-10

### 初始版本

- 从桌面版 [motion-extractor-app](https://github.com/Everett406/motion-extractor-app) 复刻核心算法。
- Android XML + Material3 界面。
- 支持从相册选择视频、调节参数、生成预览、导出 MP4。
