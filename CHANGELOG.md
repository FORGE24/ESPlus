# Changelog / 更新日志

> 中文 · English · 日本語 · 한국어 · Deutsch · Русский

---

## [1.0.0] — 2026-08-02

### 🎉 Initial Release / 首次发布

ES+ 首个正式版本发布。NeoForge 1.21.1 安全套件：从"信任 OP"升级为"验证每一次操作、记录每一条轨迹"。

---

### 🔐 Sudo 密码鉴权 / Sudo Password Auth

- BCrypt (cost=12) → AES-256-GCM → RSA-2048 key wrap → SQLite 四层密码存储
- 限时 sudo 会话（默认 5 分钟，可配 1–120 分钟）
- 连续失败锁定机制（默认 5 次失败锁定 15 分钟）
- 最小密码长度限制（默认 6 位）

### 🚫 指令门禁 / Command Gate

- 拦截 24+ 条原版敏感指令：`give` `ban` `op` `stop` `gamemode` `kick` `tp` `kill` `deop` `whitelist` `pardon` `ban-ip` `pardon-ip` `time` `weather` `difficulty` `gamerule` `defaultgamemode` `experience` `enchant` `effect` `clear` `fill` `setblock` `summon` `execute`
- 三级风险分级：`NONE` / `HIGH` / `CRITICAL`
- 无活跃 sudo 会话时直接拦截，返回可读提示
- 精细权限节点：`cmd.give` `cmd.ban` `cmd.op` 等可单独授权
- admin→admin 可选自动提权（免密码）

### 📋 全局行为审计 / Full Behavior Audit

记录以下全部事件类型（时间戳 + 玩家 + 维度 + 坐标）：

- 玩家生命周期：登录、登出、死亡、切维度
- 方块交互：破坏、放置
- 物品流动：拾取、丢弃、合成、sudo 发放
- 社交：聊天（含敏感词检测 + 禁言状态标记）
- 指令：全部指令（控制台指令也记录），密码参数自动脱敏
- 安全事件：sudo 鉴权成功/失败、密码修改/重置
- 移动：按距离/tick 间隔采样轨迹

### 🔗 物品溯源 / Item Traceability

- `/sudo give` 发放物品时自动写入唯一 `trace_id` 到 NBT 的 `esplus_trace` 字段
- 物品被拾取、丢弃、合成时链路自动延伸
- 面板支持按 trace_id 查询完整来源链

### 🚨 异常告警 / Anomaly Alerts

滑动窗口规则引擎，默认规则：

| 规则 | 阈值 | 说明 |
|------|------|------|
| `CMD_BURST` | 40 次/60s | 指令爆发 |
| `GIVE_BURST` | 8 次/60s | 物品发放异常 |
| `BREAK_BURST` | 80 次/60s | 破坏速度异常 |
| `SUDO_FAIL` | 每次 | sudo 鉴权失败 |
| `PROTECTED_CMD_BLOCKED` | 每次 | 受保护指令被拦截 |
| `LOGIN_ANOMALY` | — | 登录异常检测 |

- 告警分级：INFO / WARN / CRITICAL
- 面板告警列表 + 确认/忽略操作
- Webhook 推送（Discord 兼容格式）

### 🔍 事发链还原 / Incident Chain

- 以任意告警/审计事件为中心
- 自动聚合 ±N 分钟窗口内相关事件 + 移动轨迹 + 物品链路
- 面板一键查看完整事发时间线

### 🖥️ Web 管理面板 / Admin Panel

- 独立 JVM 进程运行 Spring Boot 3.3.5 + Thymeleaf + Spring Security
- 通过 SQLite WAL 与 Minecraft 服务端通信
- Basic Auth 登录，默认 `admin` / 启动时强制改密
- 三级角色：`admin` / `moderator` / `viewer`
- 50+ 管理页面：

