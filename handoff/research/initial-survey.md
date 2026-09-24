# KaLogin 项目初步调研报告

- 调研日期:2026-09-19
- 调研对象:`/home/Plugins/KaLogin`(Paper 1.21.8 API、Kotlin、Folia 兼容,版本 1.5.1)
- 调研方式:并行子任务精读源码 + 全 `src/` 交叉检索 + 对照 `plugin.yml` / `config.yml` / `docs`,并辅以字节级校验消除字体歧义。

## 一、调研目的

1. 建立项目整体认知:定位、技术栈、构建、目录与模块划分。
2. 弄清两种认证模式的完整实现与数据流。
3. 梳理 UI/Dialog 子系统、功能模块、命令与开放 API。
4. 排查文档与实现不一致处,为修复与后续交接提供依据。

## 二、范围与方法

- 范围:主源码 `src/main/kotlin/org/katacr/kalogin/**`、Spigot 适配 `src/spigot/kotlin/**`、资源 `src/main/resources/**`、用户文档 `docs/**`、`docs-en/**`、`README.md`、`CHANGELOG.md`、构建脚本。
- 说明:根目录 `API/**` 为反编译参考源码(AuthMe、Adventure MiniMessage、Paper/Bukkit、nashorn、asm),**不参与构建**,未纳入实现分析。
- 方法:按"认证 / UI-Dialog / 功能与命令"三块并行调研;文档逐页对照源码事实;可疑字符串用 Python 读取原始字节确认。

## 三、关键发现

### 3.1 架构与构建

- 入口 `KaLogin.kt:16`,`onLoad` 用 Libby 运行时下载依赖(`KaLogin.kt:39-102`),`onEnable` 装配各管理器(`KaLogin.kt:104-190`)。
- 双 source set:`spigotAdapter`(`build.gradle.kts:37-44`)产物合并进主 JAR(`build.gradle.kts:92-93`)。
- 平台能力由反射选择:`dialog/LoginDialogPlatformLoader.kt:9-16`。

### 3.2 认证

- 双模式选择 `AuthMeManager.kt:23-37`;内置模式入口 `LoginListener.kt:28`;AuthMe 模式入口 `AuthMeLoginListener.kt:54` 与其 `LoginEvent` 收尾(`:92`)。
- 登录态:`LoginListener.loggedInPlayers`(`LoginListener.kt:21`) vs `AuthMeApi.isAuthenticated`(`AuthMeManager.kt:53-59`)。
- 数据库表 `kalogin_users`(`DatabaseManager.kt:76-100`),单连接单线程(`:27-36`)。
- 密码 jBCrypt cost=5(`PasswordHasher.kt:10-24`);强度校验 `PasswordValidator.kt:19-84`。
- 线程经 `KaLoginScheduler`/`FoliaSchedulerAdapter` 兼容 Folia。

### 3.3 UI/Dialog

- 抽象 `dialog/LoginDialogPlatform.kt`;Paper 实现 `dialog/paper/PaperLoginDialogPlatform.kt`;Spigot 实现 `src/spigot/.../SpigotLoginDialogPlatform.kt`。
- Body 渲染 `LoginUI.kt:132-184`;消息 `MessageManager.kt`;配置升级 `ConfigUpdater.kt`(版本 10)。
- 错误提示 none/body/toast 分发在 `KaLogin.kt:298-341`。

### 3.4 功能/命令/API

- 邮箱 `EmailBindManager.kt`;欢迎 `WelcomeManager.kt`;动作 `EventActionExecutor.kt`;反作弊 `AntiCheatManager.kt`;PAPI `KaLoginPlaceholderExpansion.kt`;Geyser `GeyserCompat.kt`;更新 `UpdateChecker.kt`。
- 命令与权限 `plugin.yml:19-71`、注册 `KaLogin.kt:211-254`。
- 开放 API `listener/KaLoginAPI.kt`、`KaLoginListener.kt`、`KaLoginEvents.kt`(事件为 data class,非 Bukkit Event)。

### 3.5 文档与实现不一致

- `README.md` 称事件为 Bukkit Event、`@EventHandler` 可用 —— 错误(事件是 data class)。
- `README.md` 的 UI item 字段 `decorations`/`tooltip` —— 实际为 `show_overlays`/`show_tooltip`。
- `docs` 称 `/kl reload` 重载界面 —— 实际只重载 config/lang。
- 命令名 `resetterms` 经字节级校验**确实一致**,此前疑似不一致系字体渲染歧义。
- 其余版本号陈旧、平台描述过窄等。

## 四、结论与建议

1. **整体架构清晰**,平台抽象、异步 DB、Folia 兼容是主要优点;主要风险集中在 AuthMe 模式的行为落差与若干使用已废弃 API 的实现。
2. **优先修复 AuthMe 模式的功能缺口**:IP 限制键名错误(`AuthMeLoginListener.kt:458`)、缺少超时兜底、自动登录勾选无效。
3. **安全相关**:BCrypt cost=5 偏低;验证码用 `ThreadLocalRandom` 且内存态无频率限制;玩家命令权限未强制。
4. **兼容性风险**:`AsyncPlayerChatEvent` 与 `Bukkit.getUnsafe().loadAdvancement` 属于废弃/内部 API,建议迁移。
5. **文档**:README 已修正明显的机制性错误;建议补开放 API 文档、1.5.x CHANGELOG、英文 `config_update` 语言键。
6. 详细交接见 `handoff/overview.md`、`auth.md`、`ui-dialog.md`、`features.md`、`commands-api.md`、`docs.md`。
