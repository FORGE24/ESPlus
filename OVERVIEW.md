# ES+ — 项目概述

## 一句话

NeoForge 1.21.1 服务端安全套件：OP 密码鉴权、指令门禁、全行为审计、物品溯源、异常告警、Web 管理面板。

## 解决什么问题

Minecraft 原版的安全模型极其粗糙——**有 OP 就有上帝权限**。一个拿到 OP 的管理员可以 `/give` 无限物资、`/ban` 所有玩家、`/stop` 关服，事后无任何记录可查。

ES+ 在 NeoForge 层插入一套完整的安全中间件，让服务器从"信任 OP"变成"**验证每一次敏感操作，记录每一条行为轨迹**"。

---

## 架构

```
┌──────────────────────────────────────────────────────┐
│                    Minecraft Server                    │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐  │
│  │ Command  │  │  Event   │  │     Tick Loop      │  │
│  │  Gate    │  │  Hooks   │  │  (Snapshot/Track)  │  │
│  └────┬─────┘  └────┬─────┘  └─────────┬──────────┘  │
│       │             │                  │              │
│       ▼             ▼                  ▼              │
│  ┌────────────────────────────────────────────────┐   │
│  │              SecurityService                   │   │
│  │  ┌──────────┐ ┌──────────┐ ┌───────────────┐  │   │
│  │  │  Session │ │  Crypto  │ │  Permission   │  │   │
│  │  │  Store   │ │  Stack   │ │  Service      │  │   │
│  │  └──────────┘ └──────────┘ └───────────────┘  │   │
│  │  ┌──────────┐ ┌──────────┐ ┌───────────────┐  │   │
│  │  │  Audit   │ │ Anomaly  │ │ IncidentChain │  │   │
│  │  │ Service  │ │ Engine   │ │ Service       │  │   │
│  │  └──────────┘ └──────────┘ └───────────────┘  │   │
│  └────────────────────┬───────────────────────────┘   │
│                       │                               │
│                       ▼                               │
│              ┌────────────────┐                       │
│              │    SQLite DB   │                       │
│              │  (WAL mode)    │                       │
│              └───────┬────────┘                       │
└──────────────────────┼────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│              Spring Boot Panel (独立 JVM)              │
│              http://127.0.0.1:8088                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────┐  │
│  │ Dashboard│ │ Players  │ │ Audit / Alerts / Logs│  │
│  └──────────┘ └──────────┘ └──────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

**关键设计决策**：Spring Boot 面板在**独立 JVM 进程中运行**，通过 SQLite 文件与 Minecraft 服务端通信，避免 NeoForge ClassLoader 与 Spring 的类加载冲突。

---

## 功能模块

### 1. 密码与鉴权

| 层级 | 算法 | 用途 |
|------|------|------|
| BCrypt (cost=12) | 密码哈希 | 防彩虹表、防暴力破解 |
| AES-256-GCM | 对称加密 | 加密 BCrypt 哈希存入 SQLite |
| RSA-2048 OAEP | 非对称加密 | 包装 AES 主密钥，密钥文件 `keys/private.pem` |

**sudo 会话**：OP 执行 `/sudo` → Qt 密码弹窗 → 验证成功 → 开启限时会话（默认 5 分钟，可配置）→ 会话内可执行受保护指令。

### 2. 指令门禁

配置中列出 24+ 条受保护原版指令（`give`、`ban`、`op`、`stop`、`gamemode`、`kick` 等），无活跃 sudo 会话时**直接拦截**。

- 三级风险：`HIGH` / `CRITICAL` / `NONE`
- 精细权限：`cmd.give`、`cmd.ban` 等可单独授权给特定角色
- admin→admin 可选自动提权（无需密码）

### 3. 全局行为审计

| 事件类型 | 记录内容 |
|----------|----------|
| 玩家生命周期 | 登录、登出、死亡、切维度 |
| 方块交互 | 破坏、放置（含坐标 + 方块 ID） |
| 物品流动 | 拾取、丢弃、合成、sudo 发放 |
| 社交 | 聊天（含敏感词过滤 + 禁言检测） |
| 指令 | 全部指令（含控制台），密码参数自动脱敏 |
| 移动 | 按距离/tick 间隔采样轨迹 |
| 安全事件 | sudo 鉴权成功/失败、密码修改/重置 |

### 4. 物品溯源

每次 `/sudo give` 发放物品时，生成唯一 `trace_id` 写入物品 NBT 的 `esplus_trace` 字段。物品被拾取、丢弃、合成时，链路自动延续。可查询任意物品的完整来源链。

### 5. 异常告警

基于滑动窗口的规则引擎：

| 规则 | 默认阈值 | 说明 |
|------|----------|------|
| CMD_BURST | 40次/60s | 指令爆发 |
| GIVE_BURST | 8次/60s | 物品发放异常 |
| BREAK_BURST | 80次/60s | 破坏速度异常 |
| SUDO_FAIL | 每次 | sudo 鉴权失败 |
| PROTECTED_CMD_BLOCKED | 每次 | 受保护指令被拦截 |

### 6. 事发链还原

以任意事件为中心，自动聚合 ±N 分钟窗口内的相关事件 + 移动轨迹 + 物品链路，还原完整事发经过。

### 7. Web 管理面板

`http://127.0.0.1:8088/`，三级角色（admin/moderator/viewer），Basic Auth 登录。功能包括：

