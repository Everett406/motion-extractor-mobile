# Motion Extractor Mobile — 后续任务清单

> 优先级从高到低排列。建议按批次处理，不要一次改太多。

## P0 — 必须先修（当前版本不可用/体验很差）

- [ ] **重新设计 Haze 毛玻璃用法和整体布局**
  - 参考 iOS 控制中心/锁屏音乐卡片：背景清晰，浮层才有毛玻璃。
  - 背景改为纯色或渐变；仅参数面板/操作浮层使用 `hazeChild()`。
  - 确保 `haze()` 作用在模糊源层，`hazeChild()` 作用在浮层。

- [ ] **修复预设胶囊选中状态**
  - 在 `UiState` 中增加 `currentPreset: Preset?`。
  - `CupertinoCapsuleButton(selected = state.currentPreset == preset)`。

- [ ] **优化整体排版**
  - 预览区根据视频方向自适应比例（竖屏视频不要硬塞 16:9）。
  - 操作按钮分组：主操作一行、次操作一行。
  - 参数面板过长问题：考虑底部 Sheet、Tab 分页或二级页面。

- [ ] **增加导出进度条和取消按钮**
  - 使用 `CupertinoLinearProgressIndicator` 或自定义进度条。
  - 在 `UiState` 中保存 `exportJob: Job?`，提供取消按钮。

## P1 — 尽快处理（影响功能正确性）

- [ ] **统一视频方向处理**
  - 三端（预览、导出、播放）方向一致。
  - 推荐方案：解码时即把 Mat 旋转到 0°，编码时按实际宽高输出，不再依赖 metadata。

- [ ] **动态申请媒体权限**
  - Android 13+：`READ_MEDIA_VIDEO`
  - Android 10-12：`READ_EXTERNAL_STORAGE`
  - Android 9 及以下：`WRITE_EXTERNAL_STORAGE`
  - 未授权时友好提示并禁用选视频按钮。

- [ ] **OpenCV 未就绪时禁用处理按钮**
  - 初始化失败不要只弹 dialog，应全局显示错误占位，或禁用预览/导出。

- [ ] **保存到相册异常细化**
  - 区分权限、磁盘、文件不存在等情况，给出明确 Toast/Dialog。

- [ ] **PlayerView 重组优化**
  - 避免 `showPlayer` 切换时重建 PlayerView。
  - 可用 `remember` 保存 PlayerView 实例，或始终保留用 alpha/visibility 切换。

## P2 — 稳定后优化

- [ ] **将 UiState 抽到 ViewModel**
  - 当前 `UiState` 直接定义在 `MainScreen` 中，逻辑和 UI 耦合。
  - 抽成 `MainViewModel`，处理选视频、预览、导出、保存等业务逻辑。

- [ ] **预览 Bitmap 异步生成优化**
  - 把 Mat → Bitmap 的全过程放到后台线程。
  - 对长视频预览生成缩略图尺寸，避免内存过大。

- [ ] **ExoPlayer 生命周期完善**
  - `onPause`/`onResume` 控制播放暂停。
  - 处理音频焦点、后台播放行为。

- [ ] **导出取消与临时文件清理**
  - 取消时停止编码协程。
  - `onDestroy` 清理未保存的临时 MP4。

- [ ] **增加 Compose Preview / Screenshot 测试**
  - 为 `ParameterPanel`、`ActionBar`、`PreviewArea` 加 `@Preview`。
  - 可考虑 Paparazzi 截图测试。

- [ ] **评估是否继续使用 Cupertino**
  - 若 alpha 库不稳定或难以实现设计，考虑回退到 Material3 或自定义组件。

- [ ] **深色模式/主题可配置**
  - 当前强制 `darkColorScheme()`。
  - 可跟随系统或提供切换开关。

- [ ] **性能基准测试**
  - 导出 1080p 视频耗时、内存占用峰值。
  - 针对不同 ABI（arm64-v8a / armeabi-v7a）测试。

## 已完成的里程碑

- [x] 调研 Compose + Cupertino + Haze 方案
- [x] Gradle 依赖迁移
- [x] Compose 主题与主屏幕实现
- [x] ExoPlayer 接入
- [x] Debug / Release CI 构建通过
- [x] 编写交接文档（HANDOFF、KNOWN_ISSUES、TODO、ARCHITECTURE、CHANGELOG）
