# Motion Extractor Mobile — 交接文档

> 本文档用于在切换开发环境/交接给另一位开发者时快速了解项目现状。
> 最后更新：2026-06-15

## 1. 项目状态摘要

- **仓库**：`https://github.com/Everett406/motion-extractor-mobile`
- **当前分支**：`main`
- **最后一次提交**：`80fa6ad` — `feat: migrate UI to Jetpack Compose + iOS Cupertino + Haze glass`
- **CI 状态**：GitHub Actions Release 构建已通过，可下载 `app-release.apk`。
- **可用性**：功能基本可跑（选视频 → 生成预览 → 导出 → 播放/保存），但 **UI/UX 体验较差、已知 bug 较多**，需要一轮打磨。

## 2. 本次迁移目标与结果

### 目标
把旧的 Android XML + Material3 界面迁移为：
- **Jetpack Compose** UI
- **iOS Cupertino** 风格控件（`compose-cupertino`）
- **Haze 毛玻璃/模糊**效果
- 用 **ExoPlayer** 做导出后视频播放

### 结果
- ✅ Gradle 依赖已切换
- ✅ `MainActivity` 已改为 `ComponentActivity`
- ✅ Compose 主题、屏幕、参数面板、预览区、操作栏已实现
- ✅ ExoPlayer 已接入并在 Activity 级别管理生命周期
- ✅ Debug / Release 构建均通过
- ❌ **UI 效果未达预期**：Haze 模糊位置、整体排版、交互细节需要重新设计

## 3. 已完成（Highlights）

详见 [CHANGELOG.md](./CHANGELOG.md)。

- OpenCV 4.10.0 Android SDK 集成，NDK `libc++_shared.so` 自动打包
- MediaCodec 视频解码/编码管线（替代 OpenCV VideoCapture/Writer）
- 运动提取核心算法 + RGB 通道分离拖影 + 预设系统
- Compose + Cupertino + Haze 初版 UI
- GitHub Actions 自动构建 Release APK 并发布到 GitHub Releases

## 4. 已知问题

完整列表见 [KNOWN_ISSUES.md](./KNOWN_ISSUES.md)。主要问题：

1. **Haze 模糊位置不对**：目前给整个滚动背景加了 `haze()`，参数面板用 `hazeChild()`，导致背景糊、排版乱。应改为：背景保持纯色/渐变，仅对浮层面板使用 `hazeChild()`，并确保面板下方有可见内容被模糊。
2. **排版混乱**：所有控件垂直堆叠，参数面板过长；预览区固定 16:9，竖屏视频体验差。
3. **预设按钮没有选中状态**：`CupertinoCapsuleButton.selected` 始终传 `false`。
4. **导出进度只有文字**：缺少进度条/取消按钮。
5. **视频方向处理待验证**：预览、导出、播放三处旋转逻辑可能不一致。
6. **Android 13+ 权限未动态申请**：`READ_MEDIA_VIDEO` 只在 Manifest 声明，未在代码中请求。

## 5. 待办事项

完整优先级列表见 [TODO.md](./TODO.md)。

**P0（必须先修）**：
- 重新设计 Haze 毛玻璃用法和整体布局
- 修复预设胶囊选中状态
- 增加导出进度条与取消按钮

**P1（尽快处理）**：
- 统一视频方向处理
- 动态申请媒体权限
- 将 `UiState` 从 `MainScreen` 抽到 `ViewModel`

**P2（后续优化）**：
- Compose Preview / Screenshot 测试
- 预览 Bitmap 尺寸优化
- 评估是否继续使用 Cupertino，或回退/混合 Material3

## 6. 架构速览

详细说明见 [ARCHITECTURE.md](./ARCHITECTURE.md)。

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                                 │
│  MainActivity → MainScreen → PreviewArea / ActionBar /      │
│  ParameterPanel / ExoPlayer (via LocalExoPlayer)            │
└──────────────────────┬──────────────────────────────────────┘
                       │ UiState.toMotionExtractParams()
┌──────────────────────▼──────────────────────────────────────┐
│  Processing Layer                                           │
│  VideoProcessor → VideoDecoder → MotionExtractor →          │
│  VideoEncoder                                               │
└─────────────────────────────────────────────────────────────┘
```

## 7. 关键决策记录（ADR）

| 决策 | 选择 | 原因 | 备注 |
|------|------|------|------|
| UI 框架 | Jetpack Compose | 官方现代 UI 框架，易于实现动态主题/动画 | 当前 Cupertino 库版本较新且为 alpha，组件行为需多测试 |
| 控件风格 | iOS Cupertino | 用户希望“苹果风” | 若库不稳定，可回退到 Material3 或自定义 |
| 毛玻璃 | Haze 0.7.0 | 专为 Compose 设计的模糊库 | API 较新，需要正确理解 `haze()` + `hazeChild()` 配对 |
| 视频播放 | ExoPlayer Media3 | 官方推荐，支持本地文件 URI | PlayerView 与 Compose 组合需要生命周期注意 |
| 视频编解码 | MediaCodec + MediaMuxer | OpenCV Android SDK 缺少可靠视频 I/O | 自己管理解码/编码更可控 |
| OpenCV 初始化 | initLocal + loadLibrary fallback | 部分设备上 initLocal 不稳定 | 保留 initDebug 兜底 |

## 8. 构建与运行

### 本地构建

```bash
# 1. 下载 OpenCV Android SDK
bash scripts/setup-opencv.sh        # Linux / macOS / Git Bash
.\scripts\setup-opencv.ps1          # Windows PowerShell

# 2. 编译 Debug APK
./gradlew :app:assembleDebug

# 输出
app/build/outputs/apk/debug/app-debug.apk
```

> **注意**：如果本地 NDK 未安装，`setup-opencv.sh` 不会打包 `libc++_shared.so`，生成的 APK 在某些设备上运行会报 `dlopen failed`。CI 环境已安装 NDK，Release APK 无此问题。

### CI 构建

- 每次 push 到 `main` 会自动触发 `.github/workflows/build.yml`
- 构建产物上传到 GitHub Actions Artifacts 并发布到 GitHub Releases
- 需要配置 4 个 Secrets：`RELEASE_KEYSTORE`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`

## 9. 交接建议

1. **先跑一遍真机流程**：选视频 → 生成预览 → 导出 → 播放 → 保存，确认当前 bug 在你的设备上的表现。
2. **优先修 UI/UX**：P0 问题解决了，产品才可用。
3. **不要同时做大重构和加功能**：先把现有 Compose 代码调稳定，再考虑 ViewModel 化、测试等。
4. **保留 XML 备份已无意义**：旧布局 `activity_main.xml` 已在本次迁移中删除，如有需要可从 Git 历史恢复。
5. **有问题先看 `KNOWN_ISSUES.md` 和 `TODO.md`**，避免重复踩坑。

## 10. 相关文档索引

- [README.md](./README.md) — 项目简介、功能、构建说明
- [ARCHITECTURE.md](./ARCHITECTURE.md) — 技术架构
- [KNOWN_ISSUES.md](./KNOWN_ISSUES.md) — 已知 bug 清单
- [TODO.md](./TODO.md) — 后续任务清单
- [CHANGELOG.md](./CHANGELOG.md) — 变更历史
