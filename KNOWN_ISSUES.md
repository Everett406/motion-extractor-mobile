# Motion Extractor Mobile — 已知问题清单

> 按严重程度和模块分类，便于交接后快速定位。

## UI / UX

### 1. Haze 毛玻璃效果位置错误（P0）
- **位置**：`app/src/main/java/com/everett/motionextractor/ui/MainScreen.kt`
- **现象**：整个页面背景被模糊，参数面板也看起来脏/乱，不像 iOS 控制中心的毛玻璃。
- **根因**：
  - `Box(Modifier.fillMaxSize().haze(...))` 给整个滚动容器加了模糊。
  - `ParameterPanel` 的 `hazeChild` 形状和层级没有设计好，下方缺少需要被模糊的内容。
- **建议修复**：
  - 背景使用纯色或轻微渐变（`Color.Black` 或 `Color(0xFF050505)`）。
  - 仅对底部浮动的参数面板加 `hazeChild()`，并确保面板下方有图片/视频内容作为模糊源。
  - 参考 Haze 官方示例：上层 `haze()` 作用在背景层，下层 `hazeChild()` 作用在浮层。

### 2. 整体排版混乱（P0）
- **位置**：`MainScreen.kt`
- **现象**：所有内容从上到下堆叠，参数面板极长；横屏/竖屏视频都挤在 16:9 的预览框里。
- **建议修复**：
  - 预览区改为根据视频方向自适应宽高比（竖屏视频用 9:16 或按视频比例）。
  - 参数面板可考虑用底部 Sheet 或独立页面，而不是展开式列表。
  - 操作按钮分组更明显：主操作（选择/导出）、二级操作（保存/播放）。

### 3. 预设胶囊没有选中状态（P0）
- **位置**：`ParameterPanel.kt` 第 39-45 行
- **现象**：点击预设后没有视觉反馈，用户不知道当前生效的是哪个预设。
- **根因**：`CupertinoCapsuleButton(selected = false)` 硬编码。
- **建议修复**：把当前预设记录到 `UiState`，根据 `state.currentPreset == preset` 传 `selected`。

### 4. 可折叠参数分组交互差（P1）
- **位置**：`ParameterSection.kt`
- **现象**：展开/折叠只有文字箭头，没有动画，点击区域反馈弱。
- **建议修复**：
  - 使用 `Cupertino` 自带的 `Section` / `Form` 组件（如果稳定）。
  - 或加 `AnimatedVisibility` + 背景色变化。

### 5. 导出进度只有文字（P1）
- **位置**：`MainScreen.kt` 第 203-209 行
- **现象**：导出时只显示“导出进度: XX%”，没有进度条，无法取消。
- **建议修复**：
  - 增加 `CupertinoLinearProgressIndicator` 或自定义进度条。
  - 导出协程保存 `Job` 引用，提供取消按钮。

### 6. PlayerView 重组时可能闪烁（P1）
- **位置**：`PreviewArea.kt`
- **现象**：从预览图切换到播放时，PlayerView 可能重建。
- **根因**：`AndroidView` 的 `factory` 在 `showPlayer` 变化时会重新创建 `PlayerView`。
- **建议修复**：
  - 让 PlayerView 始终存在但 `visibility` / `alpha` 切换。
  - 或使用 `remember` 保存 PlayerView 实例。

### 7. 状态栏/导航栏未适配（P2）
- **位置**：`themes.xml`、`MainScreen.kt`
- **现象**：需验证暗色状态栏图标、底部手势导航条侵入、刘海屏安全区。
- **建议修复**：使用 `WindowInsets` + `CupertinoScaffold` 的 `contentWindowInsets`。

## 功能 / 算法

### 8. 视频方向处理不一致（P1）
- **位置**：`MainScreen.kt`、`VideoProcessor.kt`、`VideoEncoder.kt`
- **现象**：
  - 预览帧通过 `rotateMat` 手动旋转显示。
  - 导出视频通过 `muxer.setOrientationHint` 写 metadata。
  - ExoPlayer 播放时可能不识别该 metadata，导致方向与预览不一致。
- **建议修复**：
  - 统一方向处理：在解码时把 Mat 旋转到 0°，编码时直接按竖/横尺寸写入，不再依赖 metadata。
  - 或在导出时把旋转角度也传给 `VideoProcessor`，让输出帧本身就是正确方向。

