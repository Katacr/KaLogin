# 上次下线位置（KaProxy）

覆盖:通过 KaProxy 记录并恢复玩家上次下线子服与坐标的后端实现。

> 最后更新:2026-09-20

## 现状

- `proxy/LastSeenManager.kt`:
  - 玩家退服(含切服)时把坐标**直接写入共享 MySQL** `kalogin_lastseen`(不再走插件消息载体)。
  - 接收代理下发的 `lastseen/teleport`,缓存并在玩家认证完成后应用。
  - 认证完成后发送 `lastseen/ready`,由代理决定是否前往。
  - 位置世界无效/越界时发送 `lastseen/abort`,代理回退默认服。
- `DatabaseManager.kt`:
  - `createTable` 新建 `kalogin_lastseen(uuid PK, server, world, x, y, z, yaw, pitch, updated_at)`,server/坐标列均可空。
  - `updateLastSeen(...)`:退服写**坐标 + server 列**(原子,带 `updated_at` 单调守卫,MySQL 用 `IF()` 逐列)。**2026-09-23 修正**:原实现只写坐标不写 server,导致“server=lobby 却存着 pve 坐标”的错配;现同时写 server(取 `proxy.server-name`),代理真正离开时再写一次作为权威值。
  - `deleteLastSeen(uuid)`:登出/注销删除。
  - 新增 `mysql` 标志用于选择 upsert 语法。
- `KaLogin.kt`:字段 `lastSeenManager`,`onEnable` 初始化、`onDisable` 先 `reportAllOnline()` 再 `shutdown()`。**2026-09-23 修正**:停服时 `PlayerQuitEvent` 可能晚于 `onDisable`,原实现直接关库会丢弃异步写;现于关库前主动为所有在线玩家写位置,由 `close()` 的 `awaitTermination` 等待落盘。
- 配置 `config.yml`:
  - 新增 `proxy.server-name`(本服代理注册名,须与 velocity.toml 一致;留空则不记录)。
  - `last-seen.enabled`(默认 `true`,依赖 `proxy.enabled`)。
  - **`last-seen.blacklist`(2026-09-23 新增)**:不记录位置的子服名列表(默认 `[pve, pvp]`)。位于黑名单服退服时**不写坐标**,下次登录无记录 → 直连默认服(Lobby)出生点。
  - `config-version` 13 → 14。
- `AntiCheatManager.onPlayerQuit` 调用 `lastSeenManager.onPlayerQuit`(写库 + 清理未应用传送)。
- 认证成功的各尾部(内置/AuthMe 的手动登录、注册、自动登录、跨服恢复)调用 `lastSeenManager.onAuthenticated`(应用传送 + 发送 `ready`)。
- `ProxySessionManager.reportLogout` / `reportUnregister` 调用 `lastSeenManager.suppressUpdate` 并 `deleteLastSeen`,避免登出后的退服事件复活记录。

## 协议(module=lastseen)

后端 → 代理:`ready`(uuid)、`abort`(uuid)、`confirm`(uuid)、`decline`(uuid)

代理 → 目标后端:`teleport`(uuid,world,x,y,z,yaw,pitch)、`offer`(uuid,delaySeconds,targetServer)

> 坐标不再经代理中转:后端直接写库,代理只读。`offer`/`confirm`/`decline` 仅手动模式使用。

## 手动模式(2026-09-30)

- 模式开关在 **KaProxy** 的 `modules.lastseen.mode`(auto/manual),后端无需配置。
- 后端 `LastSeenManager` 新增:
  - `handleOffer`:`onPluginMessageReceived` 收到 `offer` 后,按 `join-delay-seconds` 延迟弹窗(用于先下载资源包)。
  - `showLastSeenConfirm`:调用 `dialogPlatform.showLastSeen(...)` 弹"确定/取消"确认框;确定回传 `confirm`,取消回传 `decline`。
  - `pendingOffers` 记录延迟任务,退服/停服时取消。
- `LoginDialogPlatform` 新增 `showLastSeen(player,title,body,confirmLabel,cancelLabel,onConfirm,onCancel)`;Paper 走 `confirmationDialog`,Spigot 走 `ConfirmationDialog`。
- 反作弊 `resendDialog` 新增 `"last-seen"` 分支(认证期间旋转视角会重弹)。
- 语言新增 `last-seen.{dialog-title,dialog-body,confirm-button,cancel-button}`(中英)。
- 取消后**保留记录**,下次登录仍询问。

