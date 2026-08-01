# 公网单端口面板入口

拓扑：管理员浏览器 → 公网机 `:443`（Caddy）→ 本机 `127.0.0.1:8088`（SSH 反向隧道）← 面板机出站反连。  
Minecraft 仍跑在面板机；游戏流量不经该 Web 口。

## 1. 面板机（MC + Spring）

`config/seminecraft-common.toml`：

```toml
panelEnabled = true
panelBindAddress = "127.0.0.1"
panelPort = 8088
panelUsername = "admin"
panelPassword = "换成强密码"
```

模组会向 Spring 传入 `--server.address=127.0.0.1`，面板不对公网/局域网直接监听。

建立反向隧道（二选一）：

- Linux：复制 [`panel-tunnel.service`](panel-tunnel.service)，改 `PUBLIC_VPS_IP` / 用户，然后 `systemctl enable --now seminecraft-panel-tunnel`
- Windows：改 [`panel-tunnel.ps1`](panel-tunnel.ps1) 里的 IP/用户后运行；可用任务计划程序开机启动

隧道命令等价于：

```text
ssh -N -R 127.0.0.1:8088:127.0.0.1:8088 seminecraft-tunnel@公网IP
```

## 2. 公网机（唯一放行 443）

1. 安装 [Caddy](https://caddyserver.com/)，使用 [`Caddyfile`](Caddyfile)，把 `panel.example.com` 改成你的域名并解析到该机。
2. 按 [`sshd_tunnel_notes.conf`](sshd_tunnel_notes.conf) 确认 `GatewayPorts no`，隧道只落到本机 `8088`。
3. 防火墙入站只放行 **443**（以及你运维 SSH 若另开；Web 方案本身只依赖 443）。

Caddy 会把 `https://你的域名/` 反代到 `127.0.0.1:8088`。

## 3. 验收

1. **本机**：在面板机执行  
   `curl -s -o NUL -w "%{http_code}" http://127.0.0.1:8088/login`  
   期望 `200` 或 `302`。
2. **公网**：浏览器打开 `https://你的域名/login`，能进登录页；从外网扫描面板机 `8088` 应不通。
3. **动作**：公网登录后执行 OP / 控制台命令，面板机游戏内应在数秒内生效（`panel_actions` 仍由模组消费）。

## 注意

- 默认口令务必改掉；公网只暴露 HTTPS，不要把面板机 `8088` 映射出去。
- 本方案不把 Minecraft 挤进 443；玩家仍直连面板机游戏端口。
- 公网机不跑 MC、不共享 SQLite；数据库始终在面板机。
