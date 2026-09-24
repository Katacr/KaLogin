# 功能模块

覆盖:邮箱绑定/找回密码、欢迎条款、事件动作、反作弊、PlaceholderAPI、Geyser 兼容、更新检查。

## 现状

### 邮箱绑定与找回密码

`EmailBindManager.kt`:
- 待验证码 `PendingCode(type,email,code,expireAt)`(`:23-28`),内存态 `pendingCodes: ConcurrentHashMap<UUID, PendingCode>`(`:30`),**不落库**;邮箱正则(`:31`)。
- 验证码:6 位数字,`ThreadLocalRandom` 生成(`:340-342`);过期 `code-expire-seconds`(默认 300s)(`:205,214`)。
- 发送 `sendVerificationCode`(`:193-238`):校验 `email-binding.enabled` 和 `isSmtpConfigured()`(`:194-202`、`:355-360`),异步发送成功后才写入 pending(`:207-214`);`sendMail` 用 Jakarta Mail,10s 超时(`:362-395`);STARTTLS/SSL 由 `resolveSecurityMode` 按端口裁决(`:397-408`,465 走 SSL)。
- 校验:绑定/解绑 `verifyPendingAction`(`:240-278`);找回 `verifyRecoverPassword`(`:280-336`),含格式校验、两次一致、保存(内置 `setPassword`,AuthMe `authMeManager.changePassword`)。
- 入口:`/bindemail` → `openBindDialog`(`:58-130`);`/recoverpassword` → `openRecoverPasswordDialog`(`:132-191`);登录/注册后提示 `showPromptIfNeeded`(`:33-44`);`/bindemail dismiss` → `disablePrompt`(`:46-56`)。
- 命令类内联:`BindEmailCommand`(`:411`)、`RecoverPasswordCommand`(`:448`)。
- DB 字段与方法:`email`、`show_bind_email_prompt`(`DatabaseManager.kt:84-85`),`getPlayerEmail`(`:316`)、`shouldShowBindEmailPrompt`(`:333`)、`bindEmail`/`unbindEmail`(`:453`/`:469`)。

### 欢迎与条款

`WelcomeManager.kt`:
- `showWelcomeIfNeeded`(`:10-26`)按 `welcome-dialog.enabled` 与 `hasAcceptedTerms` 判断;`showWelcomeDialog`(`:28-63`)未勾选提示 `welcome.must-accept`,成功写 `updateAcceptedTerms(uuid,true)` 并执行回调。
- 状态字段 `accepted_terms`(`DatabaseManager.kt:372/389`);触发点:内置 `LoginListener.kt:89/212/388`,AuthMe `AuthMeLoginListener.kt:105/130/360`。
- 重置:内置 `KaLoginCommand.handleResetTerms`(`:119-150`),AuthMe `AuthMeManager.kt:303-340`;DB `resetAcceptedTerms(ForAll)`(`DatabaseManager.kt:405-421`)。

### 事件动作执行器

`EventActionExecutor.kt`:
- `execute(player, eventType)` 读取 `events.<eventType>`(当前仅 `login`/`register`,`config.yml:190-200`),顺序执行(`:39-143`)。
- 动作类型:`wait <tick>`(`:65`)、`command`(玩家命令,`:78`)、`console`(控制台命令,`:95`)、`tell`(消息,`:117`)、`toast`(仅支持平台,`:128`);其他类型告警跳过(`:138`)。
- 占位符 `resolvePlaceholders`(`:145-155`):替换 `{player_name}` 与 `%player_name%`,再走 PAPI。
- Toast `parseAndSendToast`(`:157-216`):`key=value` 分号分隔,支持 `type`/`icon`/`msg|title`/`description|desc`,动态 advancement 授予后 10 tick 撤销(`:181-212`)。

### 反作弊

`AntiCheatManager.kt`:
- 进入认证保存游戏模式、锁定位置/朝向、禁飞、清零速度、加黑暗+缓慢(`:55-91`);退出恢复(`:96-117`)。
- 拦截(均 `EventPriority.HIGHEST`):移动 `PlayerMoveEvent`(`:213`)、聊天 `AsyncPlayerChatEvent`(`:234`)、命令 `PlayerCommandPreprocessEvent`(白名单 `/kalogin,/kl,/recoverpassword,/rp,/bindemail`,`:246-263`)、玩家/实体交互(`:265-291`)、背包点击/拖拽/关闭(`:292-320`)、物品切换/副手/丢弃/拾取(`:322-353`)、飞行/潜行/疾跑切换(`:354-377`)、传送门/传送(`:378-397`)、桶/进食/床/钓鱼/放置/破坏(`:398-453`)、攻击与被攻击(`:454-469`)。
- 退出清理与 `clearAll`(`:472-509`);对话框重开防抖 1500ms(`:181-198`);`programmaticClosePlayers` 避免主动关闭被误判(`:161-164,314-317`)。

