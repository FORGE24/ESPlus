# ES+

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange?style=flat-square)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square)](https://adoptium.net/)

NeoForge **1.21.1** 服务端安全套件：sudo 门禁、行为审计、物品溯源、异常告警、聊天过滤，以及嵌入式 Web 管理面板。项目采用 **LGPL-3.0-or-later** 许可，适合二次开发和服务端集成。

> **中文** · [English](#english) · [分发策略（推荐）](#分发策略推荐)

---

## 分发策略（推荐）

### 结论：只发「一个模组 JAR」

| 制品 | 给谁 | 怎么发 |
|------|------|--------|
| `esplus-<version>.jar` | 服主 / 运维 | **GitHub Releases**（主渠道） |
| 面板 Spring fat-jar | — | **不要单独分发**（已嵌入模组） |
| 源码 | 开发者 | Git 仓库 |
| Maven 本地/私服 | CI / 二次开发 | 可选，非玩家渠道 |

**推荐发布物形态：单文件、服务端-only、自包含。**

构建会把这些打进同一个模组 JAR：

1. 模组代码 + 嵌入的 `sqlite-jdbc`
2. `META-INF/esplus/esplus-panel.jar`（独立 JVM 跑面板）
3. 可选：`esplus-pwprompt-win64.zip`（Windows Qt 密码窗）

服主只需：

```text
把 esplus-x.y.z.jar 丢进服务器 mods/ → 启动
```

### 为什么选这套（相对其它方案）

| 方案 | 评价 |
|------|------|
| **单 JAR 嵌入面板（推荐）** | 安装零依赖、版本绑定清晰、不会出现「模组与面板版本对不上」 |
| 模组 + 单独 panel.jar 双包 | 易漏装、易混版本；仅适合你自己拆开调试 |
| 面板做成独立 systemd 服务 | 运维重、与游戏生命周期脱节；不符合「丢进 mods」定位 |
| CurseForge / Modrinth 为主渠道 | 可以挂镜像，但安全类模组务必在 Release 说明里强调改密与绑定；默认口令策略见下 |
| 把面板绑 `0.0.0.0` 直出公网 | **不推荐**；应用 SSH 反向隧道 / HTTPS 反代（见 `deploy/public-ingress/`） |

### 发布检查清单

1. `.\gradlew.bat build`（会自动 `-p panel bootJar` 并嵌入）
2. 取 `build/libs/esplus-<version>.jar`（不要发 `*-sources` / plain panel）
3. GitHub Release：附 jar + 简短变更说明 + 最低 NeoForge 版本
4. Release 正文强调：
   - **必须改** `panelPassword`（默认值会拒绝启动面板）
   - 面板默认只听 `127.0.0.1:8088`
   - 客户端无需安装
5. 可选：同步上传到 Modrinth/CurseForge，指向同一 jar 与同一说明

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

**纯服务端模组**：玩家客户端不必装。

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

**ES+** is a **server-side** NeoForge 1.21.1 security suite (sudo gate, audit, item trace, alerts, chat filter, embedded web panel).

### Recommended distribution

Ship **one fat mod jar** via **GitHub Releases**. The Spring panel fat-jar is **embedded** (`META-INF/esplus/esplus-panel.jar`) — do **not** distribute the panel separately. Optional Modrinth/CurseForge mirrors should use the same artifact and the same security notes.

Build: `./gradlew build` → `build/libs/esplus-<version>.jar`.

Install: drop into server `mods/`, set a strong `panelPassword` in `config/esplus-common.toml` (default password refuses to start the panel), keep `panelBindAddress = "127.0.0.1"`, expose remotely only via SSH tunnel / HTTPS reverse proxy (`deploy/public-ingress/`).

Clients do not need the mod. After `/setoppw`, promote the user to `moderator`/`admin` in the panel before protected commands work under `/sudo`.

---

## License

This project is licensed under **LGPL-3.0-or-later**.

- SPDX identifier: `LGPL-3.0-or-later`
- Source code and derivative works may be used, modified, and distributed under the LGPL terms.
- This project remains suitable for server-side integration and custom forks.
