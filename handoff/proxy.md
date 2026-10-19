# 群组服务器跨服登录（KaProxy）

覆盖:通过 KaProxy 在群组服务器间共享登录会话的实现、接入点与注意事项。

## 现状

- 新增包 `org.katacr.kalogin.proxy`：
  - `ProxyProtocol.kt`：KaProxy 二进制协议信封（`kaproxy:main`，MAGIC/VERSION/module/action）。
  - `ProxySessionManager.kt`：注册双通道、进服查询会话、上报登录/登出/注销/改密、超时与清理。
- `KaLogin.kt`：字段 `proxySessionManager`，`onEnable` 中初始化、`onDisable` 中 `shutdown()`。
- 配置 `config.yml` 新增 `proxy:` 段，`config-version` 10 → 11（`ConfigUpdater.kt` 同步）。
- 语言键 `proxy.session-restored`、`proxy.unavailable-kick`（中英）。
- 开放 API 新增 `PlayerProxyRestoreEvent` 与 `KaLoginListener.onPlayerProxyRestore`；`KaLoginAPI.callPlayerProxyRestore`。

## 协议（module=kalogin）

后端 → 代理（均经玩家连接发送）：

- `auth`：uuid、name、ip —— 登录/注册/自动登录成功时。
- `query`：uuid、name、ip —— 进服时查询是否有跨服会话。
- `logout`：uuid —— `/logout` 或 `KaLoginAPI.logout`。
- `unregister`：uuid、name —— 注销账户（管理员代发时取首个在线玩家作载体）。
- `password_changed`：uuid —— 改密/找回密码成功。
- `login_failed`：uuid、IP —— 每次密码校验失败上报代理，用于按来源 IP 临时封禁。

代理 → 来源后端：`session`：uuid、authenticated、name、authTime、loginIp、reason。
代理 → 受害者所在后端：`security_alert`：目标 uuid/name 与攻击 IP；KaLogin 在后端按 UUID 单人提示。

## 接入点

- `LoginListener.onJoin`（内置模式）：loading 遮罩后先 `proxySessionManager.query`，命中走 `restoreProxySession`，未命中/超时走 `beginLocalAuth`（原逻辑）。
- `AuthMeLoginListener.onPlayerJoin`（AuthMe 模式）：query 命中走 `restoreProxySession`（`forceLogin` 恢复，`proxyRestoredPlayers` 短路 LoginEvent）；未命中走 `beginLocalFlow`。
- 登录/注册/自动登录成功：`reportAuth`（`LoginListener`、`AuthMeLoginListener`）。
- 登出：`LogoutCommand` 与 `KaLoginAPI.logout` 先 `reportLogout`，代理接管时跳过本服踢出；`AuthMeLoginListener.onAuthMeLogout` 在群组模式下不销毁会话、不踢出（避免切服误判）。
- 注销：`KaLoginCommand.handleDelete`、`AuthMeCommandExecutor.handleDeleteCommand`、`AuthMeLoginListener` 的玩家/管理员注销事件。
- 改密：`ChangePasswordCommand` 两个成功分支、`EmailBindManager` 找回密码成功分支。
- `AntiCheatManager.onPlayerQuit`：调用 `proxySessionManager.onQuit` 取消未完成查询（不代表登出）。

## 关键决策

- **代理唯一权威**：后端只查询与上报，不判定跨服有效性；真退服由代理 `DisconnectEvent` 销毁会话，切服保留。
- **查询驱动 + 超时降级**：进服延迟 `query-delay-ticks` 后查询（避开 configuration 阶段丢包），`query-timeout-ms` 超时按代理不可用处理；`require-proxy` 决定踢出或回退本地。
- **IP 绑定在代理侧**：后端只上报 IP，代理按 `bind-ip` 校验，避免后端各自为政。
- **跨服恢复收尾可配置**：`restore-welcome`（按 DB 条款）、`restore-email-prompt`（默认不重复）、`replay-login-actions`（默认不重放）。
- **AuthMe 恢复**：目标服 `forceLogin` 并用 `proxyRestoredPlayers` 短路 `LoginEvent`，避免重复欢迎/事件；`forceLogin` 失败则回退本地界面。
- **安全告警（2026-10-14）**：Velocity `CONFLICTING_LOGIN` 表示同名新连接被拒；KaProxy 只在存活玩家有已认证会话时告警，按 UUID 冷却 600 秒，并将告警单播至玩家所在后端。KaLogin 后端由 `proxy.security-alert` 渲染展示，不包含攻击者 IP。
- **失败 IP 封禁（2026-10-14）**：内置登录与 AuthMe 模式每次密码错误均报告来源 IP；幸存连接已认证的冲突登录也计入，未认证占位连接不计（避免把正版被拒 IP 误计为攻击）。KaProxy 默认 10 分钟内累计 10 次失败后，在代理内临时封禁该 IP 30 分钟，并于 PreLogin 阶段拒绝。计数/封禁仅在代理内存，代理重启后清空。

## 踩过的坑

- `PluginMessageListener.onPluginMessageReceived` 的参数 `Player` 在 Kotlin 覆写时必须为非空，不能声明为 `Player?`（编译报「overrides nothing」）。
- 代理→后端消息的载体玩家可能为空，会话匹配必须使用载荷中的 UUID，不能依赖回调的 `player`。
- AuthMe 的 `LogoutEvent` 可能在切服/退服时触发；若在群组模式下据此销毁会话，会导致切服后需重新登录，故群组模式下该事件不销毁会话。
- 后端踢出在代理网络下可能被转投 fallback 子服而非断网；群组模式下登出改由代理 `disconnect` 处理。
- 既有 KaProxy 安装不会自动合并 `modules.kalogin` 配置，需手动补充。

## 当前待办

- **2026-10-16 认证就绪事件（已部署七服）**：新增 Bukkit `PlayerAuthenticationReadyEvent`，由 `LastSeenManager.onAuthenticated` 在本地上次位置处理后发布；即使 `last-seen.enabled: false` 也发布。KaTpa 可选监听并结合 `KaLoginAPI.isPlayerLoggedIn`/`AntiCheatManager.isAuthenticating` 等待跨服落点。代理端异步 last-seen 后续切服不由此事件保证完成；待真实玩家联测。

- [ ] 端到端联调（真实 Velocity + 后端 + 共享 MySQL）尚未执行；KaLogin 与 KaProxy 已构建，KaProxy 单元测试通过。
- [ ] `safer` 化:考虑会话变更时由代理向其它子服广播失效，清理潜在陈旧状态。
- [ ] 可选:跨服恢复时按会话去重邮箱提示的持久化（目前默认不重复即可）。