- 在线玩家列表（血量、饥饿、坐标、 ping）
- 离线操作：ban/kick/op/whitelist/gamemode
- 控制台命令执行
- 审计事件搜索
- 告警管理（确认/忽略）
- 物品溯源查询
- 服务器性能（TPS/MSPT/内存/实体数）
- GameRule 修改、时间/天气/难度控制
- 计划任务（定时广播、自动重启等）
- 聊天禁言/敏感词管理

---

## 技术栈

| 层 | 技术 |
|----|------|
| Mod 框架 | NeoForge 21.1.235, MC 1.21.1 |
| 构建 | Gradle 8.x, NeoGradle 7.1.38 |
| Java | JDK 21 (Zulu) |
| 数据库 | SQLite (sqlite-jdbc 3.46.1.3, WAL mode) |
| 密码 | BCrypt + AES-256-GCM + RSA-2048 OAEP |
| 面板后端 | Spring Boot 3.3.5 + Thymeleaf + Spring Security |
| 密码输入 UI | Qt6/C++ (Win64) 原生弹窗 |
| 网络 | NeoForge Payload API (playToClient/playToServer) |

---

## 项目结构

```
ES+/
├── src/main/java/com/esplus/
│   ├── ES+.java              # @Mod 入口
│   ├── Config.java                   # 配置定义（30+ 项）
│   ├── security/
│   │   ├── SecurityService.java      # 核心安全服务
│   │   ├── crypto/                   # BCrypt / AES / RSA
│   │   ├── db/                       # DAO 层（8个表访问对象）
│   │   ├── gate/                     # 指令拦截器
│   │   ├── perm/                     # 权限定义与检查
│   │   ├── risk/                     # 风险分级
│   │   └── session/                  # sudo 会话管理
│   ├── audit/
│   │   ├── AuditService.java         # 审计写入调度
│   │   ├── BehaviorHooks.java        # NeoForge 事件钩子
│   │   ├── AnomalyEngine.java        # 异常检测引擎
│   │   ├── IncidentChainService.java # 事发链还原
│   │   ├── MovementTracker.java      # 移动采样
│   │   ├── ItemTraceNbt.java         # NBT 溯源标签
│   │   └── ...                       # DAO + 数据类
│   ├── command/                      # /esplus, /sudo 命令
│   ├── panel/                        # Spring Boot 桥接
│   ├── network/                      # 客户端-服务端网络包
│   ├── ui/                           # Qt 密码弹窗桥接
│   └── log/                          # Log4j 日志捕获
├── panel/                            # Spring Boot 子项目
├── native/                           # Qt6 C++ 密码输入程序
├── deploy/                           # 部署配置（反向隧道等）
├── build.gradle                      # 主构建脚本
└── build.bat                         # 一键构建
```

## 构建与部署

```bat
build.bat mod          # 构建 mod + panel + native
```

产物 `build/libs/esplus-1.0.0.jar` 放入服务器 `mods/` 目录即可。Panel fat jar 和 sqlite-jdbc 类文件已内嵌，无需额外依赖。

---

## 配置

服务器首次启动后在 `config/esplus-common.toml` 自动生成。关键配置项：

```toml
sudoSessionMinutes = 5       # sudo 会话有效期
panelPort = 8088             # 面板端口
panelBindAddress = "127.0.0.1"  # 建议保持 localhost，通过反向隧道暴露
panelUsername = "admin"      # 面板管理员账户
panelPassword = "esplus" # ⚠️ 务必修改默认密码
auditEnabled = true          # 开启审计
auditRetentionDays = 30      # 审计数据保留天数
protectedCommands = [...]    # 受保护指令列表
```
