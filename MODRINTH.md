# ES+

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange?style=flat-square)](https://neoforged.net/)

**Server-side security suite** — OP password auth, protected command gating, full behavior audit, item traceability, anomaly alerts, incident chain reconstruction, and an embedded admin web panel. All in one mod. Drop into `mods/`, no extra setup.

Licensed under **LGPL-3.0-or-later**.

> 服务端安全套件：OP 密码鉴权、指令门禁、全行为审计、物品溯源、异常告警、事发链还原、Web 管理面板。一个模组搞定。
> 
> サーバーサイドセキュリティスイート：OP パスワード認証、コマンドゲート、全行動監査、アイテムトレース、異常アラート、インシデントチェーン復元、Web 管理パネル。
> 
> 서버 사이드 보안 스위트: OP 비밀번호 인증, 명령어 게이트, 전체 행동 감사, 아이템 추적, 이상 알림, 사건 체인 복원, 웹 관리 패널.
> 
> Serverseitige Sicherheitssuite: OP-Passwort, Befehlssperre, Verhaltensprüfung, Item-Rückverfolgung, Anomalie-Warnungen, Web-Panel.
> 
> Серверный комплекс безопасности: пароль OP, командный шлюз, аудит действий, отслеживание предметов, оповещения об аномалиях, веб-панель.

---

## Features / 功能

### 🔐 OP Sudo Password
OP alone is not enough. Sensitive commands require a **timed sudo session** unlocked by password.
- BCrypt (cost=12) → AES-256-GCM → RSA-2048 key wrap → SQLite
- Native Qt password prompt (no chat-based password leaking)
- Configurable session length (default 5 min), lockout after N failed attempts

### 🚫 Protected Command Gate
24+ vanilla commands (`give`, `ban`, `op`, `stop`, `gamemode`, `kick`, `tp`, `kill`, etc.) are **blocked** unless you have an active sudo session AND the right permission node.

### 📋 Full Behavior Audit
Every action is recorded with timestamp, player, dimension, and coordinates:
- Login / Logout / Death / Dimension change
- Chat messages (with optional word filter & mute)
- Commands (password auto-redacted)
- Block break & place
- Item pickup, toss, craft
- `/sudo give` (special audit trail)

### 🔗 Item Traceability
Every `/sudo give` stamps a unique `esplus_trace` NBT tag on the item. As the item moves through players (pickup, toss, craft), the trace chain auto-extends. **Full lifetime provenance**.

### 🚨 Anomaly Alerts
Sliding-window rule engine detects command bursts, mass item gives, suspicious block breaking, and failed sudo attempts.

### 🔍 Incident Chain Reconstruction
Click any event in the panel → auto-aggregates nearby events + movement path + item traces into a full incident timeline.

### 🖥️ Embedded Admin Panel
**Isolated JVM** Spring Boot web panel at `http://127.0.0.1:8088/`. Three roles (admin / moderator / viewer), Basic Auth. Dashboard, player management, console, audit search, alerts, item trace lookup, world settings, chat moderation, scheduled tasks.

---

## Commands / 指令 / コマンド

| Command | 中文 | 日本語 |
|---------|------|--------|
| `/esplus password set` | 设置 sudo 密码 | sudo パスワード設定 |
| `/esplus password change` | 修改 sudo 密码 | sudo パスワード変更 |
| `/esplus resetpassword <player>` | 重置密码 (控制台) | パスワードリセット |
| `/sudo` | 打开 sudo 鉴权 | sudo 認証 |
| `/sudo status` | 查看会话时间 | セッション残り時間 |
| `/sudo exit` | 关闭会话 | セッション終了 |
| `/sudo give <player> <item> [count]` | 受保护发放 (带溯源) | 保護アイテム付与 |

Aliases: `/setoppw`, `/changepw`

---

## Configuration / 配置

First run generates `config/esplus-common.toml`. Key settings:

```toml
sudoSessionMinutes = 5        # Sudo session TTL (1–120)
maxFailedAttempts = 5         # Lockout threshold
lockMinutes = 15              # Lockout duration
minPasswordLength = 6         # Minimum password length
auditEnabled = true           # Enable full audit recording
auditRetentionDays = 30       # Auto-delete old audit data (0 = keep forever)
panelEnabled = true           # Start web admin panel
panelPort = 8088              # Panel HTTP port
panelBindAddress = "127.0.0.1" # Keep localhost; expose via reverse tunnel
```

---

## Installation / 安装

1. Drop `esplus-1.0.0.jar` into your server's `mods/` folder
2. Start the server
3. OP yourself, then run `/setoppw` to set your sudo password
4. Access the panel at `http://127.0.0.1:8088/` (default: `admin` / `esplus` — **change immediately!**)

**Requires**: NeoForge 21.1.235+ for Minecraft 1.21.1. **Server-side only** — clients do not need to install this mod.

---

## Tech Stack / 技术栈

| Layer | Technology |
|-------|-----------|
| Mod Loader | NeoForge 21.1.235 |
| Java | JDK 21 |
| Database | SQLite (WAL mode) |
| Crypto | BCrypt + AES-256-GCM + RSA-2048 OAEP |
| Panel Backend | Spring Boot 3.3.5 + Thymeleaf + Spring Security |
| Password UI | Qt6/C++ native prompt |

## ⚠️ Important Notes

- **Change the default panel password** (`esplus`) immediately after first launch
- Panel is `127.0.0.1` by default — expose via reverse tunnel for remote access, do NOT set `0.0.0.1` on public networks without HTTPS
- The mod is **server-side only** — clients do not need to install it (Qt password prompt is auto-delivered via NeoForge networking)