### PlaceholderAPI

`KaLoginPlaceholderExpansion.kt`:identifier `kalogin`,`persist=true`(`:8,14`);提供 `%kalogin_email_masked%`、`%kalogin_email_plain%`、`%kalogin_accepted_terms%`、`%kalogin_last_login_ip%`、`%kalogin_auto_login_by_ip%`、`%kalogin_register_time%`(`:18-32`,数据来自 `getPlayerInfoSnapshot`)。

### Geyser 兼容

`GeyserCompat.kt`:纯反射调用 `GeyserApi.api()` 与 `connectionByUuid`(`:20-34`),`isBedrockPlayer`(`:39-47`);无 Geyser 时静默降级。用途:Paper 平台对基岩玩家放开认证弹窗可关闭性(`PaperLoginDialogPlatform.kt:321-333`)。

### 更新检查

`UpdateChecker.kt`:
- 数据源为 GitHub raw 的 `plugin.yml`(`:16-17`),MineBBS/SpigotMC 仅作点击链接(`:18-19`)。
- `check` 异步 GET(5s 超时),正则 `version:\s*'([^']+)'` 提取(`:37-68`);`refresh`(`:75-85`);`notifyIfUpdateAvailable` 仅对 OP 提示(`:91-111`);`isNewer` 按 `x.y.z` 逐段比较(`:117-127`)。
- 触发:`KaLogin.kt:182-184` 启动检查,`UpdateNotifyListener` OP 加入提示(`KaLogin.kt:187,353-358`)。

## 关键决策与原因

- **验证码不落库**:降低持久化复杂度;代价是重启丢失、无频率限制。
- **动作列表顺序执行 + `wait` 分段**:用 `wait` 在玩家线程上分段延迟,兼容 Folia。
- **反作弊用白名单前缀放行认证命令**:避免玩家在登录前使用游戏命令。
- **更新检查以仓库 `plugin.yml` 为唯一版本源**:无需额外发布产物。

## 踩过的坑

1. **验证码内存态**:重启丢失;无发送频率限制/冷却;单玩家仅一个 pending,绑定与找回会互相覆盖;用 `ThreadLocalRandom` 而非 `SecureRandom`。
2. **邮箱查询异常被吞**:`getPlayerEmail` 异常返回 null(`DatabaseManager.kt:326-329`),会被当作"未绑定邮箱"。
3. **欢迎回调泄漏**:`WelcomeManager.pendingCallbacks`(`:8`)未在玩家退出时清理,玩家中途退出后回调不执行。
4. **反作弊命令白名单与注释不符**:注释称允许 `/login`、`/register`,白名单并无;`startsWith` 前缀匹配可被 `/bindemailxxx` 之类绕过。
5. **使用已废弃事件**:`AsyncPlayerChatEvent`(Paper 已废弃);toast 依赖 `Bukkit.getUnsafe().loadAdvancement`。
6. **反作弊未覆盖铁砧/合成/编辑书/盔甲架等交互**;`PlayerTeleportEvent` 放行 PLUGIN/UNKNOWN 原因,其他插件可借此移动认证中玩家。
7. **AuthMe 模式 reload 不刷新更新检查**:`AuthMeManager.handleReloadCommand`(`:289-301`)未调用 `UpdateChecker.refresh`,与内置模式 `KaLoginCommand.kt:112` 不一致。
8. **`isNewer` 解析失败即不提示**:非纯数字版本号(如 `1.5.1-beta`)不提示。

## 当前待办

- [ ] 邮箱验证码落库或至少加冷却/频率限制,并考虑 `SecureRandom`。
- [ ] 区分"未绑定邮箱"与"查询失败"。
- [ ] 玩家退出时清理 `WelcomeManager.pendingCallbacks`。
- [ ] 修正反作弊命令白名单(移除错误注释,改为精确匹配或补充 `/login`、`/register`)。
- [ ] 迁移到 `AsyncChatEvent`,评估 toast 替代实现。
- [ ] AuthMe 模式 reload 补 `UpdateChecker.refresh`。
