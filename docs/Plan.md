**重写原生 Android 漫画阅读器（E-ink 优化版）开发计划文档**

**版本**：1.0（2026 年 4 月）
**目标设备**：Android 4.4.2（API 19）低内存老设备（已 root，可用 hosts/证书优化）
**核心���求**：
- 保留 **JS 源灵活性**（类似 Venera (https://github.com/venera-app/venera https://github.com/venera-app/flutter_qjs)，用 JS 写漫画源，支持 jm18c-oec.club 等 Cloudflare 分流）。
- 保留 **WebDAV 历史 & 收藏夹同步**（轻量，无需完整下载）。
- 去掉 **下载功能**（简化，降低内存/存储压力）。
- 默认做成 **E-ink 优化版**（高对比、无动画、适合 Boox/类似墨水屏，也兼容普通屏幕）。

**可行性总结**：完全可行，但需**极致轻量**（Venera 是 Flutter，重构为纯原生 Kotlin + QuickJS 后体积可控制在 8-12 MB）。工作量中等（建议 4-6 周一人全职），重点在 JS 引擎集成、E-ink 适配和 **TLS 1.2 兼容性处理**。API 19 兼容性是最大挑战，但有成熟方案。

### 1. 项目概述 & 架构设计
**推荐架构**：**MVVM + Clean Architecture（精简版）**

- **分层**（Android 官方推荐 + 老设备适配）：
- **UI 层**：Activity + Fragment（或单 Activity + ViewPager），用 AndroidX + Jetifier 兼容。
- **ViewModel 层**：LiveData / 简单 Callback（避免 Flow/Coroutines 过重）。
- **Domain 层**：JS 源解析器 + 业务逻辑。
- **Data 层**：Room/SQLite（轻量本地历史/收藏） + WebDAV 客户端 + QuickJS 引擎。
- **为什么原生 Kotlin**：比 Flutter 轻（无 Dart/Flutter 运行时），对 API 19 兼容更好，内存占用低。
- **默认 E-ink 模式**：App 启动即进入"墨水屏模式"（高对比黑白主题、无动画），设置里保留"普通屏幕切换"。

**项目结构**（推荐）：
```
app/
├── src/main/
│   ├── java/com/yourapp/
│   │   ├── ui/ # Activity、Adapter、E-ink Theme
│   │   ├── data/ # Repository、WebDAV、QuickJS
│   │   ├── domain/ # JS Source Parser、Manga Models
│   │   ├── di/ # Hilt/Dagger 简化版或手动依赖注入
│   │   └── utils/ # E-ink Utils、Hosts 辅助
│   ├── res/ # drawable（仅黑白 PNG）、values（高对比 theme）
│   └── AndroidManifest.xml
├── build.gradle # minSdk 19
└── quickjs/ # JNI 模块（QuickJS C 源码）
```

### 2. 技术栈推荐（API 19 兼容版）
| 模块 | 推荐库 / 方案 | 理由 & 注意 |
| ------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **JS 引擎** | taoweiji/quickjs-android（JNI 绑定） | 直接对应 Venera 的 flutter_qjs（QuickJS）。体积小（~350KB），支持 ES2020+，适合解析 Cloudflare 分流 JS。**注意**：库已停止维护（2021年6月），如需新版本需自行编译。 |
| **WebDAV** | thegrizzlylabs/sardine-android（OkHttp 版） | 轻量、OkHttp 底层，完美支持历史/收藏同步。只需 PUT/GET/PROPFIND。2024年2月仍有更新。 |
| **网络** | OkHttp 3.12.x + Conscrypt | API 19 兼容，结合 hosts 解决 DNS。**必须加 Conscrypt 启用 TLS 1.2**，否则无法访问现代 HTTPS 站点。 |
| **本地存储** | Room 2.x（SQLite） | 存历史、收藏、JS 源缓存。最低支持 API 16，比 1.x 更稳定。 |
| **UI** | AndroidX + RecyclerView/ListView | E-ink 默认高对比主题（黑底白字），禁用动画。Google 已停止 Support Library 更新，建议用 AndroidX + Jetifier 兼容。 |
| **图片加载** | Glide 4.x（老版）或 Picasso | 只加载必要章节图，内存缓存严格控制。 |
| **构建** | Gradle 5.x + AGP 3.x | API 19 必须用老工具链。 |
| **依赖注入** | Dagger 2.x（简化）或手动 | 避免 Hilt（太新）。 |

**E-ink 优化核心**：
- 主题：纯黑白（#000000 / #FFFFFF），高对比，无渐变/阴影。
- 禁用动画：`window.setWindowAnimations(0)` 或 `overridePendingTransition(0,0)`。
- 页面翻页：强制全刷新（可用 BOOX 风格的自定义 View + invalidate() + 延迟）。
- DPI/字体：默认 350+ DPI 粗体，文字加粗。
- 检测 E-ink：启动时可加开关（用户手动选"墨水屏模式"）。

### 3. 核心功能实现指南
#### 3.1 JS 源（Venera 核心保留）
- **集成 QuickJS**：
1. 把 QuickJS C 源码放 `jni/`，编译 .so（armeabi-v7a）。
2. 用 `QuickJS.Builder()` 创建 Runtime/Context。
3. 加载用户 JS 源（assets 或 WebDAV 下载的 .js 文件）。
4. 调用 Venera 风格的 API（如 `getChapters()`、`getImages()`）。
- **示例代码**（Kotlin）：
```kotlin
val quickJS = QuickJS.Builder().build()
val context = quickJS.createContext()
context.evaluate(jsSourceCode, "source.js")
val result = context.callFunction("getMangaList", params)
```
- **注意**：JS 源兼容 Cloudflare → 用 OkHttp 加 Headers + hosts 辅助解析。

#### 3.2 WebDAV 历史 & 收藏同步
- 用 Sardine：
```kotlin
val sardine = OkHttpSardine()
sardine.setCredentials(user, pass)
sardine.put("https://your-webdav/history.json", jsonBytes) // 同步收藏
```
- 每 5 分钟或退出时自动同步（后台 Service，轻量）。
- 只同步 JSON（历史记录 + 收藏列表），不存图片。

#### 3.3 E-ink 优化（默认开启）
- **主题**：`AppCompatDelegate.setDefaultNightMode` + 自定义黑白 style。
- **无动画**：全局 `overridePendingTransition(0,0)` + RecyclerView `setItemAnimator(null)`。
- **翻页全刷新**：阅读界面用自定义 ReaderView，每翻页调用 `view.invalidate()` + 延迟 300ms。
- **额外**：设置里加"刷新模式"（Normal / Fast / Regal），兼容普通屏和 Boox。

#### 3.4 去掉下载
- 移除所有下载队列/存储相关代码，只保留在线阅读 + 章节缓存（内存级）。

### 4. 开发步骤（分阶段，建议顺序）
1. **Week 1-2：基础框架**
   - 新建 Empty Activity 项目（minSdk 19）。
   - 配置 AndroidX + Jetifier（通过 gradle.properties）。
   - 集成 QuickJS JNI + OkHttp + Conscrypt + Sardine。
   - 实现简单"添加 JS 源"页面（从 WebDAV 或本地导入）。

2. **Week 3：JS 解析核心**
   - 移植 Venera JS API（getList、getChapters、getImages）。
   - 测试 jm18c-xxx.club 等分流 JS 源。
   - **重点**：验证 TLS 1.2 在 API 19 上正常工作。

3. **Week 4：阅读器 + E-ink**
   - 实现章节列表 → 图片阅读（ViewPager + Touch 翻页）。
   - 加上 E-ink 默认主题 + 无动画。

4. **Week 5-6：WebDAV + 收尾**
   - 实现历史/收藏同步。
   - 优化低内存（严格 Bitmap.recycle()、单线程加载）。
   - 测试 + 打包 APK。

### 5. 注意事项 & 潜在坑（API 19 低内存重点）
- **内存**：严格控制图片缓存（Glide 设 10MB 上限），章节加载用单例线程池。一次只缓存 1-2 章。
- **TLS 1.2（关键）**：OkHttp 3.12.x 在 API 19 不支持 TLS 1.2，需集成 Conscrypt：
  ```groovy
  implementation 'org.conscrypt:conscrypt-android:2.5.2'
  ```
  并在 Application 中初始化：`Security.insertProviderAt(Conscrypt.newProviderBuilder().build(), 1)`
- **API 19 兼容**：推荐使用 AndroidX，通过 `android.enableJetifier=true` 兼容旧Support Library；避免 Material Design 2+、Coroutines。
- **证书/DNS**：继续用你已装的系统证书 + hosts 文件（App 内可加"自动加 hosts"开关）。
- **JS 引擎坑**：QuickJS 内存泄漏风险高 → 每个源用独立 Context，用完 `close()`。taoweiji 库停止维护，如有问题需自行修复 JNI。
- **E-ink 屏幕撕裂**：翻页强制全刷；避免复杂 View（用 Canvas 绘制文字/图）。
- **安全**：JS 源执行用沙箱（QuickJS 限制 eval）；WebDAV 用 HTTPS + 凭证加密。
- **体积/性能**：目标 APK < 12MB；测试时只装 2-3 个 JS 源。
- **调试**：用 ADB + 真机（4.4.2），Logcat 看 QuickJS 错误。
- **Venera 归档**：原项目已于 2026 年 4 月归档不再维护，但 JS 源格式仍可参考。

**推荐启动仓库**：Fork 一个极简 Android 模板（如 "Android 4.4 boilerplate"），然后按以上集成。

这个计划已调研过 QuickJS Android 绑定、sardine-android 示例、BOOX E-ink 优化经验，完全可落地。但需重点处理 **TLS 1.2 兼容性** 和 **依赖维护** 两个风险点。

如果你需要：
- 具体 build.gradle / QuickJS JNI 配置代码
- 第一个 JS 源模板
- 或分阶段 GitHub Issue 模板