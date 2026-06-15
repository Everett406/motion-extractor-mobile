# Motion Extractor Mobile — 架构说明

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                           │
│  MainActivity (ComponentActivity)                                     │
│       │                                                             │
│       ▼                                                             │
│  MotionExtractorTheme (CupertinoTheme + darkColorScheme)              │
│       │                                                             │
│       ▼                                                             │
│  MainScreen — 状态持有者 (UiState)                                    │
│       │                                                             │
│       ├── PreviewArea  ── Image / ExoPlayer PlayerView              │
│       ├── ActionBar    ── 选择/预览/导出/保存/播放                    │
│       └── ParameterPanel ── 预设、输出模式、可折叠参数分组             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼  UiState.toMotionExtractParams()
┌─────────────────────────────────────────────────────────────────────┐
│                        Processing Layer                             │
│  VideoProcessor                                                       │
│       ├── getVideoInfo(uri)                                           │
│       ├── generatePreviewFrames(uri, params) → List<Mat>              │
│       └── exportVideo(uri, params, onProgress) → File                 │
│                           │                                         │
│                           ▼                                         │
│  VideoDecoder ── MediaExtractor + MediaCodec → BGR Mat                │
│       │                                                             │
│       ▼                                                             │
│  MotionExtractor.processFrame(current, offset, params) → BGR Mat      │
│       │                                                             │
│       ▼                                                             │
│  VideoEncoder ── MediaCodec + MediaMuxer → MP4                        │
└─────────────────────────────────────────────────────────────────────┘
```

## 2. 模块说明

### `app` 模块

主应用模块，包含 UI、业务逻辑和视频处理。

### `:opencv` 模块（可选）

由 `scripts/setup-opencv.sh` 在构建时动态创建：
- 源码：`opencv-sdk/OpenCV-android-sdk/sdk/java`
- Gradle 模块目录：`opencv/sdk/`
- `settings.gradle.kts` 仅在 `opencv/sdk` 存在时 `include(":opencv")`
- `app/build.gradle.kts` 中条件依赖 `project(":opencv")`

### Native 库

- `libopencv_java4.so`：OpenCV 4.10.0 预编译库（arm64-v8a、armeabi-v7a、x86、x86_64）
- `libc++_shared.so`：NDK C++ 运行时，由 `setup-opencv.sh` 从 `$ANDROID_NDK_HOME` 复制

## 3. UI 层

### 主题

- `Theme.kt`：`MotionExtractorTheme` 使用 `CupertinoTheme(colorScheme = darkColorScheme())`，强制暗色。
- `Color.kt`：定义暗色调色板，包括毛玻璃色调 `GlassTint`。

### 状态管理

- 当前状态全部集中在 `UiState`（`UiState.kt`）。
- `MainScreen` 通过 `remember { mutableStateOf(UiState()) }` 持有状态。
- **待改进**：抽到 `ViewModel`，以便在配置变更（旋转）时保留状态并简化测试。

### 关键 Composable

| 文件 | 职责 |
|------|------|
| `MainScreen.kt` | 整体布局、视频信息加载、预览/导出协程、Haze 背景 |
| `PreviewArea.kt` | 单帧/多帧 Bitmap 展示或 ExoPlayer 播放 |
| `ActionBar.kt` | 选择视频、生成预览、导出、保存、播放按钮 |
| `ParameterPanel.kt` | 预设、输出模式、基础/增强/RGB 参数 |
| `UiState.kt` | 所有 UI 状态和 `toMotionExtractParams()` 映射 |
| `ExoPlayerManager.kt` | ExoPlayer 创建、URI 设置、`LocalExoPlayer` CompositionLocal |

### 自定义组件

- `CupertinoCapsuleButton`：圆角胶囊按钮，用于预设。
- `ParameterSection`：可折叠参数分组。
- `SegmentedControl`：三段式选择器，用于输出模式。

## 4. 处理层

### VideoDecoder

- 使用 `MediaExtractor` 读取视频轨道。
- 使用 `MediaCodec` 解码为 `Image`（YUV_420_888）。
- 通过 `Yuv420Converter.imageToBgrMat()` 转为 OpenCV 的 BGR `Mat`。
- 暴露 `width`、`height`、`fps`、`frameCount`、`rotationDegrees`。

### MotionExtractor

核心算法，单帧处理流程：

1. **预模糊**：对当前帧和偏移帧做高斯模糊（可选）。
2. **反色偏移帧**：`255 - offset`（可选）。
3. **混合**：`result = (1 - opacity) * current + opacity * invertedOffset`。
4. **RGB 通道分离**：为 R/G/B 分别使用不同偏移帧计算运动幅度。
5. **对比度/亮度**：减去 0.5、乘对比度、加 0.5 + 亮度，再 clamp。
6. **输出模式**：彩色 / 灰度 / 反色。
7. **发光效果**：根据运动幅度生成高斯模糊光晕叠加。
8. **转回 8-bit BGR**。

### VideoEncoder

- 选择 AVC 编码器支持的 YUV420 颜色格式。
- 优先 `COLOR_FormatYUV420Planar`，回退 `COLOR_FormatYUV420Flexible`。
- `Yuv420Converter.matToI420()` 把 BGR Mat 转成 I420 byte buffer。
- 使用 `MediaMuxer` 写入 MP4，并设置 `orientationHint`。

### VideoProcessor

- 协调 `VideoDecoder`、`MotionExtractor`、`VideoEncoder`。
- 维护一个 `ArrayDeque<Mat>` 作为 look-ahead 缓冲区。
- 根据 `offsetFrames` 和 `rgbOffsets` 计算最大需要的缓冲区大小。

## 5. 数据流

```
用户点击 "选择视频"
    │
    ▼