### 9. Android 13+ 媒体权限未动态申请（P1）
- **位置**：`MainActivity.kt`、`AndroidManifest.xml`
- **现象**：Manifest 声明了 `READ_MEDIA_VIDEO`，但代码里只申请了 `WRITE_EXTERNAL_STORAGE`。
- **建议修复**：
  - Android 13+ 启动时请求 `READ_MEDIA_VIDEO`。
  - Android 10-12 请求 `READ_EXTERNAL_STORAGE`。
  - Android 9 及以下请求 `WRITE_EXTERNAL_STORAGE`。

### 10. 保存到相册异常处理简单（P1）
- **位置**：`MainActivity.kt` `saveToGallery`
- **现象**：只 catch 了 Exception，没有区分网络/权限/磁盘满等情况。
- **建议修复**：区分 `SecurityException`（权限）、`IOException`（磁盘）并给出具体提示。

### 11. RGB 偏移逻辑与 UI 描述不一致（P2）
- **位置**：`MotionExtractor.kt`、`ParameterPanel.kt`
- **现象**：
  - UI 中 R/G/B 偏移范围 0-30， enabled 仅受 `useRgbOffsets` 控制。
  - 算法中偏移值 `>0` 才启用通道分离，否则 fallback 到普通结果通道。
- **建议修复**：统一语义，或让 UI 最小值从 1 开始。

### 12. OpenCV 初始化失败未阻止后续操作（P2）
- **位置**：`MainActivity.kt`
- **现象**：`initializeOpenCV()` 失败后弹 dialog，但界面按钮仍然可点，点击后会崩溃。
- **建议修复**：OpenCV 未就绪时禁用“生成预览/导出视频”按钮，或显示全局错误占位页。

### 13. 高斯模糊可能过强（P2）
- **位置**：`MotionExtractor.kt` 第 77-79 行
- **现象**：模糊同时作用于当前帧和偏移帧，可能导致预览非常糊。
- **建议修复**：仅对偏移帧做预模糊，或把当前帧的模糊改为可选后处理。

## 性能 / 稳定性

### 14. 预览 Bitmap 在主线程创建（P2）
- **位置**：`MainScreen.kt` 第 154-161 行
- **现象**：`withContext(Dispatchers.IO)` 只负责生成 Mat，Bitmap 创建和 `matToBitmap` 在主线程。
- **建议修复**：把整个 `frames.map { ... }` 放到 `Dispatchers.Default` 或 `Dispatchers.IO`，返回 List<Bitmap> 后再更新 UI。

### 15. ExoPlayer 未处理音频焦点和生命周期（P2）
- **位置**：`MainActivity.kt`、`ExoPlayerManager.kt`
- **现象**：Activity 进入后台继续播放，无暂停/恢复逻辑。
- **建议修复**：在 `onPause`/`onResume`/`onStop`/`onStart` 中管理 `playWhenReady`。

### 16. 导出协程未绑定生命周期（P2）
- **位置**：`MainScreen.kt` `lifecycleScope.launch`
- **现象**：`lifecycleScope` 在 Activity 销毁时自动取消，但如果用户在导出时退出 App，临时输出文件可能残留。
- **建议修复**：在 `onDestroy` 中清理 `cacheDir` 下未保存的 `motion_extract_*.mp4`。

## 构建 / 环境

### 17. 本地 NDK 缺失导致 debug APK 不完整（P2）
- **位置**：`scripts/setup-opencv.sh`
- **现象**：本地 `C:/Users/s0605/AppData/Local/Android/Sdk/ndk/26.1.10909125` 为空，脚本提示未找到 NDK。
- **影响**：本地 debug APK 缺少 `libc++_shared.so`，部分真机无法启动 OpenCV。
- **建议修复**：安装 NDK 或直接使用 CI 产出的 Release APK 测试。

### 18. Compose BOM 与 Cupertino alpha 兼容性风险（P2）
- **位置**：`app/build.gradle.kts`
- **现象**：`cupertino 0.1.0-alpha04` 是 alpha 版本，后续 API 可能变化。
- **建议修复**：升级到稳定版后再大规模重构；若长期 alpha，可考虑 Material3 或完全自定义组件。
