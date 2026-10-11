# 待统一修改的项目 (Pending changes)

记录日期: 2026-10-10。以下是评估后决定"之后统一修改"的内容,尚未实施。

## 1. 高通解码器低延迟参数 (来自 DiPlay PR #496)

- 原因: 车机 `ro.board.platform = msmnile` (骁龙 855)。高通解码器 `c2.qti.avc.decoder`
  默认多缓存约两帧才输出,造成画面延迟。Android 9 上通用的 `KEY_LOW_LATENCY` 不生效
  (现有代码要求 Android 11+)。
- 修改位置: `shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt`
  约第 324 行,`next.configure(format, surface, null, 0)` 之前。
- 做法: 当解码器名以 `c2.qti.` 或 `OMX.qcom.` 开头时,对 format 设置:
  - `vendor.qti-ext-dec-low-latency.enable` = 1
  - `vendor.qti-ext-dec-picture-order.enable` = 1
- 不要一起搬: 独立输出线程、FPS 计数器 (DiPlay 的解码结构与 xcertplay 1.3.3 的
  `VideoDecodeFlow` 不同,改动大)。
- 参考: DiPlay 提交 `45179f06`,函数 `vendorLowLatencyKeys(codecName)`。

## 1b. 领克 09 预设默认改为手动热点 (来自 shilapi/xcertplay issue #36)

- 现象: Android 9 上 LocalOnlyHotspot 模式下手机拿不到有效 wlan0 IP,连接失败;
  改手动模式并填车机自带热点的 SSID/密码后可连接 (issue 提交者 ricefat 与你本人均已验证)。
- 修改位置: `common/src/main/java/com/shilapi/xcertplay/LynkCo09Profile.kt` 的
  `hotspotModeFor()`,API < 29 时返回 `WirelessHotspotMode.MANUAL` 而不是 `LOCAL_ONLY_HOTSPOT`。
  (DiPlay 提交 `386234e4` 也推荐使用车机内置热点。)
- 注意: 手动模式需要用户在设置里填写车机热点 SSID/密码,预设本身不能预填。

## 1c. 去掉 CarPlay 电话能力 (与车机蓝牙电话重复)

- 原因: 车机已有自己的蓝牙电话 (HFP),CarPlay 的来电界面/通话音频/麦克风会与之重复。
- 修改位置:
  - `shared/.../iap2/message/Iap2Messages.kt` `subscriptions()`: 去掉 `START_COMMUNICATIONS_UPDATES`
    和 `START_CALL_STATE_UPDATES` (约 298-303 行)。
  - `shared/.../transport/Iap2IdentificationClient.kt`: 同步从
    `MESSAGES_SENT_BY_ACCESSORY` / `MESSAGES_RECEIVED_FROM_PHONE` 去掉
    0x4157/0x4159/0x4154/0x4156 和 0x4158/0x4155 (文件注释要求与 Wired/Wireless 客户端成对修改)。
  - `shared/.../airplay/AirPlayInfoPlist.kt`: 去掉 `format(100, "telephony", ...)` (约 145 行)
    和 `AirPlayHid.telephonyHidDevice` (约 61 行)。
  - 麦克风: 若不需要 Siri/语音,可同时关闭麦克风上行。
- 未验证 (需上车): 去掉后 iPhone 是否把通话路由回蓝牙 HFP;Siri 是否仍可用;会话是否仍能正常建立。
- 做成设置开关 (默认关闭电话) 比直接删除更稳妥,便于回退。

## 2. 评估后暂不修改

- issue #36 的"CarPlay 音乐播放约 1 秒后暂停"(车机蓝牙音乐抢占): 用户不使用媒体功能,忽略。

- Wi-Fi 搜索暂停 (DiPlay #570): 只针对 Wi-Fi Direct;领克 09 用 LocalOnlyHotspot,不适用。
- DiPlay PR #456 / #347 (解码队列相关): 与 xcertplay 新管线不兼容,若移植高通参数后仍卡再评估。
- USB 帧、麦克风回退、侧边栏、氛围灯、设置页改版、多语言: 与当前问题无关。

## 3. 上车后需要验证 / 待确认

- iAP2 导航 (见 `iap2-route-guidance-fields.md`): 状态值、剩余时间单位 (毫秒)、
  转向类型 0..13 的含义均为未验证假设,需看日志里的 `rawType` 与状态值。
- 车道线 (0x5204): 子字段未知,暂未解码,HUD 暂无车道线。
- NaviTool 包名与广播注册方式 (静态/动态) 未确认;Android 9 上静态注册的接收器收不到隐式广播。
- 高德 ICON 编号表为凭记忆编写,需对照 NaviTool 实际代码。
- 日志路径: `/storage/emulated/0/Android/data/com.shilapi.xcertplay/files/logs/xcertplay.log`,
  回退 `/data/user/0/com.shilapi.xcertplay/files/logs/`。

## 4. 其他待办

- 提交 `70b5066` (iAP2 导航解码重写) 尚未推送,云端未编译验证。
- 用原签名密钥打包: 在仓库 Secrets 配置 `RELEASE_KEYSTORE_BASE64`、
  `RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`,
  运行 `android.yml`。需先确认原密钥库还在。
