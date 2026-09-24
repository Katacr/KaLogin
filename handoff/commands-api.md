# 命令与开放 API

覆盖:全部命令/别名/子命令/权限、开放 API(KaLoginAPI、KaLoginListener、事件数据类)。

## 命令一览

命令定义 `plugin.yml:19-51`,权限 `plugin.yml:53-71`,注册逻辑 `KaLogin.kt:211-254`。

| 命令 | 别名 | 子命令 | 声明权限 | 执行类(file:line) | Tab 补全 |
|---|---|---|---|---|---|
| `/kalogin` | `/kl` | `delete <玩家>`、`register <玩家> <密码>`、`resetterms <玩家\|all\|*>`、`reload` | `kalogin.admin`(op) | 内置 `KaLoginCommand.kt:9`;AuthMe `AuthMeCommandExecutor`(`AuthMeManager.kt:198`) | 内置 `KaLoginCommand.kt:152-175`;AuthMe `AuthMeManager.kt:342-374` |
| `/changepassword` | `/cp` | 无 | `kalogin.changepassword` | `ChangePasswordCommand.kt:13` | 空 |
| `/logout` | 无 | 无 | `kalogin.logout` | `LogoutCommand.kt:10` | 空 |
| `/bindemail` | 无 | `dismiss` | `kalogin.bindemail` | `EmailBindManager.kt:411` | `dismiss`(有权限时,`:432-445`) |
| `/recoverpassword` | `/rp` | 无 | `kalogin.recoverpassword` | `EmailBindManager.kt:448` | 空 |
| `/usercenter` | `/uc` | 无 | `kalogin.usercenter` | `UserCenterCommand.kt:10` | 空 |

> 注意:实际子命令为 **`resetterms`**(`KaLoginCommand.kt:32`、`AuthMeManager.kt:235`),文档曾误写为 `resetterms`,已修复(见 `docs.md`)。

行为要点:
- `delete`:查 `getOfflinePlayer`,未游玩过视为不存在(`KaLoginCommand.kt:53-56`);删库后在线则踢出(`:63-66`);AuthMe 模式用 `forceUnregister`(`AuthMeManager.kt:244-262`)。
- `register`:内置走 `PasswordValidator` + `setPassword`(`KaLoginCommand.kt:77-103`);AuthMe 走 `registerPlayer`(`AuthMeManager.kt:264-287`)。
- `reload`:内置重载 config/lang、`authMeManager.init()`、`UpdateChecker.refresh`(`KaLoginCommand.kt:108-114`);AuthMe 未刷新更新检查。
- `logout`:触发登出事件 → 关同 IP 自动登录 → AuthMe 下 `forceLogout` → 踢出(`LogoutCommand.kt:32-45`)。
- `changepassword`:需已登录,旧密码校验 + 新密码格式/异同/一致校验,失败上限 `change-password.max-attempts`(`ChangePasswordCommand.kt:62-151`)。
- `usercenter`:需已登录,展示账号信息并提供绑定邮箱/改密入口(`UserCenterCommand.kt:37-72`)。
- `recoverpassword`:仅未登录玩家可用(`EmailBindManager.kt:460-468`)。

## 权限现状(重要)

- `plugin.yml:53-71` 声明了 `kalogin.admin/changepassword/logout/bindemail/recoverpassword/usercenter`。
- **但仅 admin 命令与 `/bindemail` 的 Tab 补全做了 `hasPermission` 校验**;`changepassword/logout/bindemail/recoverpassword/usercenter` 的 `onCommand` 均未校验。
- 结论:玩家命令权限目前**形同虚设**,`docs/perm/permissions.md` 描述的是设计预期而非当前行为(已在 handoff 记录,未改文档以免与实际不符)。

## 开放 API

包 `org.katacr.kalogin.listener`,集成在主插件内,未拆独立 Maven 模块;`docs/` 无 API 文档。

### KaLoginAPI(`listener/KaLoginAPI.kt:34`,单例)

