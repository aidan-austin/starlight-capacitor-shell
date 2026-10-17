# StarLight 皮肤站 · Android 客户端（Capacitor 远程壳）

把 `https://skin.starlight.cool` 封装成可安装的 Android 应用（APK）。
手机端用系统 WebView 直接加载线上网站，因此**皮肤库 / 登录注册 / 下载 / 聊天 / 投稿 / 抽奖等全部功能与网页完全一致，且每次打开都是最新版本**，网站更新无需重新发版。

> **这不是 PWA。** 工程里没有 `manifest.json`、没有 Service Worker、也没有本地网页资源；
> 它只是一个 Capacitor（`BridgeActivity` + WebView）原生壳，靠 `capacitor.config.json`
> 里的 `server.url` 远程加载站点。详见下文「五、与前端的实际关系」。

---

## 一、成品 APK

`releases/` 下有四个已打好的包（本仓即随源码一起分发）：

| 文件 | 体积 | 用途 |
| --- | --- | --- |
| `StarLight皮肤站-v1.2-release.apk` | 3.0 MB | **当前正式版**：开屏动画 + 主题记忆 + 双击退出 + 输入法修复 |
| `StarLight皮肤站-v1.1-release.apk` | 3.1 MB | 历史版本：修复返回键 + 状态栏/挖孔适配 |
| `StarLight皮肤站-v1.0-release.apk` | 3.2 MB | 历史版本，已用 release 密钥签名 |
| `StarLight皮肤站-v1.0-debug.apk` | 4.2 MB | 自测用，debug 签名，体积略大 |

包信息（`aapt dump badging` 实测）：

```
package: cool.starlight.skin   versionCode=3  versionName=1.2   （v1.1 = 2 / 1.1，v1.0 = 1 / 1.0）
application-label: StarLight皮肤站
launchable-activity: cool.starlight.skin.MainActivity
minSdkVersion=23   targetSdkVersion=35   compileSdkVersion=35
权限：INTERNET、WRITE/READ_EXTERNAL_STORAGE(maxSdk 28)
签名：release 密钥（证书 SHA-256 f6ee0ec6…d08d3，与 v1.0/v1.1 一致）
```

### 版本更新记录

| 版本 | 变更 |
| --- | --- |
| **v1.2** | ① **开屏动画**：白色像素星星自下而上渐入 → 左移，`StarLight` 沿同方向滑入（主页同款 `#FF1CF7→#B249F8` 竖向渐变字）→ 停留约 1 秒后淡出，整组水平居中<br>② **主题记忆**：启动时读网页 `<html data-theme>`（`autumn-light` / `black-dark`）存进本机，下次开屏沿用；**首次启动固定深色**（与网站默认一致）<br>③ **双击返回**：首页第一次按返回弹「再按一次退出应用」，2 秒内再按一次才退出<br>④ **输入法修复**：`android.captureInput` 改为 `false`，修复「光标放到文本中间输入、字却跑到末尾」<br>⑤ 状态栏/WebView 底色跟随主题；移除 `@capacitor/status-bar` 插件（它会用配置里的固定颜色覆盖主题色）<br>⑥ 清理：删除模板残留的 `splash.png`、示例测试类、未使用的 `site_icon.png`，APK 3.09 MB → 3.01 MB<br>⑦ 版本号 versionCode 2 → 3 |
| **v1.1** | ① **返回键修复**：站内子页面返回上一页，停在首页时返回键直接退出到桌面（此前首页按返回无反应）<br>② **状态栏/挖孔适配**：网页不再顶到物理屏幕最上沿，内容不会被状态栏、前置摄像头挖孔压住（网站自身没有 `viewport-fit=cover` 与安全区 padding，所以只能由壳来让出空间）<br>③ 版本号 versionCode 1 → 2 |
| v1.0 | 首个版本：WebView 远程加载 + 系统下载管理器接管下载 |

### 安装到手机
1. 把 `.apk` 传到手机（微信 / QQ / 网盘 / 数据线均可）。
2. 手机提示「未知来源应用」时允许安装（各品牌路径不同，一般在「安全 / 更多设置 → 允许安装未知应用」）。
3. 桌面出现 **StarLight皮肤站** 图标，打开即进入网站。

---

## 二、仓库结构

```
capacitor.config.json      # Capacitor 配置：App 信息 + 远程加载 URL + captureInput
package.json               # @capacitor/{core,android,app} + CLI
dist/index.html            # Capacitor 的 webDir，占位页（远程模式下不显示）
android/                   # 原生 Android 工程（Gradle）
  app/src/main/java/cool/starlight/skin/MainActivity.java   # 主 Activity：开屏动画 / 主题 / 返回键 / 下载
  app/src/main/res/drawable/logo_star.png                   # 开屏用的网站星星图标（132×133 像素图）
  app/src/main/AndroidManifest.xml                          # 清单（网络权限、adjustResize 等）
  app/src/main/res/                                         # 图标 / 名称 / 主题
  app/build.gradle                                          # 版本号 + 签名配置
  keystore.properties                                       # 签名密钥路径（勿入库，见第三节）
  local.properties                                          # SDK 路径（勿入库）
releases/                  # 随仓分发的 APK
```

---

## 三、如何重新打包 / 升级

### 环境
- **JDK 21**（⚠️ Capacitor 7 的模块要求 Java 21，用 JDK 17 会报 `无效的源发行版：21`）
  - 本机已装：`F:\sls\.tools\jdk21`（Temurin 21.0.12.1+1）
  - 注意：原先 README 里那个 Minecraft 内置 JDK 路径已被启动器清掉，不要再用
