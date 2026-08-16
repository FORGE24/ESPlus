# ES+ Bug 清单

> 扫描范围：Mod 端（security/audit/panel/network/command/ui/log）+ Spring Boot 面板 + 前端（app.js / Thymeleaf 模板）。
> 分级标准：
> - **CRITICAL** — 可直接被利用的越权/绕过，或造成不可逆数据损坏；
> - **HIGH** — 安全模块失效、数据一致性破坏、功能完全不可用；
> - **MEDIUM** — 局部功能错误、数据可被污染、性能/可用性风险；
> - **LOW** — 边界情况、资源增长、体验问题。

---

## CRITICAL

| # | 位置 | 问题 |
|---|------|------|
| C1 | `panel/.../PanelSecurityConfig.java:42,56` + `PanelApiController.java:106-119` | **API 越权：`/api/admins/**` 未受角色保护**。配置只匹配了 `/admins/**`（页面路径），`POST /api/admins/{uuid}/reset /unlock /role` 落入 `anyRequest().authenticated()`，任何已登录角色（含 viewer/moderator）都可调用：重置密码、解除锁定、**把任意玩家提升为 owner 角色** → 直接提权到游戏内管理权限。 |
| C2 | `panel/.../PanelSecurityConfig.java:63` | **`/api/**` 全部禁用 CSRF**。`POST /api/console`、`/api/players/ban`、`/api/admins/{uuid}/role` 等所有状态变更接口均为 cookie 会话认证 + 无 CSRF 防护，仅靠浏览器默认 SameSite=Lax 兜底。管理员登录状态下访问恶意页面即可被跨站触发停服、封禁、改角色。 |

## HIGH

| # | 位置 | 问题 |
|---|------|------|
| H1 | `security/gate/CommandGate.java:42-46,121-137` | **指令门禁绕过**：`rootCommand()` 只取最外层 Brigadier literal 且不归一化命名空间。`/minecraft:give`（命名空间别名节点名为 `minecraft:give`）、`/execute run give ...`（取到根节点 `execute`）均不在受保护表中 → `requiresSudo=false` → 无 sudo 会话也能执行受保护指令，审计与权限体系整体失效。 |
| H2 | `panel/RetentionCleanup.java:25-31` | **保留期清理破坏审计哈希链**：`AUDIT_HASH_CHAIN=true` 时直接 DELETE `global_events` 最旧行，不重算 `prev_hash`、不更新 `meta.audit_chain_tip`。每次清理后 `verifyChain()` 永久误报"链路被篡改"，合法的数据清理被当作完整性事故。且执行时未持 `database.lock()`。 |
| H3 | `audit/AuditService.java:90-98` + `audit/AnomalyEngine.java:152-154` | **Webhook 阻塞阻塞审计单线程**：`recordAsync` 在唯一的 `esplus-audit-writer` 线程上同步执行 `webhook.dispatch()`（HttpClient.send，8s 超时）。webhook 慢/挂时整条审计管线（事件、物品链、移动采样）全部阻塞在无界队列里。 |
| H4 | `audit/AdminRiskScorer.java:38,115-117,143-145` | **持全局 DB 锁做阻塞 HTTP**：`recompute()` 全程 `synchronized(database.lock())`，`raiseRiskAlert()` 内同步发 webhook（最长 8s），期间服务端主线程与审计写线程的所有 DB 访问全部停滞。 |
| H5 | 多个 DAO：`AlertDao.java:43,59,67`；`GlobalEventDao.java:209,262,279,305`；`ItemTraceDao.java:19,37,56,77`；`MovementDao.java:18,41`；`AutoResponseEngine.java:122`；`BehaviorHooks.java:77,91,135,161` | **共享单条 SQLite Connection 无锁并发**：除 MfaDao/PanelActionDao 外，其余 DAO 均不取 `database.lock()`，与审计写线程/面板线程并发使用同一 Connection（sqlite-jdbc 单连接非线程安全）→ SQLITE_BUSY/MISUSE，且 catch 吞掉异常表现为"查询失败"静默丢数据。`PanelActionDao.claimPending` 开事务期间其他线程写入会被连带提交/回滚。 |
| H6 | `panel/.../templates/console.html:75-80` | **控制台页面 3 秒无限整页刷新**：`setTimeout` 无条件 `window.location.replace('/console')`（本意是清 `msg` 参数一次性刷新）。结果：输入的指令在提交前就被清空、日志视图每 3 秒重置，控制台页面基本不可用。 |
| H7 | `panel/.../PanelPageController.java:1416-1431` + `LoginAttemptService.java` | **MFA 验证码无任何限速**：密码正确进入 MFA 后，6 位验证码可无限次尝试，`LoginAttemptService` 只统计密码失败，无 IP/会话级节流。泄露密码 + 暴力 6 位码（±1 窗口）可击穿 MFA。 |
| H8 | `panel/.../PanelSecurityConfig.java:82-95` + `LoginAttemptService.java:10-41` | **登录锁定可被远程 DoS 且按用户名共享**：5 次失败锁定 admin 15 分钟，攻击者无需成本即可把唯一管理员锁出面板；锁定只按 `username`（trim+lowercase），无 IP 维度。 |

