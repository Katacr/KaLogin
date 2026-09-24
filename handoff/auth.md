# 认证核心

覆盖:双认证模式、登录/注册/自动登录流程、数据库、密码、超时与限制、线程模型。

## 现状

### 模式选择

- `AuthMeManager.init()` 读取 `use-AuthMe`(默认 `true`,`config.yml:10`);仅当 AuthMe 存在且启用时 `useAuthMe=true`,否则回退内置模式并置 false(`AuthMeManager.kt:23-37`)。
- `onEnable` 中两种模式都注册 `AntiCheatManager`;`LoginListener` 始终构造,但仅在非 AuthMe 模式注册事件(`KaLogin.kt:168-176`)。
- 命令执行器:AuthMe 模式用 `AuthMeCommandExecutor`(`AuthMeManager.kt:198`),否则 `KaLoginCommand`(`KaLoginCommand.kt:9`)。
- 对外登录态统一由 `KaLoginAPI.isPlayerLoggedIn` 按模式分支(`KaLoginAPI.kt:112-122`)。

### KaLogin 内置模式流程

- 入口 `LoginListener.onJoin` @ `PlayerJoinEvent`(`LoginListener.kt:28`):
  1. 清空失败次数,立即 `startAuthenticating` + `loading` 弹窗(`:42-47`)。
  2. 启动加载兜底超时 `login.loading-timeout`(默认 15s),超时踢出(`:50-58`)。
  3. 异步 `isPlayerRegistered`(`:61`):
     - 返回 `null`(查询失败)→ 踢出,避免误弹注册框(`:65-76`)。
     - 已注册 → 异步 `canAutoLogin`(`:80`):命中同 IP 自动登录则免密登录(`:82-93`),否则 `showLoginDialogDelayed`(`:99`)。
     - 未注册 → 校验 `login.max-accounts-per-ip` + `countAccountsByIp`,超限踢出,否则注册框(`:104-138`)。
- 登录对话框 `showLoginDialog`(`:146`):启动 `login.login-timeout`(`:162-170`)、校验最大尝试次数(`:173-180`);`loginAction` 异步 `verifyPassword`(`:192`);成功写 `last_login_ip`/`auto_login_by_ip`、欢迎、`endAuthenticating`、执行 `events.login`、邮箱提示、`callPlayerLoginSuccess(...,false)`(`:194-217`);失败递增 `loginAttempts`(`:218-232`)。
- 注册对话框 `showRegisterDialog`(`:316`):启动 `login.register-timeout`(`:332-340`);校验空密码 / `PasswordValidator.validate` / 两次一致(`:346-362`);异步 `registerPlayer`(`:373`);成功同样走欢迎与 `events.register`(`:380-393`)。
- 延迟弹窗由 `login.dialog-delay-ticks` 控制(`showLoginDialogDelayed` `:282-293`、`showRegisterDialogDelayed` `:299-310`)。

### AuthMe 模式流程

- 入口 `AuthMeLoginListener.onPlayerJoin` @ `PlayerJoinEvent`(`AuthMeLoginListener.kt:54`):重置失败次数、`processingPlayers` 去重、`AuthMeApi.isRegistered`、`initPlayerInDatabase`,已注册弹登录框,否则注册框(`:60-89`)。
- **无 loading 弹窗、无登录/注册超时任务**(与内置模式最大差异)。
- 登录 `showLoginDialog`(`:245`):2 秒防抖(`:251-257`)、最大尝试次数(`:259-265`);`loginAction` 同步 `checkPassword`(`:280`)→ `forceLogin`(`:282`)、更新 IP / 自动登录字段(`:287-290`)。
- 登录成功收尾由 AuthMe `LoginEvent` 驱动 `onAuthMeLogin`(`:92`):
  - Session 自动登录(`:99-120`)→ `callPlayerAutoLogin`;
  - 待注册转登录 `completePendingRegisterLogin`(`:122-124`);
  - 手动登录(`:126-143`)→ 欢迎 + `endAuthenticating` + `events.login` + 邮箱提示 + `callPlayerLoginSuccess`。
- 注册 `showRegisterDialog`(`:389`):校验后 `forceRegister`(`:432-434`),AuthMe 发 `RegisterEvent` → `onAuthMeRegister` 确认后 `forceLogin`(`:150-169`),再触发 `LoginEvent` 收尾。
- 其它事件映射:`RestoreSessionEvent`(`:173`)、`LogoutEvent`(`:184`)、`UnregisterByPlayerEvent`(`:201`)、`UnregisterByAdminEvent`(`:218`)。

