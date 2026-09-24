# 文档体系与修复记录

覆盖:`docs/`(中文)、`docs-en/`(英文)、`README.md`、`CHANGELOG.md` 的结构与本次修复。

## 现状

- 用户文档分中英文两套,结构一致,基于 mdBook 风格:`docs/SUMMARY.md` / `docs-en/SUMMARY.md` 为目录。
- 页面:首页、`home/start.md`、`config/config.md`、`features/{auth-modes,ui,email,welcome,actions}.md`、`perm/{commands,permissions}.md`、`PlaceholderAPI.md`、`faq.md`。
- **重定向存根(未登记目录)**:`docs/{commands,config,events,ui}.md` 均只有一句"已迁移到…请阅读 X",保留旧链接兼容用。
- `docs/lang.md` / `docs-en/lang.md` 是有效页面,但**未登记进 `SUMMARY.md`**,玩家从目录导航不到。
- `README.md` 内容详尽(功能、配置、UI、API 示例),是 API 的唯一文档来源;`CHANGELOG.md` 仅记录到 `v1.4.0`。
- 资源侧语言文件:`src/main/resources/lang/zh_CN.yml`(221 行)、`en_US.yml`(215 行)。

## 本次修复的文档错误(2026-09-19)

### README.md

1. **平台描述过窄**:标题与"支持版本"仅写 Paper 1.21.7+,补充为 Paper / Folia 1.21.7+、Spigot 1.21.6+、Java 21。
2. **版本号陈旧**:最新版本 `v1.4.0` → `v1.5.1`(与 `build.gradle.kts`、`plugin.yml` 对齐)。
3. **UI item 字段错误**:示例使用不存在的 `decorations` / `tooltip`,实际键为 `show_overlays` / `show_tooltip`(`LoginUI.kt:173-174`);并补上缺失的 `amount`、`lore`、`custom_model_data`(`LoginUI.kt:166-172`)。
4. **重复注释**:`login.yml` / `register.yml` 示例中 `item_model` 注释行各重复两次,已去重。
5. **API 依赖版本陈旧**:`KaLogin-1.3.1.jar` → `KaLogin-1.5.1.jar`。
6. **API 机制描述错误(重要)**:原文宣称"使用标准的 Bukkit Event API + `@EventHandler`",但 `listener/KaLoginEvents.kt` 中的事件类均为普通 `data class`,**不是 Bukkit Event 子类**(全项目无 `org.bukkit.event.Event` 子类),`@EventHandler` 不会生效。已改为说明通过 `KaLoginListener` 接口回调,并删除错误的 `@EventHandler` 示例,保留正确的接口示例。
7. **`getInstance()` 空值说明错误**:实现为惰性单例,**永不返回 null**(`KaLoginAPI.kt:46-52`);修正"注意事项"并调整示例为先检查插件是否存在、再用 `isEnabled()`。
8. **依赖清单不全**:FAQ 中补上运行时下载的 `jakarta.mail`、`jakarta.activation`、`adventure-text-serializer-bungeecord`。

### docs/ 与 docs-en/

9. **`/kl reload` 会重载界面的说法错误**:`/kl reload` 只重载 config 与语言(`KaLoginCommand.kt:108-114`),不重载 UI;而 UI 文件在每次弹窗时从磁盘实时读取(`LoginUI.kt:132-136`)。修正:
   - `perm/commands.md` 中 `/kl reload` 描述去掉"界面"。
   - `features/ui.md` 改为说明"保存后下次弹窗生效,无需 reload"。
   - `faq.md` 修改 UI 的排查建议改为"界面已打开时让玩家重进/再次触发"。
10. **动作变量不全**:`features/actions.md` 补充 `{player_name}`(实现同时支持 `%player_name%` 与 `{player_name}`,`EventActionExecutor.kt:145-155`)。
11. **AuthMe 模式行为说明缺失**:`features/auth-modes.md` 补充注意——AuthMe 模式下同 IP 自动登录由 AuthMe session 处理、KaLogin 勾选框不生效,且 KaLogin 的登录/注册超时在 AuthMe 模式下也不生效。

