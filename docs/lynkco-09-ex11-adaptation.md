# 领克 09 EX11 / 2023 EM-P 适配实施记录

> 本文把前期对 EX11 车机的只读采集整理成 XcertPlay 的适配基线。当前源码包含一个手动应用的车辆 preset；完整实车兼容仍需在车辆和首块 MFi 板上验收。

## 已采集车辆基线

| 项目 | 前期实车采集记录 | 适配决策 |
|---|---|---|
| 车型平台 | 领克 09 EX11；本项目目标为 2023 EM-P | 以该车机平台作为目标设备，收到车机版本变化时重新核对 |
| SoC | Qualcomm Snapdragon 8155 | 性能预期足够，不据此推断接口权限 |
| OS | Android 9 / API 28，ARM64 | 使用 `mobile` 标准 Android 应用作为首个部署目标；满足 XcertPlay Android 9 最低要求 |
| 主屏 | 1920 × 1080，横屏，density 160 | 首轮采用系统主显示的动态尺寸；不把 HUD 当作 CarPlay Surface |
| 蓝牙 | iPhone 配对；HFP、PBAP、A2DP 曾可用 | 保留原车电话/通讯录蓝牙链路，不能把蓝牙可用误判为 CarPlay 成功 |
| Wi-Fi | 车机采集到 `wlan0`、`p2p0`；设备支持 AP/P2P 的迹象 | Android 9 首选 XcertPlay 的 `LOCAL_ONLY_HOTSPOT` 路径；不先写死 P2P/固定信道 |
| I²C | `/dev/i2c-0/1/2` 存在，但权限 `crw------- root root` | 普通 APK 不走板载 I²C；不改设备节点权限，不触碰动力/安全 ECU 总线 |
| 原厂 CarLink | `com.geely.carlink` 存在；采集时无完整 MFi/iAP2/AirPlay 会话 | 需要避免 CarLink 同时抢占投屏会话；先用用户可控方式验证，不强杀系统服务 |
| USB | 普通 U 盘曾被车机识别为 storage 并忽略；Lightning 接入未枚举 Apple/MFi USB 设备 | MFi CH341 板应连到运行 XcertPlay 的 Android USB Host；先验证 USB 权限和 CH341 枚举 |
| HUD/导航 | 前期已调查 NaviTool 接收端和高德广播入口 | 后续单独实现导航事件转广播；不耦合 CarPlay 视频 Surface |
| 音频 | 目标为音乐、导航、电话、语音各走相应逻辑通道 | 保留 XcertPlay channel 语义；Android 9 实车路由逐通道实测后再固化 |

## 2026-09-29 EX11 热点实车诊断

这次运行的应用是 1208，系统日志识别车机为 `QUALCOMM/EX11`、Android 9/API 28。应用确认无线运行时权限、麦克风权限和系统定位总开关均可用。CH341 `1A86:5512` 已获 USB 权限，MFi 协处理器在 I²C 地址 `0x11` 返回版本及 908 字节配件证书，并进入 `mfi/ready`。这仍未证明 iPhone 的 challenge/sign 和完整 CarPlay 会话成功。

卡点是 Android `LocalOnlyHotspot` 回调：应用发起请求后，系统 Wi-Fi 日志约 1 秒内报告 `wlan1` 在本地热点模式 `2` 达到 AP enabled（状态 `13`），但应用没有观察到 `onStarted` 或 `onFailed`，因此未取得 reservation、SSID 和口令，60 秒后超时。之后的自动重试被仍待终止的前次请求挡住。证据见 `artifacts/diagnostics/2026-09-29-hotspot/`。现有证据不能区分 EX11 定制 Wi-Fi 服务漏发回调，还是工作线程分发路径不兼容。