### 登录状态

- 内置:`LoginListener.loggedInPlayers: ConcurrentHashMap<UUID, Boolean>`(`LoginListener.kt:21`),`isLoggedIn(uuid)`(`:427-429`);退出/清理时移除(`:419-422`、`AntiCheatManager.kt:490`)。
- AuthMe:本地不维护登录表,委托 `AuthMeApi.isAuthenticated(player)`(`AuthMeManager.kt:53-59`)。
- 独立的认证中状态:`AntiCheatManager.authenticatingPlayers: ConcurrentHashMap<UUID, Player>`(`AntiCheatManager.kt:26`),`startAuthenticating`(`:55-91`)、`endAuthenticating`(`:96-117`)、`isAuthenticating`(`:122-131`)。

### 群组跨服会话

- 启用 `proxy.enabled` 后,登录/注册/自动登录成功会上报 KaProxy;切服进服时先查询代理恢复登录态,详见 `proxy.md`。
- 本地 `loggedInPlayers`/AuthMe 认证态仍按服维护,代理会话是跨服权威;真退服由代理销毁会话,切服保留。
- AuthMe 模式恢复时调用 `forceLogin` 并用 `proxyRestoredPlayers` 短路 `LoginEvent`。

### 数据库

- 单 `java.sql.Connection` + 单线程守护执行器 `KaLogin-Database`,所有操作经 `supplyDb` 返回 `CompletableFuture`(`DatabaseManager.kt:27-36`)。`getConnection` 失效时重连并可能重调 `init`(`:131-144`)。
- 表 `kalogin_users`(`:76-100`):`uuid` PK、`username`、`password`、`ip`、`last_login_ip`、`auto_login_by_ip`(默认 FALSE)、`email`、`show_bind_email_prompt`(默认 TRUE)、`accepted_terms`(默认 FALSE)、`reg_date`。
- 迁移:`addColumnIfNotExists` 补 5 列(`:93-99`);SQLite 用 `pragma_table_info` 判断,其余靠捕获异常后 `ALTER TABLE`(`:108-128`)。
- 关键方法:`registerPlayer`(`:146`)、`isPlayerRegistered`(`:174`,返回 `Boolean?`)、`initPlayerForAuthMe`(`:196`)、`canAutoLogin`(`:228`)、`updateAutoLoginByIp`(`:279`)、`updateLastLoginIp`(`:299`)、`getPlayerEmail`(`:316`)、`hasAcceptedTerms`(`:372`)、`updateAcceptedTerms`(`:389`)、`resetAcceptedTerms(ForAll)`(`:405`)、`getPlayerInfoSnapshot`(`:423`)、`bindEmail`/`unbindEmail`(`:453`/`:469`)、`verifyPassword`(`:487`)、`countAccountsByIp`(`:512`)、`deletePlayer`(`:536`)、`setPassword`(`:554`)、`close`(`:571`)。

### 密码

- 哈希:`PasswordHasher` 使用 jBCrypt,`gensalt(5)` 固定 cost=5(`PasswordHasher.kt:10-24`),注册 `DatabaseManager.kt:148`、改密 `:556` 调用。
- 强度:`PasswordValidator.validate`(`PasswordValidator.kt:19-84`)依次校验长度、黑名单、正则、大小写/数字/符号开关、连续相同数字上限;配置见 `config.yml:35-57`。调用点:`LoginListener.kt:352`、`AuthMeLoginListener.kt:414`、`ChangePasswordCommand.kt:74`。

### 超时与限制

- 登录/注册/加载超时任务**仅内置模式存在**(`LoginListener.kt:50-58`、`:162-170`、`:332-340`)。
- 最大登录尝试:内置 `LoginListener.kt:173-232`;AuthMe `AuthMeLoginListener.kt:259-309`。
- IP 注册限制:内置登录前主动校验(`LoginListener.kt:105-125`);AuthMe 模式未主动校验(见待办)。
- 改密尝试上限:`ChangePasswordCommand.kt:133-151`。

### 同 IP 自动登录