- `getInstance()`(`:46-52`):实现为惰性 new,**永不返回 null**(与注释"未启用返回 null"不符)。
- `setEnabled`(`:58`)、`isEnabled`(`:66`)、`registerListener`(`:73`)、`unregisterListener`(`:85`)、`getListenerCount`(`:95`)。
- `isPlayerLoggedIn(playerName)`(`:102`)/`isPlayerLoggedIn(player)`(`:112`):按认证模式分支查询。
- 触发方法:`callPlayerLoginSuccess`(`:130`)、`callPlayerLoginFailed`(`:141`)、`callPlayerAutoLogin`(`:152`)、`callPlayerProxyRestore`(跨服会话恢复,新增)、`callPlayerRegisterSuccess`(`:163`)、`callPlayerRegisterFailed`(`:174`)、`callPlayerChangePasswordSuccess`(`:184`)、`callPlayerChangePasswordFailed`(`:195`)、`callPlayerLogout`(`:205`)、`callPlayerUnregister`(`:244`)、`callPlayerAdminUnregister`(`:254`)。
- 便捷方法 `logout(player, kickMessage?)`(`:218-238`):触发登出 + 关自动登录 + AuthMe 登出 + 踢出。

### KaLoginListener 接口(`listener/KaLoginListener.kt:28`)

10 个带默认空实现的方法:`onPlayerLoginSuccess/Failed`、`onPlayerAutoLogin`、`onPlayerProxyRestore`(跨服会话恢复)、`onPlayerRegisterSuccess/Failed`、`onPlayerChangePasswordSuccess/Failed`、`onPlayerLogout`、`onPlayerUnregister`、`onPlayerAdminUnregister`(`:34-88`)。

### 事件数据类(`listener/KaLoginEvents.kt`)

**均为 Kotlin `data class`,不是 Bukkit `Event` 子类**,仅通过上述接口回调传递:

| 类 | 字段 | 行 |
|---|---|---|
| `PlayerLoginSuccessEvent` | `player, ip, isAutoLogin=false` | `:9` |
| `PlayerLoginFailedEvent` | `player, remainingAttempts` | `:19` |
| `PlayerAutoLoginEvent` | `player, ip` | `:28` |
| `PlayerProxyRestoreEvent` | `player, ip`(跨服会话恢复) | 新增 |
| `PlayerRegisterSuccessEvent` | `player, ip` | `:37` |
| `PlayerRegisterFailedEvent` | `player, reason` | `:46` |
| `PlayerChangePasswordSuccessEvent` | `player` | `:55` |
| `PlayerChangePasswordFailedEvent` | `player, reason` | `:63` |
| `PlayerLogoutEvent` | `player` | `:72` |
| `PlayerUnregisterEvent` | `player` | `:80` |
| `PlayerAdminUnregisterEvent` | `playerName` | `:88` |

触发来源:内置 `LoginListener.kt:92,216,225,231,392,397`、`ChangePasswordCommand.kt:104,112,115,141,155`、`LogoutCommand.kt:33`;AuthMe `AuthMeLoginListener.kt:110,136,194,211,226,237,303,308,317,367,450`。

> `PlayerUnregisterEvent`/`PlayerAdminUnregisterEvent` 仅在 AuthMe 模式触发;**内置模式无注销账户命令**,不会触发。

## 关键决策与原因

- **接口回调而非 Bukkit Event**:避免事件类污染 Bukkit 事件总线,保持 API 轻量,也无需在主插件注册监听器。
- **`logout` 便捷方法内聚登出副作用**:统一触发事件、关闭自动登录、AuthMe 登出和踢出。

## 踩过的坑

1. **README 旧版 API 文档错误**:曾宣称"使用标准 Bukkit Event API + `@EventHandler`",但事件类是普通 data class,`@EventHandler` 无法触发。已修正 README(见 `docs.md`)。
2. **`getInstance()` 永不返回 null**,README 的 null 检查示例无效;应改用 `isEnabled()` 或检查插件是否存在。
3. **玩家命令权限未强制**(见上)。
4. **API 无用户文档**:`docs/`、`docs-en/` 未覆盖,README 是唯一来源。

## 当前待办

- [ ] 在玩家命令的 `onCommand` 中补 `hasPermission` 校验,使声明权限与行为一致。
- [ ] 修正 `KaLoginAPI.getInstance()` 的注释语义,或明确文档说明其永不返回 null。
- [ ] 将开放 API 文档补入 `docs/` 与 `docs-en/`(在 `SUMMARY.md` 登记),与 README 保持一致。
- [ ] 考虑为内置模式补充注销账户能力,或文档说明该差异。
