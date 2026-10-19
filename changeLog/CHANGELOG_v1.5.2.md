# KaLogin v1.5.2 更新日志

## 版本信息

- **版本号**: 1.5.2
- **发布日期**: 待定

---

## 中文

### 群组服务器跨服登录（KaProxy）

- 新增 `proxy` 配置节，配合 KaProxy 在群组服务器（Velocity / BungeeCord）之间共享登录会话：玩家在一个子服登录后，切换到其它子服无需重复登录。
- 内置 KaLogin 模式与 AuthMe 模式均支持跨服会话查询与登录态恢复；AuthMe 模式下各子服的 AuthMe 需共享同一数据库。
- 登录、注册、改密、注销、登出等事件会上报至代理；`/logout` 时由代理使会话失效并断开整个群组连接。
- 代理不可用时按 `proxy.require-proxy` 决定拒绝进入或回退本地登录/注册流程。
- 跨服恢复时的收尾行为可分别配置：`restore-welcome`（欢迎/条款界面）、`restore-email-prompt`（邮箱绑定提示）、`replay-login-actions`（重放 `events.login` 动作，默认不重放）。
- 开放 API 新增 `PlayerProxyRestoreEvent` 回调，跨服恢复登录态时通知接入方。
- 新增 `proxy.server-name`：本服在代理中的注册名，须与 `velocity.toml` / Bungee `config.yml` 一致；群组内每台后端必须填写各自的名称。

### 自动回到上次下线位置

- 新增 `last-seen` 配置节：玩家登录完成后可自动回到上次下线的子服与坐标，替代每次都从默认服（Lobby）开始。
- 退服（含切服）时把当前坐标与所在子服名原子写入共享数据库 `kalogin_lastseen`；`/logout` 或注销会删除该记录。
- 新增 `last-seen.blacklist`：位于黑名单子服（默认 `pve`、`pvp`）退服时不记录坐标，下次登录回默认服出生点。
- 手动模式下（KaProxy `modules.lastseen.mode: manual`）登录完成后弹出“是否返回上次位置”确认框（确定 / 取消），取消后保留记录，下次登录仍会询问。
- 停服时会主动为所有在线玩家写入位置，避免关服过程中丢失记录。
- 三种回位模式（直连 / 手动确认 / 间接自动）均由 KaProxy 的 `modules.lastseen` 控制，KaLogin 侧无需额外配置模式。

### 安全提醒与登录失败封禁

- 每次密码校验失败都会把来源 IP 上报代理，由 KaProxy 按窗口累计失败次数，达到阈值即临时封禁该 IP 并在代理登录阶段拒绝。
- 代理判定为冲突登录（其他设备尝试登录一个已登录的账户）时，会向仍在线的账户本人定向发送安全告警，提示“其他设备正在尝试登录您的账户，建议及时修改密码”；告警按玩家冷却，且不展示攻击者 IP。
- 安全告警包按目标 UUID 校验，客户端伪造的告警包会被直接忽略。

### 认证就绪事件

- 新增 Bukkit 事件 `PlayerAuthenticationReadyEvent`（`org.katacr.kalogin.listener`），在认证收尾与上次位置处理完成后触发；其他插件可据此安全执行等待落点等操作。
- 未启用 `last-seen` 时同样会触发该事件。

### 其他改进

- 新增 `login.execute-events-on-auto-login`（默认 `false`）：控制同 IP 自动登录成功后是否执行 `events.login` 动作队列。
- 新增 `proxy.restore-message`（默认 `true`）：控制跨服恢复登录态时是否发送“检测到群组登录状态，已自动登录”提示语。
- 认证期间不再改写玩家游戏模式，避免玩家在认证期间跨世界传送后被进服时的旧模式覆盖。
- `plugin.yml` 的版本号改为由构建流程注入（`${version}`），避免手工版本号与构建产物不一致。

### 配置升级

- `config-version` 由 `10` 升级到 `16`，插件会自动备份旧配置并补全 `proxy`、`last-seen` 节点。
- `proxy.server-name` 默认为空，需手动填写为各后端自身的代理注册名；把 `lobby` 复制到其它后端会导致“大厅服名 + 子服坐标”的错误回位。
- 代理端 KaProxy 的 `modules.kalogin`、`modules.lastseen`、`database` 不会自动合并已有配置，需手动补充。
- 语言文件新增 `proxy.security-alert` 与 `last-seen.*` 节点，现有语言文件会自动补全。

---

## English

### Group Server Login (KaProxy)

