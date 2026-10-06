<div align="center">
  <img src="https://raw.githubusercontent.com/shilapi/xcertplay/refs/heads/master/asset/xcertplay_small.png" width="180" height="180" alt="xcertplay icon" />
<h1><strong><font size="6">xcertplay</font></strong></h1>
  <a href="README.md">English</a> | <a href="README.zh-CN.md">中文</a>
  <p>xcertplay 是面向 Android 车机的 CarPlay 接收端项目。支持通过 CH341 I2C 桥接到 MFi 芯片，亦可通过板载 I2C 控制器直连，支持 CarPlay 有线和无线连接。</p>
</div>

## 领克 09 EX11 / 2023 EM-P 适配

设置页新增 **Apply Lynk & Co 09 EX11 preset**（应用领克 09 预设）。点击后会选择无线 CarPlay、USB/CH341 MFi、Android 9 的 LocalOnlyHotspot，以及领克车机身份字段；如缺少无线运行时权限，Android 会正常弹出授权请求。再检查设置并点击 **Save and reconnect**。

此预设不会关闭原厂 CarLink，也不修改系统权限。它只是基于此前车机采集得到的起始配置；CH341 枚举、MFi 认证、CarPlay 会话、音频路由和 NaviTool/HUD 转发仍需实车验证。完善版分阶段设置和验收顺序见[适配实施记录](docs/lynkco-09-ex11-adaptation.md)和[车辆采集评估](docs/lynkco-09-adaptation-overview.md)。

### 上车诊断日志

连接诊断事件保存在车机**内置存储**的应用目录 `内部存储/Android/data/com.shilapi.xcertplay/files/logs/xcertplay.log`（Android 上常见完整路径为 `/storage/emulated/0/Android/data/com.shilapi.xcertplay/files/logs/xcertplay.log`，不需要可拆卸 SD 卡）。若设备没有该共享存储目录，则保存到应用私有 `files/logs`。日志包含应用/车机版本、配置摘要、权限状态、CH341 VID:PID/USB 授权、热点后端与连接阶段、音视频流起停及错误阶段，不写热点 SSID/BSSID、蓝牙设备名/地址、密码、MFi token 或导航目的地，也不记录原始音视频包。文件超过 10 MB 时轮转为 `xcertplay.previous.log`。部分车机文件管理器会隐藏或限制 `Android/data`；可在设置页点击 **Save diagnostic logs (ZIP)**，通过系统文件选择器把两份日志打包保存到内置存储的可见目录（例如 Documents），不需要安装分享应用。**Share diagnostic logs** 仍需要车机上有支持接收文件的应用。无线权限被拒绝时，界面会显示需在应用设置中开启的权限，并在日志记录权限结果。分享或发送前请检查并删除 VIN、账号及个人信息。

要直接传到群晖，在设置页“Synology upload”中填写 QuickConnect ID、DSM 账号、File Station 目标目录（默认 `/docker/navitool-dashboard/data/logs`），再点 **Upload diagnostic logs to Synology**。也可填备用 DSM 根地址，例如 `https://nas.example:5001`；远程连接使用 HTTPS，HTTP 备用地址仅接受可信局域网 IP/主机。密码由 Android Keystore 加密保存，Synology 凭据不纳入 Android 备份。上传使用 DSM File Station API；建议使用仅对目标目录有写入权限的 DSM 账号。上传由用户手动触发，不会自动外传。

## Features

- 面向 Android 和 Android Automotive OS 的 CarPlay 主机应用。
- 支持 CH341 桥接 MFI 芯片、原生 `/dev/i2c-N` 设备连接的 MFI 芯片、本地证书/私钥文件和 Remote MFI 认证（API 见下）。
- 支持 CarPlay 有线或无线连接。
- 上游项目声称可触发 CarPlay Ultra 提示，但协议栈未测试/未完成；这不代表领克 09 EM-P 支持 CarPlay Ultra。
- 支持语音、导航、音乐多通道音频输出并 mapping 至 Android 的对应通道。
- 支持动态 Activity resize ，并自动重新握手至新的分辨率。
- 支持车机位置回传。
- 支持 Android 9 (API 28) 。

## 使用方法

