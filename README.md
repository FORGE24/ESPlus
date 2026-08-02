# ES+

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange?style=flat-square)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square)](https://adoptium.net/)

NeoForge 1.21.1 安全套件：OP 密码鉴权、指令门禁、全行为审计、物品溯源、异常告警、Web 管理面板。服务端与客户端均需安装。项目采用 **LGPL-3.0-or-later** 许可，适合二次开发和服务端集成。

> **中文** · [English](#english) · [分发策略（推荐）](#分发策略推荐)


### 版本号

遵循 SemVer（`gradle.properties` → `mod_version`）。破坏性配置/权限模型变更抬 minor 或 major，并在 Release 写迁移说明。

---

## 功能概要

- **sudo**：`/setoppw`、`/sudo`、受保护原版指令门禁；细粒度角色 `op` / `moderator` / `admin`
- **审计**：登录聊天指令方块物品移动等；物品 NBT 溯源；异常告警；事发链
- **面板**：独立 JVM Spring Boot，默认 `http://127.0.0.1:8088/`（光猫式高密度运维页）
- **通讯**：广播/定时广播、禁言、敏感词、title/bossbar 等

权限要点：

- `/setoppw` **仅 Minecraft OP** 可用；默认 SEM 角色 `op` **只有** `sudo.session`，没有 `cmd.*`
- 要执行受保护指令：面板里把该用户升为 `moderator` / `admin`（或勾选具体权限）后再 `/sudo`

---

## 构建

需要 **JDK 21**。

```bat
.\gradlew.bat build
```

产物：`build/libs/esplus-<version>.jar`

仅重打面板（开发时）：

```bat
.\gradlew.bat -p panel bootJar
.\gradlew.bat build
```

Windows Qt 密码窗（可选，放入 `native/pwprompt/dist/` 后重新 `build` 才会打进 jar）：

```bat
native\build-pwprompt.bat
```

---

## 安装（服务器）

1. NeoForge **21.1.235+** / Minecraft **1.21.1**
2. 将 `esplus-*.jar` 放入 `mods/`
3. 启动一次，编辑 `config/esplus-common.toml`：

```toml
panelEnabled = true
panelBindAddress = "127.0.0.1"
panelPort = 8088
panelUsername = "admin"
panelPassword = "换成强密码"          # 不能再是默认 esplus
# panelAllowDefaultPassword = false   # 保持 false；仅本地调试可 true
```

4. 重启服务端
5. 给自己 OP → `/setoppw` → 面板「管理员」里升角色（如需执行受保护指令）
6. 本机打开 `http://127.0.0.1:8088/`

**服务端 + 客户端均需安装**：sudo 鉴权依赖客户端网络包与 Qt 密码弹窗。

### 远程访问面板（推荐）

不要把 `8088` 映射到公网。使用：

- SSH 本地转发：`ssh -L 8088:127.0.0.1:8088 user@游戏机`
- 或公网机 Caddy `:443` + SSH 反向隧道：见 [`deploy/public-ingress/README.md`](deploy/public-ingress/README.md)

---

## 常用指令

| 指令 | 说明 |
|------|------|
| `/setoppw` | 设置 sudo 密码（需 OP） |
| `/changepw` | 修改密码 |
| `/sudo` | 开启 sudo 会话 |
| `/sudo status` / `/sudo exit` | 状态 / 退出 |
| `/sudo give <玩家> <物品> [数量]` | 带溯源的受保护发放 |
| `/esplus resetpassword <玩家>` | 控制台或 4 级权限重置 |

---

## 配置摘要

| 项 | 含义 |
|----|------|
| `sudoSessionMinutes` | sudo 会话时长 |
| `protectedCommands` | 需 sudo + SEM 权限的根指令 |
| `panelBindAddress` | 建议保持 `127.0.0.1` |
| `panelPassword` | 经环境变量注入面板 JVM，**不写进磁盘 properties** |
| `panelAllowDefaultPassword` | 默认 `false`：默认口令时拒绝启动面板 |
| `auditRetentionDays` | 审计自动清理天数 |

数据目录（相对服务器根）：

- `esplus/security.db` — SQLite
- `config/esplus/keys/` — RSA 包装密钥
- `esplus/panel/` — 解压出的面板 jar 与非密钥 runtime 配置
- `logs/spring-panel.log` — 面板日志

---

## 安全提示

1. 立刻改掉面板密码；默认 `esplus` 在未允许时**不会启动面板**
2. 面板凭据只走进程环境，不进 `application-runtime.properties`、不进 JVM argv
3. 普通 OP ≠ 可执行全部受保护指令；需面板赋权
4. 不要对公网直接 `panelBindAddress = "0.0.0.0"`

---

## English {#english}

**ES+** is a NeoForge 1.21.1 security suite (sudo gate, audit, item trace, alerts, chat filter, embedded web panel). **Requires installation on both server and client.**

### Recommended distribution

Ship **one fat mod jar** via **GitHub Releases**. The Spring panel fat-jar is **embedded** (`META-INF/esplus/esplus-panel.jar`) — do **not** distribute the panel separately. Optional Modrinth/CurseForge mirrors should use the same artifact and the same security notes.

Build: `./gradlew build` → `build/libs/esplus-<version>.jar`.

Install: drop into server `mods/` AND each player's client `mods/`, set a strong `panelPassword` in `config/esplus-common.toml` (default password refuses to start the panel), keep `panelBindAddress = "127.0.0.1"`, expose remotely only via SSH tunnel / HTTPS reverse proxy (`deploy/public-ingress/`).

Clients must also install the mod (required for the Qt password prompt and network payload exchange). After `/setoppw`, promote the user to `moderator`/`admin` in the panel before protected commands work under `/sudo`.

---

## License

This project is licensed under **LGPL-3.0-or-later**.

- SPDX identifier: `LGPL-3.0-or-later`
- Source code and derivative works may be used, modified, and distributed under the LGPL terms.
- This project remains suitable for server-side integration and custom forks.