## MEDIUM

| # | 位置 | 问题 |
|---|------|------|
| M1 | `panel/.../PanelQueryService.java:210-225` + `panel/.../PanelActionProcessor.java:160-178` | **任意控制台指令无白名单**：`enqueueConsoleCommand` 原样入队，消费端以控制台（OP4）权限 `performPrefixedCommand` 执行；审批队列关闭（`approvalEnabled=false`）时 `stop/op/ban-ip` 等直接生效。 |
| M2 | `panel/.../PanelGovernanceService.java:174-202` | **审批决策 TOCTOU**：`SELECT ... AND status='pending'` 与后续 UPDATE 非原子，两个并发审批（面板 + ops API）都对同一动作入队两次 → `stop_server`/`console_cmd` 重复执行。 |
| M3 | `security/db/SqliteDatabase.java:35,578-583` + `PanelActionDao.java:30-94` | **非原子跨线程事务**：`claimPending` 关闭 autoCommit 期间，其他线程对同一连接的写入自动加入该事务，回滚会**静默丢弃**审计事件/权限变更等其它线程的写入。 |
| M4 | `panel/.../PanelActionDao.java:30-94` | **`processing` 状态动作永不重捞**：服务端在 `markDone` 前崩溃后，卡在 `processing` 的 action（含 ban/op/stop）重启后永远丢失。 |
| M5 | `ui/QtPasswordPrompt.java:80-88` + `network/ClientPasswordHandlers.java:17-21` | **Qt 弹窗挂起 → 认证整体 DoS**：先 `readLine()` 后 `waitFor(5min)`，Qt 进程既不输出也不退出时 readLine 永久阻塞；且单线程 WORKERS 执行器使一次挂起毁掉该客户端之后所有密码/授权/重置流程。 |
| M6 | `ui/QtPasswordPrompt.java:77,96-101` | **stderr 合并流导致误判**：`redirectErrorStream(true)` 后只读一行并 Base64 解码，Qt 的任何警告行都会被当成"密码"→ 解码失败 → 上层按 `canceled` 处理，玩家无任何提示地无法鉴权；异常路径不销毁子进程（泄漏）。 |
| M7 | `panel/IsolatedSpringPanel.java:166` | **ops API token 明文落盘**：`esplus.opsApiToken` 明文写入 `application-runtime.properties`，与类注释"密码永不落盘/argv"的设计矛盾，读得到面板目录即拿到 token。 |
| M8 | `ESPlus.java:79-84` / `IsolatedSpringPanel.java:118-132` | **面板子进程无关闭钩子**：服务端被强杀/崩溃时 Spring 子进程成为孤儿进程，端口与 SQLite 句柄不释放，下次启动面板绑定失败。 |
| M9 | `security/perm/PermissionService.java:102-118` | **`reconcilePlainOpRoles` 每次重启清空 op 用户的自定义授权**：`seedRoleDefaults` → `replaceAll` 全量 DELETE+重插角色模板，面板管理员给 op 角色玩家单独授予的 `cmd.*` 权限在每次重启后消失（权限回退/数据丢失）。 |
| M10 | `security/perm/PermissionService.java:39-52,54-65` | **`ensureSeeded` 自动重新种入默认权限**：管理员主动删光某用户权限行（如停权）后，下次 `has()` 因 `hasAny==false` 又自动恢复角色默认权限 → 无法真正撤销权限（deny 失效）。 |
| M11 | `security/gate/CommandGate.java:59-62,102-119` | **自动提权目标识别错误**：`findOnlinePlayerTarget` 取参数中第一个匹配在线玩家的 token；`AUTO_SUDO_ADMIN_TO_ADMIN` 开启时，`/give @a <半管名> 64` 之类只要参数里恰好含某半管理员名字即可免密通过门禁。 |
| M12 | `SecurityService.java:392-416` + `security/db/MfaDao.java:16-56` | **TOTP 无失败锁定 + 明文存储**：TOTP 路径不校验是否已通过密码关、无尝试次数限制；且 MFA secret 明文存 TEXT（密码却是 AES 加密），DB 泄露即二因子失效。 |
| M13 | `security/risk/TaskRiskService.java:22-24` + `SudoCommands.java:178-196` | **被降权 OP 可持续 `/sudo give`**：`/sudo` 在 EXEMPT 列表，`give` 只查会话不复查 `isMinecraftOperator`；降权玩家的活动会话通过滑动续期无限使用。 |
| M14 | `panel/.../PanelQueryService.java:1282-1305` | **CSV 公式注入**：导出审计 CSV 只转义引号/逗号/换行，未中和前导 `=`/`+`/`-`/`@`，审计 detail 中玩家可控内容在 Excel 中可执行公式。 |
| M15 | `panel/.../PanelApiController.java:52-64,66-73` + `PanelSecurityConfig.java` | **GET 敏感数据对 viewer 开放**：`/api/audit`、`/api/users`（含 failed_attempts、locked_until）、`/api/logs` 无角色限制，任何已登录角色可读；`playerInventory` 的 `%q%` 匹配可用 `q=%` 枚举全服玩家背包。 |
| M16 | `panel/.../PanelGovernanceService.java:638-658` | **哈希链 canonical 对 NULL 坐标不一致**：`String.valueOf(row.get("x"))` 产生字面 `"null"`，而写侧用空串 → 完整性校验误报链断。 |
| M17 | `audit/BehaviorHooks.java:199,216-219` | **受保护指令事件重复记录**：CommandGate 已记 `protected_*` 事件，BehaviorHooks LOWEST 优先级再记一条 → 每条约单两行，双倍写库并双倍喂给风险计分与告警。 |
| M18 | `audit/AdminRiskScorer.java:56` | **整个 security 类目都算 sudo 活动**：鉴权失败、权限拒绝等也累计进 sudo 计数，抬高风险分，造成误报 HIGH 告警。 |
| M19 | `audit/AdminRiskScorer.java:115-117,127-149` | **ADMIN_RISK 告警无去重**：每次 recompute 越界就插入新告警（去重仅靠随机 alert_id），面板周期性刷新可刷满 alerts 表。 |
| M20 | `audit/BehaviorHooks.java:264,282,299` | **物品溯源链断裂**：`linkItem` 恒传 `parentTraceId=null`，合成产出物的父链路从不记录，`parent_trace_id` 列与索引形同虚设，跨合成无法追溯来源。 |
| M21 | `audit/ItemTraceNbt.java:51-52` + `audit/AuditService.java:125-131` | **追踪 ID 落库失败仍写入 NBT**：`createItemTrace` 捕获 SQL 异常后仍返回 traceId，`ensureTrace` 把不存在的 id 印进物品 NBT，后续 linkItem 因外键约束静默失败。 |
| M22 | `panel/.../PanelActionProcessor.java:174-177,684-688` | **指令执行结果未校验即标记成功**：`applyConsole`/`applyGiveItem` 不检查 `performPrefixedCommand` 是否解析执行成功，拼错的物品/指令也记 audit 成功，审计轨迹被污染；`applyGiveItem` 未对 `itemId` 做 `sanitizeId()` 过滤。 |
| M23 | `SecurityService.java:511-514` | **锁定模式会话上限被滑动续期绕过**：`openSudoSession` 限制 TTL，但每次受保护指令 `refreshSudoSession` 都恢复完整会话时长，锁定模式（1 分钟 TTL）策略失效。 |
| M24 | `panel/.../templates/players.html:13`、`gamerules.html:15`、`messages-schedule.html:13` 等 | **全页 meta 刷新（8~12s）清空表单**：正在输入的封禁原因、禁言理由、广播文本、游戏规则数值在定时刷新时全部丢失；bans/scoreboard/items-inventory/messages-bossbar 等同病。 |
| M25 | `panel/.../static/app.js:31-52,77-96,98-127` | **SPA 视图渲染竞态**：`renderX` 先 await fetch 再赋值 `innerHTML`，快速点击导航时旧响应后到会覆盖新视图；搜索/告警按钮无 in-flight 保护与错误处理（401 无提示、双击重复 ack）。 |
| M26 | `panel/.../static/app.js:67-74` | **搜索写入已不存在的 DOM**：请求返回前切换视图，`searchBody` 为 null → 未处理 TypeError；并发两次搜索旧结果覆盖新结果。 |
| M27 | `panel/IsolatedSpringPanel.java:39-46` | **面板默认密码守卫只拦字面量 "esplus"**：`panelPassword=""` 时放行，空密码启动管理面板。 |