1. 通过蓝牙将 iPhone 与车机配对。
2. CarPlay 视频流尚未启动时，点击右下角的设置按钮；也可以用三指向下滑动打开设置页面。
3. 确认所有设置均已按需配置。
   如需启用另一种手势，打开 `More gestures to Settings page`：单指从屏幕左侧 1/8 区域的上 1/4 开始，沿左侧下滑，在下 1/4 区域抬起。
4. 滑动到底部，选择 `Save & Reconnect`。
5. 按照你选择的方式连接 MFi 芯片。
6. 等待连接完成，然后开始使用。

## 当前进度

它运转👍，已在车机/手机平台测试。如果出现部分车机不适配的情况，欢迎提交 issue 并附上日志。日志位于内置存储 `Android/data/com.shilapi.xcertplay/files/logs/xcertplay.log`；也可使用设置页的 **Share diagnostic logs** 导出。

转接板：[CH341-to-MFI](https://github.com/shilapi/ch341-to-mfi-chip)

## 本地 MFI 文件

在 `MFI certificate & signing target` 中选择 `Local files`，然后通过两个 `Choose`
按钮使用 Android 系统文件选择器选择证书和私钥。当前支持 DER PKCS#7 证书（`.p7b`）
及与之匹配的、未加密 DER PKCS#8 私钥（`.pk8`）。应用会在开始连接手机前校验两者是否
匹配，并在 MFI 重连时重新读取文件。

建议把私钥放在受保护的位置。应用不会把证书或私钥复制到偏好设置，只会保存 Android
授予的持久读取权限和文档 URI。

## 工程结构

| 路径 | 用途 |
| --- | --- |
| `common/` | 两个目标共用的 CarPlay 宿主界面、设置、持久化和应用资源。 |
| `mobile/` | 使用共享 CarPlay 主机界面的 Android 应用。 |
| `automotive/` | 使用共享主机界面并支持高级音频通道映射的 Android Automotive OS 应用。 |
| `shared/` | Car App Library 代码，以及 CH341、I2C、MFi、iPhone、iAP2、NCM、VPN、AirPlay 和媒体实现。 |

## Remote MFI 功能

Remote MFi 客户端把远程服务当作一块 MFi 芯片远程调用，抑或是采用 BAA 认证，通过远程进行认证免去了本地连接 MFI 芯片进行认证的流程。

### 端点

| Method | Path | 用途 | Request body | Success response | 失败 response |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/mfi/certificate` | 获取 MFI 芯片版本、证书类型和证书内容，客户端首次调用后缓存 | 无 | 证书 JSON | `{"detail":"..."}` |
| `POST` | `/mfi/sign` | 对 challenge 签名 | `{"challenge":"...","requestId":"..."}` | `{"signature":"..."}` | `{"detail":"..."}` |
| `POST` | `/mfi/reset` | 请求重置远程 MFI 芯片 | `{}` | `{"detail":""}` | `{"detail":"..."}` |

（可选）采用标准 Bearer Authentication 进行验证。

**当前仅测试了 BAA Authentication**

## 环境要求

- 启动 Gradle 需要 JDK 17 或更高版本；daemon 通过 Gradle toolchain 解析 Java 25。
- Android SDK Platform 37。
- Android 9（API 28）或更高版本。
  在 Android 9 上不可用 Wi-Fi P2P 5 GHz 模式，应用会改用 LocalOnlyHotspot。
- Android NDK `28.2.13676358`。
- 硬件验证需要支持 USB Host/OTG 的 Android 设备以及 MFi 硬件。

## 构建

在 Windows PowerShell 中：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :shared:testDebugUnitTest :common:lintDebug :mobile:lintDebug :automotive:lintDebug :mobile:assembleDebug :automotive:assembleDebug
```

在 macOS 或 Linux 中：

```bash
./gradlew :shared:testDebugUnitTest :common:lintDebug :mobile:lintDebug :automotive:lintDebug :mobile:assembleDebug :automotive:assembleDebug
```

构建未签名 release APK：

```powershell
.\gradlew.bat :mobile:assembleRelease :automotive:assembleRelease
```

## 致谢

感谢 [LIVI](https://github.com/f-io/LIVI) 项目为本项目提供了重要参考。
感谢 [showcase](https://github.com/amineross/showcase) 项目为本项目的 BAA 认证提供重要参考。

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 许可。