- Added a `proxy` configuration section that shares the login session across a group of backends (Velocity / BungeeCord) via KaProxy: a player logged in on one backend no longer has to log in again when switching to another one.
- Both the built-in KaLogin mode and the AuthMe mode support cross-server session lookups and login-state restoration; in AuthMe mode every backend must share the same AuthMe database.
- Login, registration, password change, account deletion and logout are reported to the proxy; `/logout` invalidates the session and disconnects the whole group through the proxy.
- When the proxy is unavailable, `proxy.require-proxy` decides whether the player is rejected or falls back to the local login/registration flow.
- The finishing steps of a cross-server restore are configurable: `restore-welcome` (welcome/terms dialog), `restore-email-prompt` (email binding prompt) and `replay-login-actions` (replaying `events.login` actions, disabled by default).
- The open API adds a `PlayerProxyRestoreEvent` callback that notifies integrations when a login state is restored across servers.
- Added `proxy.server-name`: the name this backend is registered under in the proxy. It must match `velocity.toml` / the Bungee `config.yml`, and every backend in the group must use its own name.

### Returning to the Last Position

- Added a `last-seen` configuration section: after login the player can return to the server and coordinates they last logged out from, instead of always starting in the default server (Lobby).
- On quit (including a server switch) the current coordinates and the server name are written atomically to the shared database table `kalogin_lastseen`; `/logout` and account deletion remove that record.
- Added `last-seen.blacklist`: quitting in a blacklisted server (`pve` and `pvp` by default) does not record coordinates, so the next login starts at the default server spawn.
- In manual mode (KaProxy `modules.lastseen.mode: manual`) a "return to your last position?" confirmation dialog is shown after login (confirm / cancel); cancelling keeps the record and the question is asked again next time.
- On shutdown the position of every online player is written explicitly, so records are not lost while the server stops.
- The three return modes (direct / manual confirmation / indirect automatic) are controlled by KaProxy's `modules.lastseen`; KaLogin needs no extra mode setting.

### Security Alerts and Failed-Login Bans

- Every failed password check reports the source IP to the proxy, which counts failures in a time window and temporarily bans the IP once the threshold is reached, rejecting it during the proxy login stage.
- When the proxy detects a conflicting login (another device trying to log into an account that is already logged in), a security alert is delivered to the still-online account owner: "another device is trying to log into your account, please change your password". Alerts are rate-limited per player and never show the attacker's IP.
- Alert packets are validated against the target UUID; forged packets sent by clients are ignored.

### Authentication Ready Event

- Added the Bukkit event `PlayerAuthenticationReadyEvent` (`org.katacr.kalogin.listener`), fired once authentication and the last-position handling are complete, so other plugins can safely perform follow-up actions such as waiting for a teleport.
- The event is fired even when `last-seen` is disabled.

### Other Improvements

- Added `login.execute-events-on-auto-login` (default `false`): controls whether the `events.login` action queue runs after a same-IP automatic login succeeds.
- Added `proxy.restore-message` (default `true`): controls whether the "group login session detected, logged in automatically" message is sent when a login state is restored.
- The player's game mode is no longer rewritten during authentication, so a world change while authenticating is not overwritten by the mode captured on join.
- The version in `plugin.yml` is now injected by the build (`${version}`), so it can no longer drift from the built artifact.

### Configuration Upgrade

- `config-version` was raised from `10` to `16`; KaLogin backs up the old configuration and adds the `proxy` and `last-seen` sections automatically.
- `proxy.server-name` is empty by default and must be set to each backend's own proxy registration name; copying `lobby` to the other backends causes wrong returns ("Lobby name + instance coordinates").
- KaProxy's `modules.kalogin`, `modules.lastseen` and `database` sections are not merged into an existing proxy configuration automatically and must be added manually.
- The language files gain `proxy.security-alert` and `last-seen.*` keys; existing language files are patched automatically.

---

## Upgrade Guide

### Upgrading from 1.5.1 to 1.5.2

1. Stop the server (or unload the plugin) and back up the KaLogin data folder and the shared database.
2. Replace the plugin JAR with `KaLogin-1.5.2.jar`.
3. Fully restart the server. `config.yml` is upgraded to `config-version: 16` automatically and the `proxy` / `last-seen` sections are added; review the new keys afterwards.
4. Single-server deployments: no further action is required; `proxy.enabled` stays `false` and the `last-seen` section is ignored.
5. Group deployments: upgrade every backend in the group to this version, then set `proxy.server-name` to each backend's own proxy registration name and add the missing KaProxy sections (`modules.kalogin`, `modules.lastseen`, `database`).
6. After upgrading, verify that switching servers no longer asks for a password, that the last position is restored, and that the security alert is delivered to the account owner when a conflicting login is detected.
