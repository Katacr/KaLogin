# 项目概览

## 现状

- **定位**:面向 Paper / Folia / Spigot 的 Minecraft 登录注册与账号安全插件,使用原生 Dialog 提供登录、注册、改密、邮箱绑定、密码找回、欢迎条款等交互。
- **版本**:`1.5.1`(`build.gradle.kts:9`、`src/main/resources/plugin.yml:2`)。
- **平台支持**:编译目标 `paper-api:1.21.8`(`build.gradle.kts:47`);`plugin.yml:4` `api-version: '1.21'`,`plugin.yml:5` `folia-supported: true`;Spigot 适配编译于 `spigot-api:1.21.6`(`build.gradle.kts:63`)。README 旧描述为 "Paper 1.21.7+",实际运行要求见 `docs/home/start.md`。
- **软依赖**:PlaceholderAPI、AuthMe、Geyser-Spigot(`plugin.yml:14-17`)。

## 技术栈与构建

- Kotlin `2.3.20` + Gradle,JDK 21(`build.gradle.kts:2`、`:78-81`)。
- Shadow 打包;`build` 任务依赖 `shadowJar`(`build.gradle.kts:84-95`)。
- **Spigot 适配 source set**:`spigotAdapter`(`build.gradle.kts:37-44`),源码在 `src/spigot/kotlin`,其编译产物合并进主 JAR(`build.gradle.kts:92-93`)。
- **运行时依赖下载(Libby)**:`onLoad` 中通过 `BukkitLibraryManager` 下载 kotlin-stdlib、jbcrypt、sqlite-jdbc、jakarta.activation、jakarta.mail、adventure-text-serializer-bungeecord,库目录为服务器根目录 `libraries/`(`KaLogin.kt:39-102`)。`plugin.yml:7-12` 另有 `libraries` 声明。
- 邮箱、AuthMe、PAPI、adventure 等为 `compileOnly`;sqlite/jbcrypt/mail 运行时由 Libby 提供(`build.gradle.kts:46-67`)。
- 仓库使用阿里云镜像加速(`build.gradle.kts:12-17`、`settings.gradle.kts:2-8`)。

## 目录结构

```
KaLogin/
├── build.gradle.kts / settings.gradle.kts
├── README.md / CHANGELOG.md
├── docs/            # 中文用户文档
├── docs-en/         # 英文用户文档(与 docs 结构一致)
├── API/             # 反编译的参考 API 源码(AuthMe / Adventure MiniMessage / Paper / nashorn / asm),不参与构建
├── handoff/         # 本交接目录
└── src/
    ├── main/kotlin/org/katacr/kalogin/   # 主源码
    │   ├── KaLogin.kt                    # 插件入口
    │   ├── LoginListener.kt              # 内置模式认证
    │   ├── AuthMeManager.kt              # AuthMe 集成 + AuthMeCommandExecutor
    │   ├── AuthMeLoginListener.kt        # AuthMe 模式认证
    │   ├── DatabaseManager.kt            # 数据库
    │   ├── listener/                     # 开放 API(KaLoginAPI/KaLoginListener/事件数据类)
    │   ├── dialog/                       # Dialog 平台抽象与 Paper 实现
    │   └── ...                           # 功能管理器
    ├── spigot/kotlin/.../dialog/spigot/  # Spigot Dialog 实现
    └── main/resources/                   # plugin.yml / config.yml / lang / ui / ui-en
```

## 启动流程(`KaLogin.kt`)

- `onLoad`(`:39`):创建并下载 Libby 依赖。
- `onEnable`(`:104`):
  1. `KaLoginScheduler.init`(`:105`)。
  2. `MessageManager` 初始化并注入 `ConfigUpdater`(`:108-110`)。
  3. 配置版本检查/升级,必要时 `reloadConfig`(`:113-125`)。
  4. 释放并加载 `ui/*.yml`,`LoginUI.init`,加载 Dialog 平台(`:128-133`)。
  5. `GeyserCompat.init`(`:136`)。
  6. 依次初始化 `AntiCheatManager`、`EventActionExecutor`、`EmailBindManager`、`AuthMeManager`、`DatabaseManager`(始终初始化)、`WelcomeManager`(`:139-156`)。
  7. 注册 PAPI 扩展(`:158-161`)。
  8. 启用 `KaLoginAPI`(`:164-165`)。
  9. 注册监听器:反作弊始终注册;按 `useAuthMe` 注册 `LoginListener` 或 `AuthMeLoginListener`(`:168-176`)。
  10. 注册命令(`:179`)、检查更新、注册 OP 更新提示监听器(`:182-187`)。
- `onDisable`(`:256`):关闭 Dialog 平台、取消任务、关数据库、清反作弊、注销 PAPI、关闭 API。

## 模块地图

| 模块 | 主要文件 | 交接文件 |
|---|---|---|
| 入口/装配 | `KaLogin.kt` | 本文件 |
| 认证 | `LoginListener.kt`、`AuthMeManager.kt`、`AuthMeLoginListener.kt`、`DatabaseManager.kt`、`PasswordValidator.kt`、`PasswordHasher.kt` | `auth.md` |
| UI/Dialog | `LoginUI.kt`、`dialog/**`、`MessageManager.kt`、`ConfigUpdater.kt`、`ui/**` | `ui-dialog.md` |
| 功能 | `EmailBindManager.kt`、`WelcomeManager.kt`、`EventActionExecutor.kt`、`AntiCheatManager.kt`、`KaLoginPlaceholderExpansion.kt`、`GeyserCompat.kt`、`UpdateChecker.kt` | `features.md` |
| 群组跨服 | `proxy/ProxyProtocol.kt`、`proxy/ProxySessionManager.kt`、`proxy/LastSeenManager.kt` | `proxy.md`、`lastseen.md` |
| 命令/API | `KaLoginCommand.kt`、`ChangePasswordCommand.kt`、`LogoutCommand.kt`、`UserCenterCommand.kt`、`listener/**` | `commands-api.md` |
| 文档 | `docs/**`、`docs-en/**`、`README.md` | `docs.md` |

## 关键决策与原因

- **原生 Dialog 而非聊天/背包 UI**:Paper 1.21.7+ 提供 Dialog API,体验更现代;通过抽象层 `LoginDialogPlatform` 隔离平台差异。
- **双 source set**:Spigot 无 Paper 原生 Dialog,需用 Bungee Dialog + 公共 API 打补丁,单独 source set 避免主包引用 Spigot 专属类。
- **Libby 运行时下载**:减小插件体积;代价是首次启动需联网下载。
- **数据库全异步 + 统一调度器**:避免阻塞主线程,并为 Folia 的区域线程模型做兼容。
- **AuthMe 模式复用其认证**:保留既有 AuthMe 账号体系,KaLogin 只做 UI 与扩展数据。

## 踩过的坑

- Spigot 端 Dialog 需自建回调表与 JSON 映射,能力弱于 Paper(详见 `ui-dialog.md`)。
- `config.yml` 升级为"覆盖 + 保留同键用户值",无法做细粒度迁移。
- `resetterms` 命令名易被误写为 `resetterms`,文档曾出错(已修复,见 `docs.md`)。

## 当前待办

- 版本号与文档同步:`README.md` 已更新为 `1.5.1`,但 `CHANGELOG.md` 仅记录到 `1.4.0`,缺 1.5.x 条目(缺乏可靠变更来源,待补)。
- `API/` 目录为参考反编译源码,建议在 README/交接中明确其不可编译,避免误用。
- 仓库无有效 git 历史(`.git` 存在但 `git` 报不是仓库),变更追溯困难。

