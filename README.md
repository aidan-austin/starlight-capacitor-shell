# StarLight 皮肤站 · Android 客户端（Capacitor 远程壳）

把 `https://skin.starlight.cool` 封装成可安装的 Android 应用（APK）。
手机端用系统 WebView 直接加载线上网站，因此**皮肤库 / 登录注册 / 下载 / 聊天 / 投稿 / 抽奖等全部功能与网页完全一致，且每次打开都是最新版本**，网站更新无需重新发版。

> **这不是 PWA。** 工程里没有 `manifest.json`、没有 Service Worker、也没有本地网页资源；
> 它只是一个 Capacitor（`BridgeActivity` + WebView）原生壳，靠 `capacitor.config.json`
> 里的 `server.url` 远程加载站点。详见下文「五、与前端的实际关系」。

---

## 一、成品 APK

`releases/` 下有两个已打好的包（本仓即随源码一起分发这两个文件）：

| 文件 | 体积 | 用途 |
| --- | --- | --- |
| `StarLight皮肤站-v1.0-release.apk` | 3.2 MB | **正式分发用**，已用 release 密钥签名 |
| `StarLight皮肤站-v1.0-debug.apk` | 4.2 MB | 自测用，debug 签名，体积略大 |

包信息（`aapt dump badging` 实测）：

```
package: cool.starlight.skin   versionCode=1   versionName=1.0
application-label: StarLight皮肤站
launchable-activity: cool.starlight.skin.MainActivity
minSdkVersion=23   targetSdkVersion=35   compileSdkVersion=35
权限：INTERNET、WRITE/READ_EXTERNAL_STORAGE(maxSdk 28)
```

### 安装到手机
1. 把 `.apk` 传到手机（微信 / QQ / 网盘 / 数据线均可）。
2. 手机提示「未知来源应用」时允许安装（各品牌路径不同，一般在「安全 / 更多设置 → 允许安装未知应用」）。
3. 桌面出现 **StarLight皮肤站** 图标，打开即进入网站。

---

## 二、仓库结构

```
capacitor.config.json      # Capacitor 配置：App 信息 + 远程加载 URL + 状态栏等
package.json               # @capacitor/{core,android,app,status-bar} + CLI
dist/index.html            # Capacitor 的 webDir，占位页（远程模式下不显示）
site_icon.png              # 图标源图
android/                   # 原生 Android 工程（Gradle）
  app/src/main/java/cool/starlight/skin/MainActivity.java   # 主 Activity（含下载处理）
  app/src/main/AndroidManifest.xml                          # 清单（网络权限等）
  app/src/main/res/                                         # 图标 / 启动图 / 名称
  app/build.gradle                                          # 版本号 + 签名配置
  keystore.properties                                       # 签名密钥路径（勿入库，见第三节）
  local.properties                                          # SDK 路径（勿入库）
releases/                  # 随仓分发的 APK
```

---

## 三、如何重新打包 / 升级

### 环境
- **JDK 21**
- **Android SDK**（platform 35 + build-tools 35）
- **Node / npm**（仅 `npx cap sync` 需要；本仓已把 sync 产物强制入库，可不装）

### 步骤
```powershell
# 1) 依赖（可选，仅当需要重新 sync 时）
npm install
npx cap sync android

# 2) 环境变量
$env:JAVA_HOME='<你的 JDK 21 目录>'
$env:ANDROID_HOME='<你的 Android SDK 目录>'
$env:GRADLE_USER_HOME='<你的 gradle 缓存目录>'

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
- 图标：`android\app\src\main\res\mipmap-*` 下的 `ic_launcher*.png`（源图 `site_icon.png`）

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
- **界面来源**：完全来自线上前端（Vue 3 + Vite 构建产物），本仓不含任何网页资源，`dist/index.html` 只是 115 字节占位页。
- **因此**：站点更新 = App 立即更新；离线不可用（首次打开必须联网）。
- 线上站点目前同样没有 PWA 清单 / Service Worker，所以「添加到主屏幕」只能得到普通书签，没有离线缓存。

## 六、已知说明与后续可选增强

- **聊天推送**：当前聊天室是网页内即时交互（在线时可用）。要在 App 内收到离线/推送通知，需接入 `@capacitor/push-notifications` + 厂商推送（FCM / 国内厂商），并让后端配合下发 token。
- **下载**：`MainActivity` 已加 `DownloadListener`，皮肤/文件下载走系统 `DownloadManager`，落到系统「下载」目录并弹通知。
- **录音（语音聊天）**：站点语音功能需要麦克风时，安装后首次使用系统会请求权限，允许即可。
- **WebView 版本**：依赖手机自带 Android System WebView（minSdk 23 / Android 6.0+），老旧机型网页兼容性由系统内核决定。

---

## 七、姊妹项目

**Flutter 原生客户端**（不走 WebView，直接调后端 `/starlight/*` 接口）在另一个仓维护；
本仓只负责上面的 WebView 壳。两者包名不同，可同时安装：

| App | 包名 |
| --- | --- |
| 本仓 WebView 壳 | `cool.starlight.skin` |
| Flutter 原生客户端 | `cool.starlight.starlight_skin` |
