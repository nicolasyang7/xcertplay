# 群晖 Remote MFi 快速部署

此目录是上游 `remote-mfi-for-xcertplay` 的 Synology Container Manager 部署模板。它通过群晖 USB 接入 CH341-I2C 转接板和实体 MFi 芯片。上游标明 Synology Container Manager 与真实 CarPlay MFi 签名尚未完成实机验证，请按下方清单验收。

## 部署前

1. DSM 安装 Container Manager，并确认 NAS CPU 是 `amd64` 或 `arm64`。
2. CH341 与 MFi 芯片已经备好。接入 NAS 后，先在 DSM 能用的终端/SSH 检查是否出现 `/dev/bus/usb/BBB/DDD`，并记录实际 VID:PID；模板默认 `1a86:5512`，若不一致就修改 `.env` 的 `MFI_CH341_USB_IDS`。
3. 该服务使用 libusb，不是 NAS 的 `/dev/i2c-N`。需要把整个 `/dev/bus/usb` 映射进容器，并给字符设备主设备号 189 放行。Compose 不能弥补 DSM 宿主机未识别的设备或缺失的 USB 驱动。
4. 一颗 CH341 同时只能由一个进程占用；停止其他使用该转接板的软件。

## Container Manager 项目

1. 把本 `synology` 目录复制到 NAS 持久化共享目录，例如 `docker/remote-mfi`。
2. 复制 `.env.example` 为 `.env`。在 NAS 上生成 token（例如 `openssl rand -hex 32`），填入 `MFI_BEARER_TOKEN`。
3. CH341 接入 NAS 后确认宿主机出现 `/dev/bus/usb/BBB/DDD` 节点并记录 VID:PID；如果不是 `1a86:5512`，修改 `MFI_CH341_USB_IDS`。I2C 地址默认 `0x11`，速度默认 100 kHz。
4. 确认 `MFI_IMAGE_TAG` 是准备运行的 GHCR 发布标签；在 DSM 打开 **Container Manager → Project → Create**，项目路径选该目录，来源选 `docker-compose.yml`，启动项目。
5. 检查项目日志，容器应持续运行且 Health 状态为 healthy。

Compose 以 root 身份运行容器（上游镜像如此配置），映射 `/dev/bus/usb` 和 `c 189:* rmw`。设备范围较宽，不应再添加 `privileged`。Bearer token 会作为命令行参数出现在容器配置中，因此应限制 DSM 管理权限。

## 验证

从局域网客户端访问 `http://NAS地址:8972/healthz`，检查响应 JSON 的 `ok` 为 `true`。接口始终以 HTTP 200 返回，必须检查 `ok` 字段。然后浏览器访问受保护的诊断页（建议使用 Authorization header 的 API 工具访问 `/debug/usb`，避免 token 留在 URL/浏览历史中），确认 CH341 显示 `ready`。

用 Bearer token 请求真实证书：

```sh
curl -fsS -H "Authorization: Bearer YOUR_TOKEN" \
  http://NAS地址:8972/mfi/certificate
```

随后在 Xcertplay 设置 Remote MFi 地址 `http://NAS地址:8972` 并填写同一 token，完成 iPhone 实际 CarPlay 认证，确认日志收到 `AA05 AuthenticationSucceeded`。`ready` 只证明 USB 会话打开成功，不证明 MFi 签名有效。

跨网访问前，在 DSM 反向代理上配置 HTTPS 并限制来源。服务本身只提供 HTTP。不要把真实 token 放到截图、URL、聊天记录或提交到 Git。

## 现场清单

- [ ] 记录 NAS 型号、DSM、Container Manager 版本、CPU 架构。
- [ ] 记录 CH341 VID:PID、USB Bus/Device；拔插后确认设备节点重新出现。
- [ ] Compose 项目能构建/拉取、启动；容器日志没有 USB 权限/设备缺失错误。
- [ ] `/healthz` 返回 `ok: true`，`/debug/usb` 显示匹配候选及 `ready`。
- [ ] `/mfi/certificate` 返回证书；校验 SHA-256 与解码后证书相符。
- [ ] Xcertplay 完成真实签名和 AA05。失败时保存时间、服务日志和诊断 JSON，分享前清除 token。
- [ ] 拔插和更换设备后重复验证；换设备需重启容器以刷新证书缓存。
- [ ] 记录实际使用的镜像 tag/digest 与 `.env` 参数，便于回滚。

### 快速定位

* 宿主机没有 `/dev/bus/usb` 节点：检查 USB 口、线材、DSM 是否枚举设备及 CH341 是否处于正确 USB 模式；容器配置无法修复宿主机未枚举。
* 宿主机有节点但 `/debug/usb` 显示 missing：核对 `MFI_CH341_USB_IDS` 和容器卷映射。
* 显示 permission denied：核对容器身份、USB 节点权限与 `c 189:* rmw` 规则是否被 Container Manager 接受。
* 状态 ready 但证书读取失败：检查 CH341 与芯片连接、供电、I2C 地址和速度；ready 不代表芯片认证通过。
* 证书能读但 CarPlay 签名失败：保存服务日志、Xcertplay 日志和诊断 JSON，最终以 iPhone 返回 AA05 为验收依据。

服务端 `/mfi/reset` 是 no-op，不会重置芯片。证书按进程缓存，签名 requestId 缓存 60 秒。服务只支持 `type=mfi`，不提供 BAA 证书服务。
