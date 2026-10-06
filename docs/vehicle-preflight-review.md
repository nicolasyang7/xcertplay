# 领克 09 2023 EM-P 封包前检查

检查日期：2026-09-27。候选版本：1.2.4-lynk09-hotspot-fix1（1205）。

## 当前状态

源码检查已通过，允许生成实车测试 APK。此报告不能证明已通过实车验证。

| 检查 | 结果 |
| --- | --- |
| shared 单元测试 | 108 项，0 失败、0 跳过 |
| common 单元测试 | 4 项，0 失败、0 跳过 |
| shared Lint | 0 错误，10 警告 |
| common Lint | 0 错误，81 警告 |
| mobile Lint | 0 错误，3 警告 |
| automotive Lint | 0 错误，3 警告 |

最终验证构建：BUILD SUCCESSFUL，耗时 11 分 38 秒，148 个任务。完整报告已保存到 artifacts/verification。APK 已生成并核对：versionCode 1205，minSdk 28，APK v2 签名验证通过，与前版同一调试证书；ARM64 I2C 库存在，ZIP 完整性通过。

## 本轮修复

- LocalOnlyHotspot：跨实例串行请求，取消期间保留系统回调，迟到的 reservation 及时关闭；关闭过程中释放启动阶段已取得的 reservation。
- 控制器：热点发布与关闭同步，过期的无线连接任务不能重新挂载热点。
- 日志：刷新及关闭等待限定为 2 秒，导出文件复制移到后台执行；新增日志队列顺序与关闭测试。
- 音频：Opus 编码器配置或启动失败时释放已创建的 MediaCodec。
- 群晖：发现所得地址也检查 HTTPS / 局域网 HTTP 要求。
- 测试工程：补齐已有 Android 9 红绿灯识别测试所需 Robolectric 依赖；新增热点请求互斥回归测试。

## 检查范围和限制

复查 Android 9 权限与 API 分支、领克预设、USB CH341 接口、热点生命周期、后台连接、音视频资源、日志导出和上传。XML 解析及重复 Kotlin import 检查已通过。生产代码已全量编译；完整测试与四个模块 Lint 已通过。

旧构建目录中存在大量 macOS dataless 占位文件，导致资源链接和 Java 资源合并等待。最终使用 /private/tmp/xcertplay-vehicle-build 作为新的构建输出目录，并使用本机 Android SDK build-tools 36.0.0 的 aapt2，全量验证通过。该替换仅用于构建命令，不改变车辆运行参数。

实车仍必须验证：USB VID/PID 与权限、芯片认证和签名、热点启动及重复重连、iPhone 配对与投屏、触摸、音乐、麦克风与 Siri、熄屏/恢复及日志上传。当前没有连接车机，无法确认这些项目。

现有项目 README 明确指出 CarPlay Ultra 协议栈未测试/未完成；本轮不能确认 Ultra 可用。

## 权限复查与警告

热点、Wi-Fi Direct、蓝牙和麦克风调用明确捕获权限拒绝并清理资源。本机蓝牙 MAC 读取先检查可选系统权限，无权限或读取失败时使用既有配置地址；仅此处针对静态权限检查作局部说明。普通安装仍需实测配置地址是否适合无线握手。

其余警告包括旧 API 常量内联、新版接收器标志的旧平台分支、界面文本/无障碍、依赖版本建议及未使用资源。USB Lockdown 专用 TLS 通道沿用上游的不校验对端证书实现，限制在 USB 配对传输使用；群晖上传没有使用该信任管理器。Lint 通过不代表这些警告已全部消除。

## APK 交付

路径：artifacts/xcertplay-lynk09-hotspot-fix1.apk。

SHA-256：`859af3fd378f690434e3a50aaf5d382184ed0bd700fb8960fc22d0c67e68242d`。

安装前在车机设置中强行停止旧版 xcertplay，释放旧版遗留的热点请求；然后覆盖安装。Android 9 保持位置开关开启并授予定位、麦克风权限，USB 小板授权后启动连接。依次验证首次连接、断开后连续重连、后台返回、音乐、触摸和 Siri。若有失败，通过应用导出日志或手动上传到群晖。

## 日志导出修复（1206）

新增 **Save diagnostic logs (ZIP)** 按钮：用 Android `ACTION_CREATE_DOCUMENT` 文件选择器，将当前和轮转日志写入用户选择的文件位置，不依赖分享接收应用，也不需要外置 SD 卡。`:mobile:assembleDebug` 成功；APK versionCode 1206、versionName 1.2.4-lynk09-log-export1、APK v2 签名验证通过，签名证书与 1205 相同。SHA-256：`cb2a7673a6d083b1fd9d0054d9640109c62fbb31ddc229bd924345d70dcbd5f6`。新增公共界面已编译并打包；上一轮 112 项单元测试和四模块 Lint 是对 1205 源码的结果，尚未针对这次 ZIP 导出增量重新运行。

无线权限提示：启动路径先检查麦克风，再检查无线权限；Android 9 无线模式请求 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`。Android 不允许应用静默授予这些运行时权限。

## 权限失败提示修复（1207）

权限请求回调现在记录具体未授权的 permission，并将界面阶段更新为要在应用设置中开启的权限，避免静止显示 Preparing CarPlay。Android 仍要求用户确认授权，应用不会也不能自动授予运行时权限。与 ZIP 导出合并在 1207。shared 108 项、common 4 项单元测试通过；common、mobile Lint 通过，四模块完整检查在 1207 构建中成功；APK v2 签名验证通过，签名证书与旧版相同。SHA-256：`b55458a00a41717f24702d6ce48b4a0f100d280074ab05e0cd9f603e1e5c0a94`。

## Android 9 系统定位自检（1208）

19:18 日志中的 `location_enabled=false` 原指 CarPlay 位置上报选项，不是 Android 系统定位总开关。1208 将日志字段改为 `location_reporting`，另记 `system_location_enabled`。在 Android 9 无线自动热点模式下，启动前检查系统定位总开关；关闭时停在明确的阶段提示，并提供打开系统定位设置的对话框。定位权限已授予并不等于系统定位开关已打开。系统定位打开后，从设置返回会自动重新检查并继续启动。

本地 `:shared:testDebugUnitTest :common:testDebugUnitTest :mobile:assembleDebug` 已成功；APK versionCode 1208、versionName 1.2.4-lynk09-location-check1，APK 签名证书与 1207 相同，ZIP 完整性通过。路径：`artifacts/xcertplay-lynk09-location-check1.apk`；SHA-256：`9cfd10bc8e596db94044021c4d5b40be4f153852d294e2da1d03031744213b11`。这版只修复自检和提示；若系统定位已开启但热点仍失败，必须获取 `wifi/ap starting` 后的最终错误日志再继续定位。