- 存储于 `last_login_ip` + `auto_login_by_ip`(`DatabaseManager.kt:230/281`)。
- 内置模式通过 `canAutoLogin` 命中后免密登录(`LoginListener.kt:80-93`)。
- AuthMe 模式的勾选值仍写入数据库(`AuthMeLoginListener.kt:290`),但 `canAutoLogin` **从不被调用**,实际自动登录由 AuthMe session 机制处理(`RestoreSessionEvent` + `onAuthMeLogin` session 分支)。

### 线程模型与 Folia

- 数据库与 BCrypt 全在 `KaLogin-Database` 线程;回调经 `KaLoginScheduler.runPlayer/runPlayerLater` 切回玩家线程(如 `LoginListener.kt:67,81,193,380`)。
- `KaLoginScheduler` 反射检测 Folia(`KaLoginScheduler.kt:17-28`),统一 `runPlayer/runPlayerLater/runGlobal/runGlobalLater/runAsync/teleport`,并用 `KaLoginTaskHandle` 屏蔽任务类型差异(`:40-66`、`:97-105`)。
- Folia 适配 `FoliaSchedulerAdapter.kt`:玩家任务用 `EntityScheduler`,全局用 `GlobalRegionScheduler`,异步用 `AsyncScheduler`,传送用 `teleportAsync`(`:16-40`)。
- AuthMe 回调经 `AuthMeLoginListener.runOnPlayerThread` 切线程(`:42-52`)。

## 关键决策与原因

- **认证与会话解耦**:`authenticatingPlayers` 独立于登录态,便于反作弊拦截未登录玩家。
- **内置模式对 DB 查询失败零容忍**:`isPlayerRegistered==null` 时直接踢出,避免把查询失败误判为"未注册"而弹注册框。
- **AuthMe 模式把登录判定完全交给 AuthMe**,KaLogin 只做 UI 与扩展数据,降低与 AuthMe 的耦合风险。

## 踩过的坑

1. **AuthMe 模式缺少全部 KaLogin 超时**:`login-timeout`/`register-timeout`/`loading-timeout` 在 AuthMe 模式下不生效,只能依赖 AuthMe 自身超时。
2. **AuthMe 模式 IP 限制未实现**:`AuthMeLoginListener.kt:458` 读取的键 `ip-limit.max-accounts` 在 `config.yml` 中不存在(应为 `login.max-accounts-per-ip`),且未在加入时主动校验。
3. **AuthMe 模式自动登录开关可能误导玩家**:勾选框存在但底层由 AuthMe session 决定。
4. **默认值不一致**:`login-timeout` 代码默认 60(`LoginListener.kt:162`),`config.yml:64` 为 90。
5. **BCrypt cost=5 偏低且硬编码**,注释明确改动后旧密码无法验证(`PasswordHasher.kt:11-12`)。
6. **单连接单线程数据库**:MySQL 断连/慢查询会拖慢所有 DB future;内置模式有 loading 超时兜底,AuthMe 模式无。
7. **AuthMe 加入早期防护空窗**:显示对话框前未 `startAuthenticating`(`AuthMeLoginListener.kt:55-90`),`dialog-delay-ticks` 越大空窗越久。
8. **AuthMe 注册强依赖 `forceLogin` 触发 `LoginEvent`**:若版本不触发,`pendingRegisterPlayers` 可能滞留。
9. **`forceLogin` 与自动登录字段更新分离**:`AuthMeManager.forceLogin` 只更新 `last_login_ip`(`:75-82`),`auto_login_by_ip` 需调用点单独更新,漏一处即不一致。
10. **`addColumnIfNotExists` 迁移兼容性**:非 SQLite 依赖异常判断,`reg_date` 等既有字段未纳入补充逻辑。

## 当前待办

- [ ] 修正 AuthMe 模式 IP 限制键名并对齐内置模式的加入校验。
- [ ] 决定 AuthMe 模式是否补 KaLogin 超时兜底,或从文档明确该差异。
- [ ] 统一 `login-timeout` 代码默认值与配置值(60 vs 90)。
- [ ] 评估 BCrypt cost 提升与旧密码兼容迁移方案。
- [ ] 评估数据库连接池化(至少处理 MySQL 断连重连的递归 `init`)。
- [ ] AuthMe 模式取消/隐藏无实际作用的同 IP 自动登录勾选,或在文档注明。
- [ ] 群组跨服功能的后端到端联调(真实代理 + 两个子服 + 共享 MySQL),详见 `proxy.md`。