针对已实测的 `EX11 / ex11_high_12g + API 28`，下一轮源码只尝试 Android API 明确支持的主线程回调分发，并在回调入口记录到达情况；若仍无终止回调，暂停无效自动重试。其他 Android 设备继续使用上游式工作线程回调。此变更不读取隐藏热点配置、不指定 SSID/信道，也不自动停用原厂 CarLink。车机具体 OSN 软件版本尚未记录，不能把该改动声明为所有 OSN 版本已验证。

上游 [Issue #20](https://github.com/shilapi/xcertplay/issues/20) 曾出现 LocalOnlyHotspot 错选 `rmnet0` 接口、2.4 GHz 下无画面的报告；这是其他设备的案例，不能直接证明 EX11 的后续故障。EX11 的系统日志明确显示本地热点接口为 `wlan1`、频率为 2412 MHz。源码因此只在该实测机型上要求 `wlan1`，并将实际接口名写入日志；如果 `wlan1` 不可用，保留“无可用主机地址”的明确错误，不选移动网络接口。Android 9 的 `WifiConfiguration.apChannel` 若在 EX11 上无法读取，则按项目既有的手动热点语义向 iAP2 报告 `0`（自动），避免虚构信道或中断启动。未强制 5 GHz，待实车看到热点回调及后续 iPhone 行为再判断。

## 项目路径

```text
iPhone
  ├─ Bluetooth：发现/配对
  └─ Wi-Fi：无线 CarPlay 数据流
       ↓
领克 09 Android 9 主机运行 XcertPlay
  ├─ USB Host → CH341F (VID:PID 1A86:5512) → I²C → MFI337S3959
  ├─ CarPlay 主画面 → 1920×1080 中控主屏
  ├─ MEDIA / NAVIGATION / PHONE / ASSISTANT → Android 音频属性 → 原车 Audio HAL
  └─ CarPlay 导航事件 → NaviTool 高德格式广播 → HUD
```

MFi 认证仍是当前关键依赖。第一阶段以已制作的 CH341F + MFI337S3959 USB 板为目标；未焊好前可用经授权且具有真实认证能力的 Remote MFi 服务进行开发验证。不得伪造证书或跳过认证。

## 完善版预设方案

按“先连通、再调体验、最后做集成”的顺序落地。设置入口目前只应用第一阶段的基础值；其它选项应在真实车机上逐项验收后再存成默认值，避免把前期推测固化成车辆事实。

### 阶段 1：首板连通配置（当前可用）

在 XcertPlay 设置页点击 **Apply Lynk & Co 09 EX11 preset**，检查并应用以下值，再点击 **Save and reconnect**：

| 设置 | 首轮值 | 说明 |
|---|---|---|
| 传输 | Wireless CarPlay | 已知车机具备 `wlan0` / `p2p0`；不是完整会话通过证明 |
| Android 9 Wi-Fi 会话 | LocalOnlyHotspot | API 28 首轮兼容路径；不预设 SSID、密码、频段或信道 |
| MFi 目标 | USB/CH341 | 适用于计划中的 CH341F + MFI337S3959 板 |
| USB VID:PID | 预期 `1A86:5512` | 以焊好板卡实际 USB 描述符为准；若不匹配，按实测枚举值更新 |
| 车机标识 | Manufacturer `Lynk & Co`；Model `09 EX11`；OEM `Lynk & Co` | 仅是 iAP2/界面身份字符串，不是车机认证字段 |
| 定位上报 | 关闭 | 首次连通不需要主动共享车机位置；确认导航用途和权限后再评估 |
| CarLink | 保持原样 | 如有会话冲突，先由用户手动结束/切换并记录，不自动禁用系统应用 |

应用会按 Android 版本申请无线所需权限。首轮把车机接到稳定电源并保持前台运行；USB 授权弹窗选择允许。不要在同一轮同时改变 AP、帧率、分辨率、音频映射等设置，以便定位问题。

### 阶段 2：显示与无线调优

| 项目 | 建议起点 | 调整依据 |
|---|---|---|
| 显示器 | XcertPlay 动态读取主屏 | 不将采集到的 1920×1080 写死到 profile；检查实际安全区域、刘海/圆角和横屏方向 |
| 画面缩放/安全区 | 默认值 | CarPlay 成功显示后再调到边缘完整、不遮挡车机状态区；记录每次更改 |
| 帧率 | 默认值先跑通 | 只有掉帧/发热等现象才逐级调低；8155 型号本身不能替代实测 |
| 无线方式 | LocalOnlyHotspot 先验；必要时按应用支持项对比 | 记录 iOS 版本、连接耗时、频段/信道（系统可见时）、断连/重连和车机热点并存情况；勿手动指定未经验证的信道 |
| HEVC | 保持应用默认 | Android 9 页面说明 HEVC 软件解码选项不可用；成功出画面后再看日志与稳定性，不为追求画质强开未验证配置 |

### 阶段 3：车载功能集成

- **音频**：保持默认 mobile 兼容路由先验 MEDIA、NAVIGATION、PHONE、ASSISTANT；每类单独听测、观察 AudioFocus 和系统日志。Android 9 实测前，不启用仅适用于 AAOS 的高级 audio-bus 映射，也不承诺 Bose/DSP 分区已经命中。
- **原车电话**：验证 CarPlay 音频与原车 HFP 来电/接听的焦点切换，确认结束通话后音乐恢复。首轮不要同时让两个系统处理同一通话。
- **触控/实体键/麦克风**：分别记录主屏触控映射、旋钮/方向盘键入口、语音上行麦克风和回声情况；无已确认接口前不写固定键值映射。
- **HUD 导航**：先证明 iAP2 RGI 消息确实从当前 XcertPlay 版本到达，再验证 NaviTool 接收端 action、包名和字段。任何字段都按实际 payload 映射；不要因预设启用广播或合成行程数据。
- **开机启动**：先关闭 Auto-start，完成稳定性与权限验收后再手动打开并测试熄火/启动周期；车机是否允许后台启动需实车确认。

### 每轮验收记录

每次只改一类变量，记录：车机软件版本/API、XcertPlay 构建版本、iPhone/iOS 版本、板卡版本、USB VID:PID/授权结果、热点建立和配对阶段、首帧耗时、断连原因、画面、音频类型、麦克风、电话焦点、温度/长时稳定性。日志分享前移除 VIN、账号、SSID/密码、令牌及个人导航目的地。

建议通过条件：连续 30 分钟无崩溃/异常断连；冷启动和用户主动断开后均能恢复；MEDIA/NAVIGATION/PHONE/ASSISTANT 各自完成单独验收。此为项目联调门槛，不等同于 OEM 量产级认证。

## 首版应用配置规格

当前显式 profile 已有基础项；以下尺寸、广播 action/package 仍然是规划项，只有经过车端实测后才应实现为默认配置：

```kotlin
data class LynkCo09Profile(
    val model: String = "EX11",
    val androidApi: Int = 28,
    val displayWidth: Int = 1920,
    val displayHeight: Int = 1080,
    val wirelessMode: WirelessHotspotMode =
        WirelessHotspotMode.LOCAL_ONLY_HOTSPOT,
    val mfiTarget: MfiTarget = MfiTarget.USB_CH341,
    val ch341VendorId: Int = 0x1A86,
    val ch341ProductId: Int = 0x5512,
    val navigationBroadcastAction: String =
        "AUTONAVI_STANDARD_BROADCAST_SEND",
    val navigationPackage: String = "com.navi.link",
)
```

此为规格示意，需按上游实际类名和配置机制实现。若实车 USB Host 不支持 CH341 或首板未就绪，开发配置可改用 Remote MFi；不要把不可读的 `/dev/i2c-*` 作为默认后端。XcertPlay 当前设置允许用户选择认证方式，因此不要把该选择永久硬编码为 `USB_CH341`。

## 实施拆分

### A. 设备 profile 与设置入口（已实现首版）

- 复用 `mobile` 模块作为首个部署目标；是否采用 `automotive` target 需按该车机实际安装/权限形态实测确认，不能只因采集记录称 Android 9 就排除 AAOS。
- 设置页提供“Apply Lynk & Co 09 EX11 preset”，定义位于 `common/.../LynkCo09Profile.kt`。
- preset 设置无线 CarPlay、USB/CH341 MFi、`Lynk & Co` / `09 EX11` 身份字段；Android 9 选择 LocalOnlyHotspot。
- 如果缺少无线所需的 Android 运行时权限，应用会在用户应用 preset 时发起常规权限请求。
- preset 改动暂存在设置状态中，用户还需点击“Save and reconnect”确认应用。
- 记录机型、系统/API、显示尺寸、USB 设备信息和音频设备信息，不采集 VIN、账号等个人数据。
- 不自动禁用/卸载 CarLink；先通过可恢复的用户操作验证会话冲突。

### B. MFi 与 USB Host

- 目标板：CH341F + 已编程有效的 MFI337S3959。
- USB 枚举预期：`1A86:5512`。在 Android USB Host API 中申请用户授权并检查断连/重连。
- 验收顺序：供电无短路 → 3.3 V 正常 → CH341 枚举 → I²C/MFi 响应 → certificate 读取 → challenge/sign → iPhone 建立 CarPlay。
- USB-A 公头 MFi 板必须接运行 XcertPlay 的主机 USB Host 口，不等同于把认证板插到原厂 USB 媒体口。
- 不接触 `/dev/i2c-*`；不要请求 root，不改系统分区。

### C. 无线 CarPlay 与屏幕

- Android 9 首选 `LOCAL_ONLY_HOTSPOT`，完成 Bluetooth discovery 后验证 Wi-Fi 切换、频段、信道、DHCP 和断连恢复。
- 先通过 Android `Display` API 获取真实主屏尺寸；1920×1080 作为前期采集基准，不硬编码 Surface 尺寸。
- 目标 Surface 是中控主屏。HUD 由 NaviTool 路径处理，避免挪用或覆盖 HUD 显示。

### D. 音频路由

保持 XcertPlay 对 CarPlay 音频类型的区分：

| CarPlay 内容 | Android 用途/内容类型 | 目标路由 |
|---|---|---|
| 音乐 | `USAGE_MEDIA` / MUSIC | 原车媒体（Bose） |
| 导航播报 | `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` / SPEECH | 原车导航提示通道 |
| 电话 | VOICE_COMMUNICATION / SPEECH | 原车通话链路 |
| Siri/语音助手 | ASSISTANT / SPEECH | 原车语音助手通道 |

不要将导航播报重映射为普通媒体，也不要由 NaviTool 再次播放导航语音。Audio HAL 是否实际区分路由必须在车辆上做听音和焦点日志验收；仅创建不同 `AudioAttributes` 不代表已进入对应 Bose DSP 通道。

### E. CarPlay 导航事件 → NaviTool HUD

这是单独的**数据投递链路**，不把 CarPlay 视频 Surface 搬到 HUD：iPhone 高德在 CarPlay 会话中输出路线引导数据，XcertPlay 从 iAP2 导航消息解码；桥接层归一化后向 NaviTool（若其实际接收高德兼容广播）发送 Android Intent；NaviTool 再更新车辆 HUD。HUD 是最后的显示端，不应假设它直接理解 CarPlay 私有事件。

公开 XcertPlay README 确认其包含 iAP2、媒体实现和 CarPlay 音视频接收能力，但 README 没有公开导航消息订阅/API。公开的高德车机标准广播资料描述了 `AUTONAVI_STANDARD_BROADCAST_SEND` / `AUTONAVI_STANDARD_BROADCAST_RECV` 两个 action：输出方向是 SEND，`KEY_TYPE=10001` 携带引导信息；`10019` 是状态/昼夜等信息，`13012` 是车道线 JSON，`60073` 是红绿灯扩展数据。协议副本和第三方接收器项目能作实现参考，但这不能证明 NaviTool 的具体版本开放了同一 receiver。

源码检查（2026-09-27）已在本仓库确认消息 ID 和接收链路：`0x5200` 是 XcertPlay 发送的 `StartRouteGuidanceUpdates`；`0x5201`、`0x5202`、`0x5204` 分别登记为来自 iPhone 的 `RouteGuidanceUpdate`、`RouteGuidanceManeuverUpdate`、`LaneGuidanceInfoUpdate`；`0x5203` 是停止订阅请求。wired/wireless 控制客户端都会发送订阅，收到除控制消息外的帧后会调用 `onIncoming`。所以问题不是缺订阅或不知道事件号，而是这些 endpoint 目前只在 registry 中以 opaque body 登记，尚无导航字段 schema/decoder，且 `CarPlayController` 的有线调用传空 lambda、无线调用省略 callback，导航帧没有被消费。

#### XcertPlay 解码侧需要配合的接口

XcertPlay 侧建议提供一个**进程内、只读的导航事件接口**给 HUD 广播桥，不让 HUD 逻辑依赖底层 iAP2 字节流，也不把广播发送塞进协议解析器。订阅入口已经存在，下一步应补 route-guidance 解码/事件分发并把两个 control client 的 `onIncoming` 接入该分发器。字段定义仍应以 iAP2 schema/样例帧核实结果为准，不能把第三方逆向文档直接当成当前 parser 已支持。

建议交付给 HUD bridge 的最小契约：

```kotlin
interface CarPlayNavigationListener {
    fun onNavigationUpdate(update: CarPlayNavigationUpdate)
    fun onNavigationEnded(reason: NavigationEndReason)
}

data class CarPlayNavigationUpdate(
    val sessionId: Long,
    val sequence: Long,
    val maneuvers: List<CarPlayManeuver>,
    val currentManeuverIndex: Int?,
    val distanceToNextManeuverMeters: Int?,
    val tripRemainingDistanceMeters: Int?,
    val tripRemainingTimeSeconds: Int?,
    val currentRoadName: String?,
    val destinationName: String?,
    val isGuidanceActive: Boolean,
)

data class CarPlayManeuver(
    val type: Int,                 // 保留原始 enum，另提供稳定的归一化方向 enum
    val instruction: String?,
    val distanceAfterPreviousMeters: Int?,
    val afterRoadName: String?,
    val turnAngleDegrees: Float?,
)
```

字段以实际 RGI payload 为准：若 iAP2 没有提供全程剩余距离/时间、当前道路或目的地名称，填 `null`，不要通过 CarPlay 画面 OCR、GPS 猜测或普通 CarPlay 定位事件伪造这些字段。优先保留 maneuver 原始 enum 和原始必要 TLV 的受控诊断记录，同时提供稳定的归一化 turn enum 给 AMap mapper；处理 malformed/截断/未知 TLV 时丢弃该次更新并留带长度和 type 的脱敏日志，不能让解析失败打断 iAP2/AirPlay 主会话。

源码落点目前是 `shared/.../iap2/catalog/Iap2Endpoint.kt`（endpoint ID 登记）、`shared/.../iap2/message/Iap2Messages.kt`（已发送 RGI start 订阅）、`shared/.../transport/Iap2WiredControlClient.kt` 与 `Iap2WirelessControlClient.kt`（接收并透传 `onIncoming`）、以及 `shared/.../orchestration/CarPlayController.kt`（wired 给空 callback、wireless 未提供 callback）。`Iap2FrameFormatter` 已能记录未知/不透明 body 的 parameter ID、长度/摘要和 raw-body preview，可用于受控实车采样；注意日志涉及导航目的地信息，采样文件需按敏感诊断数据处理。

XcertPlay 实现任务可按这个顺序拆：

1. 利用现有订阅与 frame trace，在 iPhone 高德实际导航时采集 `0x5201/0x5202/0x5204` 样例；确认更新频率、payload 字段/嵌套、可选字段和停止/清路线语义。原始 trace 临时受控采集，避免常态把目的地等数据写日志。
2. 增加独立 `RouteGuidanceDecoder` 和 typed endpoint schema，按 TLV 长度边界严格解码；先用脱敏固定样例帧做 parser 单元验证，再接入会话，不改视频/音频线程的阻塞行为。
3. 把更新转换为上面的 `CarPlayNavigationUpdate`；同一 session 内用 sequence 丢弃过期更新，路线重算支持替换 maneuver 列表，session 断开/停止订阅/导航结束明确发 ended/clear。
4. 在 `CarPlayController` 为 wired 和 wireless/tunnel 两条路径提供同一个 `onIncoming` handler；仅解析 RGI 消息，其他帧维持现有处理。handler 应快速把解码结果投递到独立事件流/执行器，不在 iAP2 receive loop 里发送广播或做重活。
5. 通过 listener/Flow 等进程内接口向车型无关层发事件；`AmapBroadcastEmitter` 单独做单位与 maneuver 图标转换，再调用 NaviTool 已核实的 Android 广播契约。
6. 输出脱敏诊断日志：RGI 是否订阅、消息 type/长度、解析成功/失败原因、session start/update/end 和关键字段是否存在。默认不落盘完整目的地或全程路线。

特别注意：公开项目指出 CarPlay 发送的是 maneuver/route guidance 数据，不是现成的高德 `ICON` 图标；转向图标需要按 maneuver type/角度在设备端映射。公开逆向实现也表明 RGI 内容含 maneuver 序列和到下一 maneuver 的距离，但不同导航 App、iOS 版本及接收硬件可能改变实际内容，所以要用 iPhone 上实际运行高德的会话验收，不能仅以 Apple Maps 或合成消息通过作为完成标准。

建议模块边界：

```text
iPhone 高德 → CarPlay/iAP2 导航消息
  → XcertPlay 导航消息订阅（待上游源码确认）
  → CarPlayNavigationDecoder（字段与单位归一）
  → CarPlayNavigationEvent（车型无关模型）
  → AmapBroadcastEmitter（高德车机广播兼容层）
  → 已核实的 NaviTool 接收端 → HUD
```

首期最小字段建议为 `KEY_TYPE=10001` 中 NaviTool 实际需要的转向图标、分段距离、下一道路名、总剩余距离/时间和导航状态；`10019` 状态、`13012` 车道线、`60073` 红绿灯后续逐项加。字段名/类型必须按实际 NaviTool 版本验证；`60073` 是扩展/非基础引导字段，不能作为首个通路是否打通的唯一判断。高德标准广播协议公开资料给出的 10001 字段有 `ICON`/`NEW_ICON`、`SEG_REMAIN_DIS_AUTO`、`ROUTE_REMAIN_DIS(_AUTO)`、`ROUTE_REMAIN_TIME_AUTO`、`NEXT_ROAD_NAME`/`CUR_ROAD_NAME` 等，但不同车机渠道包可能有差异。

要求：

- 只在收到真实且有效的 CarPlay 导航事件时发广播；导航结束/路线清除按已验证的 NaviTool 契约清空状态。不得把 CarPlay 屏幕 OCR 或车机定位推算冒充路线引导数据。
- 先核实 NaviTool 的应用包名、版本、接收 action、是否要求显式 package/component、receiver exported 状态与权限；`com.navi.link` 是公开 Navi-Link 示例工程的包路径线索，不等同于已确认的领克 NaviTool 包名。
- 明确转向枚举映射、距离/时间单位、速度单位、空值策略；仅当对方协议要求时发送坐标和红绿灯等字段。
- Android 8+ 对 manifest 隐式广播有限制；运行时动态注册接收器、显式指定接收包名/组件和系统预装白名单是不同情况，按 NaviTool 实际部署确认，避免随意加全包可见或导出权限。
- 加调试开关和结构化日志；默认不记录完整路线或个人目的地。
- 分三步验收：已知样例 Intent → NaviTool/HUD；CarPlay 实际 iAP2 数据 → 解码日志；完整真导航 → HUD 持续更新及结束清除。每步都留 ACTION、KEY_TYPE、必要 extras 和接收结果，不记录目的地个人信息。

已核实的公开资料：XcertPlay 上游项目 README（仓库模块与 Android 9 支持）；高德车机标准广播协议副本（10001/10019/13012/10019 状态码）；Navi-Link 开源示例（说明有第三方接收器可消费 10001 与 60073）。这些只是开发参照，尚未证明目标 NaviTool 固件使用该接口或 XcertPlay 当前实现已暴露导航事件。

参考：

- [XcertPlay 上游仓库](https://github.com/shilapi/xcertplay)
- [NaviTool HUD 高德广播协议规格说明](navitool-hud-broadcast-spec.md)
- [高德车机标准广播协议资料副本](https://www.xiaodecheji.com/article/189)
- [Navi-Link 高德广播接收器示例](https://github.com/xiaoming-5628/Nav-Link)
- [AMap- 广播字段及解析参考](https://github.com/zd423/AMap-)

### F. CarLink 并存与启动

- 不在启动时默认 `force-stop`、禁用或修改 `com.geely.carlink`。
- 先验证能否由用户手动启动 XcertPlay 并保持原车蓝牙电话功能。
- 若确证 CarLink 抢占无线 CarPlay，再评估可逆的启动/前台策略；涉及系统签名权限、SELinux、系统分区的工作必须另行评估并保留恢复方案。

## 联调验收顺序

| 阶段 | 验收条件 | 当前状态 |
|---|---|---|
| 0. 开发基线 | 上游源码和车辆 profile/设置入口 | 已克隆 XcertPlay 1.2.4；已加入手动 preset，未做车端构建/安装验证 |
| 1. 安装启动 | Android 9/API 28 上安装、启动、回前台、重启后可恢复 | 待上车执行 |
| 2. CH341 | USB Host 授权稳定，枚举 `1A86:5512` | 等首块焊接板 |
| 3. MFi | 读取认证信息并通过 challenge/sign | 等板卡台架检查和上车 |
| 4. CarPlay 会话 | iPhone 建立无线 CarPlay，画面在中控正常显示 | 待 MFi 通过 |
| 5. 音频 | MEDIA/NAVIGATION/PHONE/ASSISTANT 分别走预期通道 | 待真实会话后测 Audio HAL |
| 6. HUD 导航 | 真实导航事件正确转换并显示，结束时清除 | 待事件解码和车上验证 |
| 7. 稳定性 | 冷/热启动、重连、来电、导航开始结束、长时运行 | 待前序验收通过 |

## 前期硬件风险提醒

焊接任务记录发现设计源/网表有 R5 位号，而 BOM/坐标表只列 R1–R4 的不一致；历史网表分析提示 R4/R5 可能在 CS0 上形成冲突。第一次上电前应以实际打板版本的原理图、Gerber、PCB 丝印和焊盘核对，不能照此文档盲目指定 R5 焊接状态。首板先测试，再焊其余板。

## 当前实现边界

当前改动提供了车辆 preset 和项目适配基线。它没有绕过 MFi 认证，没有更改 CarPlay 导航事件解析器，也没有硬编码 HUD、音频 DSP 或 CarLink 系统服务行为；这些都需要在取得真实 iAP2 导航帧、车机 Audio HAL 观测和首块 MFi 板之后逐项开发与验证。