| 分类 | 页面 |
|------|------|
| 总览 | Dashboard（在线玩家、TPS、告警数、性能概览） |
| 玩家 | 玩家列表、玩家详情、离线操作（ban/kick/op/whitelist/gamemode） |
| 审计 | 审计日志搜索、告警管理、事发链还原、物品溯源 |
| 控制台 | 服务端命令执行、实时日志查看 |
| 聊天 | 广播消息、定时广播、禁言管理、敏感词过滤、title/bossbar |
| 世界 | GameRule、时间、天气、难度、世界边界、出生点、维度列表 |
| 实体 | 实体列表、实体清理 |
| 物品 | 物品发放、背包查看、物品清理 |
| 安全 | sudo 管理、MFA 设置、权限配置、锁定模式、完整性检查、快照、Webhook、风险评分 |
| 系统 | 性能趋势、连接状态、版本检查、存档/维护/重载/停止、计划任务、数据保留 |

### 💬 聊天管理 / Chat Moderation

- 敏感词过滤（正则匹配，可配置词表）
- 玩家禁言（临时/永久）
- 广播消息（全服公告）
- 定时广播（可配置间隔与内容）
- Title / Bossbar 推送

### 🔑 权限系统 / Permission System

- 三类 SEM 角色：`op` / `moderator` / `admin`
- 细化权限节点：`sudo.session` `cmd.*` `audit.view` `panel.access` 等
- `/setoppw` 仅 Minecraft OP 可用
- 默认 SEM 角色 `op` 仅含 `sudo.session`，无 `cmd.*`
- 需在面板将用户升为 `moderator` / `admin` 后方可执行受保护指令

### 🖥️ Qt 密码弹窗 / Native Password Prompt

- Qt6/C++ 原生 Windows 密码输入对话框
- 通过 NeoForge Payload API 与服务端通信
- 密码不经过聊天框，杜绝 log 泄露
- 自动通过 NeoForge 网络包分发给客户端

### 🛡️ 安全存储 / Secure Storage

- SQLite WAL 模式，数据目录 `esplus/security.db`
- RSA-2048 密钥对自动生成，存储在 `config/esplus/keys/`
- AES-256-GCM 主密钥经 RSA 包装后存入数据库
- 面板密码仅走进程环境变量，不写入磁盘/配置文件

### 📡 网络通讯 / Networking

- NeoForge Payload API（`playToClient` / `playToServer`）
- 密码弹窗请求/响应协议
- 客户端-服务端双向数据包

### ⚙️ 配置 / Configuration

- 首次启动自动生成 `config/esplus-common.toml`（30+ 配置项）
- 支持项：sudo 会话时长、锁定阈值、密码长度、审计开关、面板端口/地址/凭据、数据保留天数、受保护指令列表、风险阈值等

### 🌐 多语言 / i18n

- 模组内语言文件：简体中文 (`zh_cn.json`) + English (`en_us.json`)
- Web 面板：全部页面中英双语
- 文档：README / MODRINTH / OVERVIEW / CHANGELOG 六语覆盖（中英日韩德俄）

### 📦 构建与发布 / Build & Distribution

- 单 JAR 分发，面板 fat-jar 和 sqlite-jdbc 内嵌
- Gradle 8.x + NeoGradle 7.1.38
- JDK 21 (Zulu)
- `build.bat` 一键构建
- GitHub Releases 主渠道，Modrinth 镜像

### 📄 许可 / License

LGPL-3.0-or-later

### ⚠️ 已知限制 / Known Limitations

- Qt 密码弹窗仅支持 Windows (Win64)；Linux/macOS 客户端回退到聊天框密码输入
- 面板默认仅监听 `127.0.0.1`，远程访问需 SSH 隧道或 HTTPS 反代
- Spring Boot 面板在独立 JVM 启动，首次冷启动需约 3–5 秒

---

## 版本号规则 / Versioning

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/)。

- `MAJOR`：破坏性 API/配置变更
- `MINOR`：向后兼容的功能新增
- `PATCH`：向后兼容的问题修复