## 群组跨服功能的文档新增(2026-09-19)

- 新增 `docs/features/proxy.md` 与 `docs-en/features/proxy.md`,并登记进两份 `SUMMARY.md` 与 `features/README.md`。
- `docs/config/config.md` 与 `docs-en/config/config.md` 增加 `proxy` 节点说明表。
- 根 `README.md` 功能列表新增"群组服务器跨服登录",API 事件表新增 `PlayerProxyRestoreEvent`。
- `config-version` 10 → 11;`handoff/proxy.md` 记录实现细节。

## 上次下线位置文档新增(2026-09-19)

- `docs/features/proxy.md` 与 `docs-en/features/proxy.md` 增加"自动回到上次位置"说明与 `last-seen` 配置表。
- `docs/config/config.md` 与 `docs-en/config/config.md` 增加 `last-seen` 节点说明。
- 根 `README.md` 功能列表新增"登录后自动回到上次位置"。
- `config-version` 11 → 12;`handoff/lastseen.md` 记录实现细节。
- KaProxy 侧新增 `docs/modules/lastseen.md` 与 `docs-en/modules/lastseen.md`。
- 直连改造(2026-09-19):KaProxy 新增 `modules.lastseen.connect-directly` 与位置持久化,`features/proxy.md` 中英同步说明"初次连接直接回上次子服,消除双重进服"。

## 已核对但**不是**错误

- 命令名统一为 `resetterms`(`KaLoginCommand.kt:32`、`AuthMeManager.kt:235`、`plugin.yml`、语言文件、docs、README 全部一致)。此前终端字体下易误读为 `resetterms`,经字节级校验确认无差异,**未改动**。

## 已知文档缺口(未修复)

1. **`CHANGELOG.md` 缺 1.5.x 条目**:最新只到 `v1.4.0`,缺少 1.5.0 / 1.5.1 变更来源,无法在不臆造的前提下补齐。
2. **`en_US.yml` 缺 `config_update.*` 键**:`zh_CN.yml:203` 有、`en_US.yml` 无;`config_update.backup_failed` / `save_failed` 两种语言都缺,英文环境可能显示原始 key。属资源问题,非 docs。
3. **玩家命令权限文档与实现不符**:`docs/perm/permissions.md` 描述权限可控制玩家命令,但实现中 `onCommand` 未校验(仅 admin 命令与 bindemail Tab 补全校验),详见 `commands-api.md`。
4. **开放 API 未进入 `docs/`**:API 说明只在 README,且中英文档均无 API 页。
5. **`docs/lang.md` 未登记 `SUMMARY.md`**;`docs/{commands,config,events,ui}.md` 为重定向存根。
6. **`ui-en/` 为死资源**:代码只读写 `plugins/KaLogin/ui/`,英文 UI 未实现,详见 `ui-dialog.md`。

## 文档同步要求(AGENTS.md)

- 改动涉及用户可见内容(指令、权限、配置项、占位符、流程)时,必须同步更新 `docs/` 与 `docs-en/`,新增页在 `SUMMARY.md` 登记。
- 本次改动同时更新了交接文档,修复记录见本文件。

## 当前待办

- [ ] 补充 1.5.x 的 `CHANGELOG.md`(需可靠的变更来源)。
- [ ] 为 `en_US.yml` 补 `config_update.*` 键(从 `zh_CN.yml` 翻译)。
- [ ] 视权限实现修复进度,同步 `perm/permissions.md`。
- [ ] 将开放 API 整理为 `docs/` 与 `docs-en/` 页面并登记。
- [ ] 评估是否将 `docs/lang.md` 登记目录、清理重定向存根。
- [ ] 决定 `ui-en/` 是删除还是落地多语言 UI。