- **Android SDK**（platform 35 + build-tools 35）：本机 `F:\sls\.tools\android-sdk`
- **Node / npm**（仅 `npx cap sync` 需要；本仓已把 sync 产物强制入库，可不装）

### 步骤
```powershell
# 1) 依赖（可选，仅当需要重新 sync 时）
npm install
npx cap sync android

# 2) 环境变量（换成你自己的路径；下面是本机实际值）
$env:JAVA_HOME='F:\sls\.tools\jdk21'
$env:ANDROID_HOME='F:\sls\.tools\android-sdk'
$env:GRADLE_USER_HOME='F:\sls\.tools\gradle-home'

# 3) 打包
Set-Location android
.\gradlew.bat assembleDebug     # 调试包
.\gradlew.bat assembleRelease   # 签名正式包
```

产物输出：
- debug：`android\app\build\outputs\apk\debug\app-debug.apk`
- release：`android\app\build\outputs\apk\release\app-release.apk`

### 改版本号
编辑 `android\app\build.gradle` 的 `versionCode`（每次发版递增）与 `versionName`。

### 改 App 名称 / 图标
- 名称：`android\app\src\main\res\values\strings.xml`
- 图标：`android\app\src\main\res\mipmap-*` 下的 `ic_launcher*.png`（原图来自网站 `/logo-static.png`，本仓不再保存源图，需要时从站点或私有仓 `frontend/public/` 取）

### 改加载的站点
编辑 `capacitor.config.json` 的 `server.url` 和 `allowNavigation`，然后 `npx cap sync android` 重新打包。
若要让 App 完全离线（把网页打进安装包），把前端构建产物放进 `dist/`、删掉 `server.url`、再 `cap sync` 即可。

---

## 四、签名密钥（重要，务必备份）

正式包用 release 密钥签名，**密钥文件与 `keystore.properties` 都不在本仓**（已被 `.gitignore` 排除，防止密码泄露）。

`android/keystore.properties` 需要自己创建，格式：

```properties
storeFile=/absolute/path/to/starlight-release.keystore
storePassword=******
keyAlias=starlight
keyPassword=******
```

- 密钥别名：`starlight`，有效期 10000 天（约 27 年）。
- **密钥文件 + 密码务必单独备份**：丢失后将无法给已发布的包升级，只能换包名重新发布。
- 若 `keystore.properties` 不存在，`assembleRelease` 会产出未签名包（gradle 脚本已做存在性判断，不会报错）。

---

## 五、与前端的实际关系

- **运行方式**：`MainActivity extends BridgeActivity`，WebView 打开 `https://skin.starlight.cool/`；`allowNavigation` 限定在该域名内。
- **返回键**：`OnBackPressedCallback` 接管 —— 网页能回退（`WebView.canGoBack()`）先回退一步；已在首页时第一次按弹「再按一次退出应用」，2 秒内再按一次才 `finish()`。`plugins.App.disableBackButtonHandler=true` 关掉 Capacitor 自带处理，避免双重响应。
- **状态栏 / 挖孔**：不再依赖 `@capacitor/status-bar`（已移除，它会用配置里的固定颜色覆盖主题色）。改由 `MainActivity.applySystemBarColors()` 直接设置状态栏颜色与图标明暗；Android 15+ 用主题里的 `windowOptOutEdgeToEdgeEnforcement` 退出强制 edge-to-edge，页面始终从状态栏下方开始。
- **开屏动画**：`showIntroAnimation()` 纯原生绘制（星星 `ImageView` + 自绘 `GradientWordView` 画渐变字），不依赖网页；动画期间状态栏锁定跟随开屏背景，网页在背后同时加载。
- **主题记忆**：`ThemeBridge`（`addJavascriptInterface`）配合页面内 `MutationObserver` 读 `<html data-theme>`（`autumn-light` / `black-dark`），写入 `SharedPreferences("starlight")` 的 `theme`；下次启动据此决定开屏背景。**首次启动（无记忆）固定深色**，与网站默认一致。
- **输入法**：`android.captureInput=false`，走 WebView 原生 `InputConnection`，光标位置插入、中文拼音组字/候选都正常。
- **界面来源**：完全来自线上前端（Vue 3 + Vite 构建产物），本仓不含任何网页资源，`dist/index.html` 只是 115 字节占位页。
- **因此**：站点更新 = App 立即更新；离线不可用（首次打开必须联网）。
- 线上站点目前同样没有 PWA 清单 / Service Worker，所以「添加到主屏幕」只能得到普通书签，没有离线缓存。

## 六、已知说明与后续可选增强

- **聊天推送**：当前聊天室是网页内即时交互（在线时可用）。要在 App 内收到离线/推送通知，需接入 `@capacitor/push-notifications` + 厂商推送（FCM / 国内厂商），并让后端配合下发 token。
- **下载**：`MainActivity` 已加 `DownloadListener`，皮肤/文件下载走系统 `DownloadManager`，落到系统「下载」目录并弹通知。
- **录音（语音聊天）**：站点语音功能需要麦克风时，安装后首次使用系统会请求权限，允许即可。
- **WebView 版本**：依赖手机自带 Android System WebView（minSdk 23 / Android 6.0+），老旧机型网页兼容性由系统内核决定。
