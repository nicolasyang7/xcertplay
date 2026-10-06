# XcertPlay × 2023 领克 09 EM-P 适配评估

本仓库包含 XcertPlay 上游源码及领克 09 EX11 / 2023 EM-P 适配记录。目标是在车辆 Android 车机运行 XcertPlay，让 iPhone 建立普通 CarPlay 会话。

**当前结论：根据之前在领克 09 EX11 车机上的实测采集，适配 XcertPlay 普通 CarPlay 项目的路径已成立，项目值得继续推进；MFi 小板焊好后仍需完成认证与投屏联调。CarPlay Ultra 不属于当前可确认的交付能力。**

## 先厘清系统结构

XcertPlay 是运行在 Android 车机上的 CarPlay 接收端。它通过 CH341 I²C 桥或车机原生 I²C 与 MFi 芯片通信，接收 iPhone 的 CarPlay 会话并把界面/音频呈现在 Android 车机上。因此 MFi 板不是插入原车 USB 后即可给原厂车机增加 CarPlay 的“解锁芯片”。

之前的实车采集记录已经确认项目不是只根据 8155 芯片做推测：领克 09 EX11 车机侧曾检查到 `com.geely.carlink`、蓝牙 HFP/PBAP/A2DP 功能，以及 `wlan0`、`p2p0` 网络接口；车机系统可运行 Android 应用/调试工具，并已完成 NaviTool/HUD 和车载音频通路的只读检查。此前采集还记录了 Lightning 接入没有枚举出 Apple/MFi USB 设备，原厂 CarLink 只提供蓝牙相关功能，没有形成完整 MFi+iAP2+AirPlay CarPlay 会话。这些结果支持“通过 XcertPlay 接收端 + 独立 MFi 芯片板补齐 CarPlay 链路”的适配方向。

当前阻塞项是 MFi 板还在制作/焊接，尚未在这辆车上验证 CH341F USB 枚举、MFI337S3959 认证、iAP2 会话和 CarPlay 音视频。因此结论应表述为：**车机平台和接入路径适配，CarPlay 功能等待板卡实测验收**。此前资料提到 09 EM-P 使用高通骁龙 8155；但这只是平台背景，适配判断主要依据车机实测采集和 XcertPlay 结构。