MainActivity.launch("video/*") → selectedUri
    │
    ▼
MainScreen.LaunchedEffect(selectedUri)
    │
    ▼
videoProcessor.getVideoInfo(uri) → VideoInfoSummary
    │
    ▼
用户调节参数 / 点击预设
    │
    ▼
UiState 更新 → recompose
    │
    ▼
用户点击 "生成预览"
    │
    ▼
videoProcessor.generatePreviewFrames(uri, params)
    │
    ▼
Mat → rotateMat → Bitmap → previewBitmaps
    │
    ▼
PreviewArea 显示单帧或多帧拼贴
    │
    ▼
用户点击 "导出视频"
    │
    ▼
videoProcessor.exportVideo(uri, params, onProgress)
    │
    ▼
输出到 cacheDir/motion_extract_<ts>.mp4
    │
    ▼
用户点击 "播放" → ExoPlayer 加载 FileProvider URI 播放
用户点击 "保存" → MainActivity.saveToGallery 写入 MediaStore
```

## 6. 构建与依赖

### Gradle 关键配置

```kotlin
android {
    compileSdk = 34
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.13" }
}
```

### 主要依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Compose BOM | 2024.04.00 | UI 框架 |
| activity-compose | 1.8.2 | ComponentActivity setContent |
| lifecycle-runtime-compose | 2.7.0 | Compose 生命周期工具 |
| cupertino | 0.1.0-alpha04 | iOS 风格组件 |
| cupertino-icons-extended | 0.1.0-alpha04 | iOS 风格图标 |
| haze | 0.7.0 | 毛玻璃模糊 |
| media3-exoplayer | 1.3.1 | 视频播放 |
| media3-ui | 1.3.1 | PlayerView |
| OpenCV Android SDK | 4.10.0 | 图像处理 |

## 7. CI / CD

`.github/workflows/build.yml`：

1. 检出代码
2. 安装 JDK 17、Android SDK、NDK 26.1.10909125
3. 缓存 Gradle 和 OpenCV SDK
4. 运行 `scripts/setup-opencv.sh`
5. 解码签名 keystore
6. `./gradlew assembleRelease`
7. 上传 artifact
8. 创建 GitHub Release

## 8. 已知架构债务

- `UiState` 与 `MainScreen` 耦合过重，建议抽到 `ViewModel`。
- 视频方向在解码、编码、播放三处分别处理，需统一。
- 权限申请逻辑分散，建议集中到单一权限管理类。
- 缺少单元测试和 UI 测试。

## 9. 扩展建议

- 增加 `ViewModel` 后，可轻松支持配置变更、导出后台运行、状态持久化。
- 如未来支持实时相机预览，可复用 `MotionExtractor.processFrame`，替换 `VideoDecoder` 为 Camera2/ImageReader。
- 如需要多种输出格式（GIF、WebM），可替换 `VideoEncoder` 中的 muxer/codec。
