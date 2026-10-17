# StarLight 皮肤站 · Android 客户端

把 <https://skin.starlight.cool> 封装成 Android App（Capacitor WebView 壳）。打开即进入网站，功能与网页一致，站点更新后 App 自动生效。

## 下载安装

`releases/` 目录内有安装包，也可从 [Releases](https://github.com/aidan-austin/starlight-capacitor-shell/releases) 下载最新版。

| 版本 | 说明 |
| --- | --- |
| **v1.2（最新）** | 开屏动画、主题记忆、双击退出、输入法修复 |
| v1.1 | 返回键修复、状态栏适配 |
| v1.0 | 首个版本 |

下载 APK 直接安装；已装旧版可覆盖安装（签名一致）。支持 Android 6.0 及以上。

## 更新记录

- **v1.2**
  - 新增开屏动画
  - 开屏背景跟随网站深浅色，首次启动默认深色
  - 首页双击返回才退出
  - 修复输入法：光标放在文本中间输入却跑到末尾
  - 体积 3.01 MB
- **v1.1**
  - 修复首页按返回无反应
  - 适配状态栏 / 挖孔屏
- **v1.0**
  - 首个版本，WebView 远程加载 + 系统下载管理器

## 构建

环境：JDK 21、Android SDK 35、Node.js（仅 `npx cap sync` 需要）。

```powershell
npm install
npx cap sync android

$env:JAVA_HOME='F:\sls\.tools\jdk21'
$env:ANDROID_HOME='F:\sls\.tools\android-sdk'
Set-Location android
.\gradlew.bat assembleRelease
```

产物：`android\app\build\outputs\apk\release\app-release.apk`

改版本号：`android\app\build.gradle` 中的 `versionCode` / `versionName`。

## 签名

正式包使用 release 密钥签名。`android/keystore.properties` 自行创建（不入库）：

```properties
storeFile=/absolute/path/to/starlight-release.keystore
storePassword=******
keyAlias=starlight
keyPassword=******
```

密钥与密码请单独备份——丢失后无法再升级已发布的包。

## 说明

- 站点更新后 App 自动使用最新内容，无需重新安装；离线不可用。
- 下载走系统下载管理器，文件保存在系统「下载」目录。
- 依赖手机自带 Android System WebView（Android 6.0+）。