已从 [`shilapi/xcertplay`](https://github.com/shilapi/xcertplay) 获取上游 `master`（克隆时 HEAD：`8885cba`, version 1.2.4），并加入一个可从设置页手动应用的 **Lynk & Co 09 EX11 preset**。它启用无线 CarPlay、CH341 USB MFi 和 Lynk & Co 车机身份字段；Android 9 自动选用 LocalOnlyHotspot。它不会关闭原厂 CarLink，也不会更改系统权限。应用 preset 后需点“Save and reconnect”；首板和 MFi 认证仍须实车验收。

## 针对 09 EM-P 的现状

| 项目 | 已知信息 | 对适配的影响 |
|---|---|---|
| 车型 | 2023 款领克 09 EM-P（具体配置、生产批次及车机版本待车主补充/实测） | 车型年份不足以确定车机软硬件权限 |
| 座舱芯片 | 公开车型资料称采用高通骁龙 8155 | 算力不等于可安装第三方 APK 或访问硬件接口 |
| 原厂系统 | 实车采集记录确认车机可以运行 Android 应用/调试工具；记录中出现 `com.geely.carlink`、`wlan0`、`p2p0` | 具备开展接收端集成的条件；车机具体系统版本号仍应留档 |
| MFi 转接板 | 前期硬件方案为 CH341F + MFI337S3959 USB-A 3.3V 板 | 要连接运行 XcertPlay 的 Android USB Host；仅有原厂 USB 插口不构成兼容证明 |
| 普通 CarPlay | XcertPlay 仓库说明支持 Android 车机接收有线/无线 CarPlay；之前实车采集确认原厂 CarLink 未建立完整 CarPlay 数据会话 | 项目方向与实车现状匹配；待 MFi 板接入后验证完整 CarPlay 会话 |
| CarPlay Ultra | XcertPlay README 只称可触发 iPhone Ultra 提示，协议栈未测试/不完整；Apple 要求车企进行深度整车集成 | 不将其作为本项目在 09 EM-P 上的承诺或验收目标 |

## 之前采集到的车辆信息与判断

之前任务中的实车分析/只读采集记录包含以下现象：

- iPhone 可通过蓝牙连接车机，HFP 电话、PBAP 通讯录和 A2DP 蓝牙媒体可用；这些功能本身不等于 CarPlay。
- 车机侧存在 `com.geely.carlink`，但原厂 CarLink 当时没有建立 MFi 认证、iAP2 控制会话和 AirPlay 视频/音频链路。
- 采集记录指出 `wlan0` 未进入 CarPlay 数据状态、`p2p0` 未建立 CarPlay 会话；Lightning 接入时也没有枚举出 Apple/MFi USB 设备。
- 车机环境已用于 NaviTool/HUD 和音频链路调查，所以项目不是要把 MFi 板当作原厂 USB 车机的“解锁芯片”，而是由 XcertPlay 在可运行 Android 应用的车机侧承担 CarPlay 接收端。

因此此前的实际判断是：**领克 09 EX11 车机适合继续做 XcertPlay 接收端适配；原厂 CarLink 单独不能提供所需的完整 CarPlay 接收能力。** 适配的最后一段仍需等待板卡焊接后，在车上确认 CH341F/MFi 通信与 CarPlay 会话。

## 推荐的适配路线

### 路线 A：原车 Android 车机运行 XcertPlay（当前项目路线）

1. 延续之前已采集到的车机 Android/CarLink、Wi-Fi 接口、HUD 和音频环境记录，并补齐车机版本信息。
2. 在当前可用的 Android 车机运行环境中启动 XcertPlay；确认最终集成形态所需的应用权限和开机启动方式。
3. 使用已焊接并完成台架检查的 MFi 小板接入可用 USB Host/OTG；确认供电、枚举和应用 USB 权限。
4. 接入已焊接并完成台架检查的 CH341F + MFI337S3959 板，验证 MFi 认证、iPhone 配对、CarPlay 会话、音频输出、触控/旋钮和方向盘按键。
5. 若失败，导出 XcertPlay 日志并记录车机 USB 枚举结果，再判断是 USB 权限、CH341 驱动、系统限制还是音频/显示集成问题。

### 路线 B：原厂系统不允许安装/访问硬件

将 XcertPlay 运行在独立 Android 主机上，再评估其与原车屏幕、音频及控制接口的连接方式。屏幕输入、音频切换和车辆按键都必须单独验证；不要把 MFi 板直接接入车辆 CAN 或未知针脚。

## 实车验证清单

| 阶段 | 检查项 | 通过标准 |
|---|---|---|
| 车机信息 | 配置/年款、车机版本、Android 版本/API | 信息完整可复现 |
| 安装权限 | 安装、启动 XcertPlay；重启后可再次运行 | 不需破坏系统完整性，应用可稳定启动 |
| USB 主机 | USB Host/OTG、供电、设备枚举、权限授予 | CH341 可稳定识别，无反复断连 |
| MFi | XcertPlay 能读取 MFi 芯片并完成认证 | 日志无认证错误，iPhone 接受连接 |
| 会话 | 有线与无线连接分别验证 | iPhone 进入普通 CarPlay 会话并持续运行 |
| 体验 | 画面比例/分辨率、触控、声音、麦克风、方向盘控制 | 每项分别记录；未测项目不写“通过” |
| 稳定性 | 冷启动、热启动、断连重连、长时间运行 | 记录断连、温升和异常日志 |

**当前不是从零确认车机是否有适配可能：之前的采集已支持继续针对该车开发。** 但在首块板完成 USB 枚举、MFi 认证及 iPhone CarPlay 会话之前，不应把功能状态写成“实车 CarPlay 已通过”，也不建议批量焊完全部板卡。首板成功后再做剩余板卡和固定外壳。

## 需要车主/实车补充的资料

- 车辆准确配置名称、出厂年月、销售市场；
- 车机“关于本机”页面照片和软件版本号；
- USB 口位置、标识、说明书中关于数据/CarPlay/OTG 的描述；
- 当前车机软件版本/API level、既有 XcertPlay 安装/启动方式及所需权限记录；
- 若已试装：XcertPlay 版本、安装/启动结果、USB 枚举结果和应用日志。

请遮盖 VIN、车牌、手机号、账号等个人信息。

## 前期硬件资料

旧工作目录中的焊接交接记录显示，板卡采用以下基础 BOM：

- CH341F ×1；
- MFI337S3959 ×1；
- HR1117V-3.3 ×1；
- 10 µF 0402 ×2、0.1 µF 0402 ×3；
- 2 kΩ 0402 ×1、2.2 kΩ 0402 ×2、4.7 kΩ 0402 ×1；
- USB-212-BCW ×1。

旧记录还发现 PCB/网表中有 R5 与 BOM/位号表不一致的迹象，且涉及 CS0 电阻配置。**首次上电前需对照当前实际 Gerber、原理图和实物位号确认，不要仅凭旧 BOM 推断焊接方式。**

当前工作区没有该板的源文件或 Gerber，所以本仓库不复制、不修改硬件设计文件，也不把旧工作区的检查当成最新硬件版本核验。

## 参考资料

- [XcertPlay 项目 README](https://github.com/shilapi/xcertplay)：Android 车机接收端、MFi/CH341 接入、系统要求和 Ultra 状态。
- [CH341-to-MFI 硬件项目](https://github.com/shilapi/ch341-to-mfi-chip)：转接板用途和 BOM。
- [Apple：CarPlay Ultra 发布与合作车企信息](https://www.apple.com/newsroom/2025/05/carplay-ultra-the-next-generation-of-carplay-begins-rolling-out-today/)。
- [Apple Developer：CarPlay/CarPlay Ultra 车企接入说明](https://developer.apple.com/carplay/)。
- [Apple 中国大陆 CarPlay 车型列表](https://www.apple.com.cn/ios/carplay/available-models/)：截至本次查阅没有列出领克 09；该清单用于原生车型支持参考，不能单独证明 XcertPlay 的 Android 接收端路线必然失败或成功。
- [领克 09 EM-P 官方车型页](https://www.lynkco.com.cn/cars/2509emp)：当前官方页面为新款车型资料，不可直接用来证明 2023 款具体车机权限。

## 更新记录

- 2026-09-26：结合前期车机只读采集修订结论：09 EX11 的 Android 接收端适配路径已成立；MFi 板和完整 CarPlay 会话仍待首板实测；将 Ultra 排除在可承诺的验收目标之外。
