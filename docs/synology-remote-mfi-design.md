# 群晖 Remote MFi 部署与验收

## 仓库分析

参考服务端源码已放在工作区 `remote-mfi-for-xcertplay-main/`。它是 Go 1.23 服务，用 libusb 通过 CH341 USB-I2C 桥连接一颗实体 MFi 认证协处理器，向 Xcertplay 提供 Remote MFi HTTP API。许可证是 GPL-3.0-only；代码声明 CH341 和 MFi 协议实现参考 Xcertplay GPL 代码。

上游目前标记为实验版本：真实 MFi 签名、CarPlay `AA05 AuthenticationSucceeded`、Asuswrt-Merlin 和 Synology Container Manager 都尚未硬件验证。因此 Compose 能成功启动不代表群晖 USB 透传或 CarPlay 认证已通过。

### 服务实现要点

* `GET /mfi/certificate` 读取 MFi 芯片的协议主版本和证书，返回 `type=mfi`、Base64 证书以及对解码后证书计算的 SHA-256 小写十六进制摘要。成功结果按进程缓存；换芯片要重启服务。
* `POST /mfi/sign` 接收 Base64 challenge（解码后 1..128 字节）和 UUID `requestId`。成功结果按 ID 缓存 60 秒；同 ID 不同 challenge 返回 400。所有芯片事务串行，等待芯片锁最多 8 秒。
* `POST /mfi/reset` 接受 `{}` 并返回 `{"detail":""}`，实际是兼容 no-op，不操作硬件，也不清除缓存。
* `GET /debug/usb` 提供 USB/近期请求诊断，支持 Bearer token 或 query token；`GET /healthz` 无鉴权，始终返回 HTTP 200，需查看 body 的 `ok` 字段。
* 业务 JSON body 上限 64 KiB；业务响应使用扁平 JSON。设置 Bearer token 后 `/mfi/*` 和 `/debug/usb` 鉴权，`/healthz` 仍公开。
* 目标芯片是 MFi 类型。服务端不会提供 BAA 类型或 BAA 证书包。

主要实现位于 `cmd/remote-mfi-for-xcertplay/main.go`、`internal/config/config.go`、`internal/biz/service.go`、`internal/chip/driver.go`、`internal/transport/ch341.go` 和 `internal/httpapi/handlers.go`。单元测试使用替身 transport/driver；不能替代实体 MFi 认证验收。

## 群晖部署

上游已经提供 `docker-compose.yml`、`.env.example` 和 GHCR 镜像。当前模板关键配置如下：

```yaml
ports:
  - "${MFI_HTTP_PORT:-8972}:${MFI_HTTP_PORT:-8972}"
command:
  - "--http-addr=:${MFI_HTTP_PORT:-8972}"
  - "--bearer-token=${MFI_BEARER_TOKEN:?set MFI_BEARER_TOKEN in .env}"
volumes:
  - /dev/bus/usb:/dev/bus/usb
device_cgroup_rules:
  - "c 189:* rmw"
```

操作顺序：

1. 确认 NAS CPU 架构为 `amd64` 或 `arm64`，并安装 DSM 对应版本的 Container Manager。
2. 将 `remote-mfi-for-xcertplay-main` 作为项目目录放到 NAS 共享文件夹；复制 `.env.example` 为 `.env`，生成并填写长随机 `MFI_BEARER_TOKEN`。不要把真实 token 提交到版本库或截图中。
3. 确认 CH341 直连 NAS USB 后，DSM 宿主机能创建对应 `/dev/bus/usb/BBB/DDD` 节点，设备处于 CH341 I2C 模式；默认 VID:PID 是 `1a86:5512`，I2C 地址默认 `0x11`，速度默认 100 kHz。不同 VID:PID 可设置 `MFI_CH341_USB_IDS`。
4. 在 Container Manager 的 Project 中选择该目录和 `docker-compose.yml`，Build/启动项目。仓库镜像以 root 身份运行，USB 权限简化为挂载整个 `/dev/bus/usb` 并放行字符设备主设备号 189。此映射对设备范围较宽；不要额外配置 `privileged`。
5. 通过 `http://NAS局域网地址:8972/healthz` 检查 JSON。远程或跨网访问时通过 DSM 反向代理配置 HTTPS，并限制来源；Xcertplay Remote MFi URL 使用服务地址，token 填与 `.env` 相同的值。
6. 用带 Bearer 认证的 `/debug/usb` 检查设备候选及状态，再调用证书端点检查实际证书。最后让 Xcertplay/iPhone 完成真实签名和 `AA05` 验收。

Compose 模板将 `.env` 中的值插入程序命令行 flags；程序本身不读取应用配置环境变量。注意 token 会出现在容器的 command 配置中，NAS 管理权限也应受控。服务默认 HTTP，不提供 TLS。

## 验收与限制

需记录 NAS 型号、DSM/Container Manager 版本、CPU 架构、CH341 VID:PID、USB Bus/Device、镜像 tag/digest 和日志。按 `remote-mfi-for-xcertplay-main/docs/03-acceptance-checklist.md` 完成：

* 容器中能看到 USB 节点，`/healthz` 的 `ok` 为 true，`/debug/usb` 显示匹配设备 ready。
* 证书 Base64 解码成功，长度在客户端允许范围内，SHA-256 与返回字段相符。
* 对真实 challenge 的签名成功，并由 Xcertplay/iPhone 完成认证；`ready` 只说明 USB 会话可打开，不验证签名功能。
* 测试设备拔插/更换。证书在进程级缓存；更换设备必须重启容器。上游指出热拔插恢复还需要实测。

仓库自带自动化测试为 fake hardware，不构成群晖验收。本地这份 Android 项目也没有目标 NAS，无法从这里证明 DSM 内核驱动、USB 设备节点和 Container Manager cgroup 映射实际有效。若 NAS 宿主机不能枚举 CH341，则该架构不能仅靠 Compose 修复；应把服务运行在能访问 CH341 的 Linux 主机，再让群晖作 HTTPS 代理。
