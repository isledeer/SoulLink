# SoulLink Native（原生外壳）

原生 Kotlin + Compose 外壳，内嵌 WebView 承载现有 React 内核（`../dist`）。
游戏层/聊天界面的重活继续交给 React，外壳负责：原生导航与设置页（后续）、
通知、文件选择、以及**原生网络层**（OkHttp SSE，摆脱 WebView 的 CORS 与流式开销）。

## 结构

```
native/
├── app/src/main/java/com/isledeer/soullink/
│   ├── MainActivity.kt        Compose 外壳 + WebView 宿主（含文件选择器桥）
│   ├── LocalAssetServer.kt    本地 HTTP 服务器（127.0.0.1:38517）提供 assets
│   ├── WebAppInterface.kt     JS ↔ Kotlin 桥
│   └── NativeSSE.kt           OkHttp 原生 SSE（解析 → evaluateJavascript 推送）
├── app/src/main/assets/       WebView 内核（由 ../dist 同步）
└── gradlew                    构建入口
```

## 两个关键设计

1. **内核不走 file://，走 127.0.0.1** —— file:// 的 origin 是 `null`，
   第三方 API 的 CORS 预检基本必挂；而且 WebView 在 file:// 下 IndexedDB
   不稳，存档会丢。端口固定 38517 是为了让 origin 稳定。
2. **图标/包名沿用 `com.isledeer.soullink`** —— 可覆盖安装旧版。但 WebView
   的存储 origin 变了（https://localhost → http://127.0.0.1:38517），
   旧版存档不会自动迁移。

## 构建

### 路线 A：Termux 本地构建

```bash
# 1. JDK
pkg install openjdk-17

# 2. Android SDK（Google 不发布 arm64 Linux 版，手动拼装）
pkg install aapt2 zipalign apksigner || true   # 有哪个装哪个
mkdir -p ~/android-sdk/platforms
cd ~/android-sdk/platforms
curl -LO https://dl.google.com/android/repository/platform-34_r02.zip
unzip -q platform-34_r02.zip && mv android-14 android-34   # 注意：API34 的目录名是 android-14

export ANDROID_HOME=$HOME/android-sdk

# 3. 让 Gradle 用 Termux 的 aapt2（Google 的 aapt2 只有 x86_64）
#    在 native/gradle.properties 里解开这一行：
#    android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

# 4. Web 内核先构建（在仓库根目录，用你现有流程）
#    pnpm build   → 产出 dist/

# 5. 打包
cd native
./gradlew assembleDebug        # syncDist 会自动把 ../dist 拷进 assets
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 路线 B：GitHub Actions（推荐，x86_64 环境零摩擦）

在现有 workflow 里加一个 job 即可（需要 sdkmanager 安装 platform-34 +
build-tools;34.0.0）。这条路不用处理 aapt2 架构问题。

## 已知摩擦点

- **首次构建大概率有小问题**：AGP/Kotlin 版本组合、Gradle 下载、
  Termux 的 aapt2 版本差异。把报错贴给 AI 助手即可。
- Termux 里 Gradle 首次运行要下载 ~150MB 依赖，网络差时耐心等。
- `dist/` 不存在时构建**不会失败**，装出来是占位页（提示未同步内核）。

## 阶段规划

- [x] 外壳骨架 + 双向内核桥 + 本地资源服务器 + 原生 SSE 通道
- [ ] React 侧接入：`apiClient` 切换到 `window.SoulLinkNative.sendChatRequest`
- [ ] Compose 设置页（API 配置、模型、思考强度），替代 Web 内设置页
- [ ] 原生文件导入（角色卡 PNG）
- [ ] 通知、后台保活