## 关键决策

- **坐标直接写库**:原实现靠"同服其他在线玩家"作插件消息载体上报,单人子服退服必丢。改为直接写 MySQL 后根因消失,不再需要载体选择逻辑。
- **server 列由代理写**:Paper 的 `PlayerQuitEvent` 无法区分切服与真离开,只有代理能区分,故 server 由代理在真正离开时写入;后端只写坐标。
- **时间戳守卫**:切服时源服/目标服退服事件可能乱序,`updated_at` 单调保证最后一次写入胜出。
- **认证后应用传送**:`onAuthenticated` 在所有登录收尾之后调用,避免与防作弊位置锁定冲突。
- **直连由代理负责**:KaProxy 默认开启 `connect-directly`,初次连接异步读库直接连到记录服,KaLogin 无需改动。
- **登出/注销清除**:`suppressUpdate` + `deleteLastSeen` 保证登出后断线不会重新写回位置。

## 踩过的坑

- **MySQL 不支持 `ON DUPLICATE KEY UPDATE ... WHERE`**(实测报错 1064),须用 `IF()` 逐列守卫。
- **代理写 server 与后端写坐标是两条异步语句**:行可能尚不存在,代理侧 `saveServer` 必须用 upsert。
- 后端 `proxy.server-name` 必须配置且与代理注册名一致,否则记录不可用。
- 配置升级(12→13)会自动补全新键并保留用户值;但**新增的 `server-name` 默认空**,需手动填写。

## 修复记录(2026-09-29)

- **后端配置未实际生效**:排查真实运行配置发现 PVE/PVP/Survival1/Survival2/Resource1/Resource2 的 `proxy.server-name` 都仍为 `lobby`，与此前部署记录不符。故子服退服会原子写入 `server=lobby` 加子服坐标，重登时正确读取了错误服名而落到 Lobby 的同坐标。已按 Velocity `[servers]` 注册名改为 `pve`/`pvp`/`survival1`/`survival2`/`resource1`/`resource2` 并完成对应后端重启；旧记录不批量删除，新一次离线会覆盖该玩家记录。

## 修复记录(2026-09-23)

- **停服不保存坐标**:`onDisable` 直接 `dbManager.close()`,若 `PlayerQuitEvent` 晚于停服回调触发,异步写被已关闭的执行器丢弃。修复:`KaLogin.onDisable` 先调用 `LastSeenManager.reportAllOnline()`(遍历在线玩家写库),再 `shutdown()`,由 `dbManager.close()` 的 `awaitTermination(5s)` 等待落盘。
- **黑名单离开残留错误坐标**:后端 `updateLastSeen` 只写坐标不写 `server`,且不区分黑名单;代理在离开黑名单服时 `delete()` 后,后端稍后的坐标写又会 `INSERT` 重建该行(`server` 仍 NULL)。之后在 lobby 正常退出时,代理 `saveServer("lobby")` 只改 `server` 列,PVE 坐标被保留 → 下次登录“server=lobby + PVE 坐标”落到 lobby 错误位置。修复:①后端 `updateLastSeen` **原子写入 `server` 列**(取 `proxy.server-name`),记录自洽;②新增 `last-seen.blacklist`,黑名单服退服**不写坐标**,代理离开时 `delete()` 后不再被重建;下次登录无记录 → 回默认服出生点。
- **`proxy.server-name` 全服错配**:7 台后端 `KaLogin/config.yml` 的 `proxy.server-name` 均为 `lobby`,导致非 Lobby 服写入错误的 server。此前部署记录误称已修正；实际配置于 2026-09-29 按各自代理注册名完成核正与重启(见 `/home/Server/handoff/plugin-sync.md`)。
- **数据清理**:删除 `kalogin_lastseen` 中 `server IS NULL/''` 的残留行(副本坐标)。

## 当前待办

- [ ] 端到端联调(真实代理 + 两个子服 + 共享 MySQL):跨服免登录、退出后回上次服与坐标、黑名单服退服回默认服。
- [ ] 可选:按 `updated_at` 定期清理长期未更新的行。

## 文档同步(2026-09-20)

- `docs(/docs-en)/features/proxy.md`:新增 `proxy.server-name`;改述为"坐标直接写共享库、代理异步读库直连、server 列由代理写"。
- `docs(/docs-en)/config/config.md`:新增 `proxy.server-name` 行。
- 配置升级 `config-version` 12 → 13(新增 `proxy.server-name`)。
