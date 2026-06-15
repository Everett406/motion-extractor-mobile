# Motion Extractor Mobile

Android APK 版「运动提取」视频处理工具。

复刻自桌面版 [motion-extractor-app](https://github.com/Everett406/motion-extractor-app) 的核心思路：
把视频复制一层、反色、半透明、做时间偏移后叠加，让静止部分相互抵消，只保留运动信息。
完整支持原版的 RGB 通道分离拖影效果。

## 功能

- 从相册选择视频
- 实时调节参数：帧偏移、反色、不透明度、高斯模糊、发光、对比度、亮度
- 输出模式：彩色 / 灰度 / 反色
- RGB 通道分离拖影（彩虹尾迹）
- 一键预设：经典、霓虹、高对比、柔和
- 生成预览帧（多张拼贴）
- 导出处理后的视频（MP4）
- 分享 / 保存到相册

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose + iOS Cupertino（compose-cupertino）+ Haze 毛玻璃
- 视频/图像处理：OpenCV Android SDK
- 构建：Gradle + Android Gradle Plugin 8.3
- CI/CD：GitHub Actions（自动编译 APK）

## 项目结构

```
motion-extractor-mobile/
├── .github/workflows/build.yml   # GitHub Actions 构建配置
├── app/                          # Android 应用模块
│   ├── src/main/java/com/everett/motionextractor/
│   │   ├── MainActivity.kt       # Compose 入口与 ExoPlayer 生命周期
│   │   ├── MotionExtractor.kt    # 运动提取核心算法
│   │   ├── VideoProcessor.kt     # 视频读取/写入封装
│   │   └── ui/                   # Compose UI（Cupertino + Haze）
│   │       ├── MainScreen.kt
│   │       ├── ParameterPanel.kt
│   │       ├── PreviewArea.kt
│   │       ├── ActionBar.kt
│   │       ├── UiState.kt
│   │       ├── ExoPlayerManager.kt
│   │       └── theme/Theme.kt
│   └── src/main/res/             # 主题、图标、FileProvider 配置
├── scripts/
│   ├── setup-opencv.sh           # CI / Linux / macOS 下载 OpenCV
│   └── setup-opencv.ps1          # Windows 下载 OpenCV
├── gradle/                       # Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 本地开发

### 1. 克隆仓库

```bash
git clone https://github.com/Everett406/motion-extractor-mobile.git
cd motion-extractor-mobile
```

### 2. 准备 Android NDK（推荐）

OpenCV 4.10.0 的预编译 `libopencv_java4.so` 依赖 NDK 提供的 C++ 共享运行时 `libc++_shared.so`。脚本会自动从已安装的 NDK 复制该库；如果本地没有 NDK，APK 在某些设备上会出现 `dlopen failed: library "libc++_shared.so" not found`。

通过 Android Studio 的 SDK Manager 安装 **NDK (Side by side) 26.1.10909125**（或任意较新版本），或设置环境变量：

```bash
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
```

### 3. 下载 OpenCV Android SDK

**Linux / macOS / Git Bash:**

```bash
bash scripts/setup-opencv.sh
```

**Windows PowerShell:**

```powershell
.\scripts\setup-opencv.ps1
```

脚本会自动：
- 下载 OpenCV Android SDK（约 250 MB）
- 解压到 `opencv-sdk/`
- 把 native so 库复制到 `app/src/main/jniLibs/`
- 把 Java 模块放到 `opencv/sdk/`，供 Gradle 引用
- 如果检测到 Android NDK，会同时把 `libc++_shared.so` 一起打包（OpenCV 的预编译库依赖它）

### 3. 编译 Debug APK

```bash
./gradlew assembleDebug
```

APK 输出位置：

```
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions 自动构建

仓库已配置 `.github/workflows/build.yml`：

- 每次 push 到 `main` 分支会自动触发构建
- CI 会自动安装 Android NDK、下载并缓存 OpenCV SDK
- 构建 **Release APK** 并自动创建 GitHub Release
- Release 中会上传已签名的 `app-release.apk`

### 配置签名密钥

CI 使用 GitHub Secrets 对 Release APK 签名。需要添加以下 4 个 Secrets：

| Secret 名称 | 说明 |
|-------------|------|
| `RELEASE_KEYSTORE` | 签名 keystore 文件的 Base64 编码 |
| `RELEASE_KEYSTORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key 密码 |

生成 keystore 并获取 Base64 示例：

```bash
keytool -genkey -v -keystore release.keystore -alias motionextractor \
  -keyalg RSA -keysize 2048 -validity 10000

base64 -w 0 release.keystore
```

把输出的 Base64 字符串添加到仓库 Settings → Secrets and variables → Actions → New repository secret。

手动触发：进入仓库 → Actions → Build Android APK → Run workflow。

## 算法说明

### 基础运动提取

对每一帧 `F(t)` 与偏移帧 `F(t + Δ)` 做以下混合：

1. 取偏移帧，可选反色：`F' = 255 - F(t + Δ)`
2. 按不透明度 `α` 混合：`R = (1 - α) * F(t) + α * F'`
3. 静止区域 `F(t) ≈ F(t + Δ)` 会抵消成中灰色，运动区域保留
4. 可选高斯模糊、发光、对比度/亮度调整增强效果

### RGB 通道分离拖影

开启后，R/G/B 三个通道分别使用不同的时间偏移帧计算运动幅度：

```
channel = clamp(|F(t) - F(t + Δ_channel)| * 3.0, 0, 1)
```

移动物体身后会留下红、绿、蓝错位的彩色尾迹，效果类似霓虹/故障艺术。

### 预设说明

| 预设 | 特点 |
|------|------|
| 经典 | 默认灰度运动提取，效果干净 |
| 霓虹 | 彩色 + RGB 拖影 + 发光，效果强烈 |
| 高对比 | 高对比度灰度，运动边缘更锐利 |
| 柔和 | 轻微模糊 + 低对比 + 柔和拖影，氛围感强 |

## 已知限制

- 处理长视频需要一定时间，建议在后台线程执行（已实现）。
- 输出为 H.264 / MP4，兼容性较好。

## 许可证

MIT