## LOW

| # | 位置 | 问题 |
|---|------|------|
| L1 | `security/crypto/RsaKeyManager.java:41-47,87-89,97-101` | RSA 私钥以默认 umask（0644）写盘，多用户主机上任何本地用户可读 `private.pem` → 解包 AES 主密钥解密全部密码哈希。 |
| L2 | `security/crypto/TotpService.java:46` | TOTP 码用 `equals` 比较非恒时，存在逐位时序侧信道。 |
| L3 | `security/crypto/BcryptPasswordService.java:15-28` + `org/mindrot/jbcrypt/BCrypt.java` | 无密码长度上限、自带 BCrypt 不按 72 字节截断（与标准实现不互通）；损坏的短哈希使 `matches()` 抛未捕获的 `StringIndexOutOfBoundsException`。 |
| L4 | `audit/MovementTracker.java:16,49` | `last` 采样表永不清理，长期运行内存无界增长；`tickCounter` int 在约 3.4 年后溢出导致采样错乱。 |
| L5 | `audit/AlertWebhookDispatcher.java:44-48,92-93` | 冷却 map check-then-act 竞态可重复推送；`testPing()` 无条件返回 true，面板误报 webhook 正常。 |
| L6 | `audit/AnomalyEngine.java:118-127` | burst 桶 deques 不清理过期条目，按"去重后的 actor 数"缓慢无界增长。 |
| L7 | `audit/AuditBlockSigner.java:32-37` | 计数先归零再签名，`signNow` 失败时整批事件静默失去区块签名覆盖。 |
| L8 | `panel/RetentionCleanup.java:24-31` | 只清 6 张表；`config_revisions`/`security_snapshots`/`panel_actions`/`approval_requests`/`webhook_delivery_log`/`audit_block_signatures` 永不清理，DB 无限膨胀。 |
| L9 | `log/ServerLogCapture.java:115-119` | 热路径对 `ConcurrentLinkedQueue` 每事件调用 O(n) 的 `size()`；检查-再 poll/offer 竞态使上限可能被突破。 |
| L10 | `ui/PasswordPromptBridge.java:52-57,91-94` | 新请求覆盖旧 pending 不回调 `onCancel`；过期清理仅在下次请求时触发，未响应的玩家残留回调条目。 |
| L11 | `security/session/SudoSessionStore.java` | `expiresAt == now` 边界处误判过期（微小逻辑边界）。 |
| L12 | `panel/.../PanelOpsApiController.java:93` | ops token 用非恒时 `equals` 比较；失败无限速无审计。 |
| L13 | `panel/.../PanelQueryService.java:20-34` | 面板指向空/新 SQLite 时 `/api/dashboard` 首页 500，且 get 请求触发 7 天全量 `recomputeRisk`（GET 副作用 + 写库放大）。 |
| L14 | `panel/.../PanelActionProcessor.java:593-619` | `applyTitle` 只替换引号不转义反斜杠/控制字符，含 `\` 的 payload 生成非法 JSON。 |
| L15 | `panel/.../templates/status-connection.html:22` | 绑定 0.0.0.0 时页面显示不可访问的 `http://0.0.0.0:8088/`。 |
| L16 | `panel/.../static/app.js:26,87,98-127,148-150` | 空串 `trace_id` 被当作无溯源链接（应判 null）；`/api/incident/` 空 id 404 无反馈；trace/incident"未找到"状态无 UI 展示；除首屏外所有视图 fetch 失败无错误提示与超时。 |
| L17 | `panel/.../static/app.js` 同源 | `api()` 无超时/abort，服务端挂起时视图永远停在"加载中"。 |

---

## 修复优先级建议

1. **立即修复（CRITICAL/HIGH）**：C1、C2、H1、H6、H7、H8 — 均涉及越权或功能完全失效，且改动小（补一条 requestMatcher、给 `rootCommand` 做命名空间归一化+递归解析 execute/function、`console.html` 加 `if (params.has('msg'))`、MFA 接入登录限速等）。
2. **尽快修复**：H2-H5（哈希链清理、webhook 移出锁/写线程、DAO 统一加锁）、M1-M3、M7。
3. **排期修复**：其余 MEDIUM/LOW，随重构与回归测试一起处理。

> 说明：本清单由静态审查产出，多数条目已人工复核确认（C1、H1、H2、H6 等已逐行验证）；个别条目依赖运行时环境（Qt 行为、并发时序），修复时应以可复现测试验证。
