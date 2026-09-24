# UI 与对话框子系统

覆盖:Dialog 平台抽象、Paper/Spigot 实现、UI Body 渲染、MessageManager、ConfigUpdater、弹窗时序与错误提示。

## 现状

### Dialog 平台抽象

- 抽象接口 `dialog/LoginDialogPlatform.kt`:属性 `platformName`(`:14`)、`supportsToast`(`:17`);方法 `initialize`、`showLogin/showRegister/showChangePassword/showBindEmail/showRecoverPassword/showWelcome/showUserCenter/showLoading`、`close/sendMessage/kick/shutdown`(`:19-108`)。回调统一用 `*Response` 数据类(`:111-116`),业务侧不引用平台专属类型。
- 运行时选择 `dialog/LoginDialogPlatformLoader.kt:9-16`:先探测 Paper 标志类 `io.papermc.paper.dialog.Dialog` → `PaperLoginDialogPlatform`;否则探测 Bungee `net.md_5.bungee.api.dialog.Dialog` → `SpigotLoginDialogPlatform`;都失败抛 `"KaLogin requires Paper/Folia 1.21.7+ or Spigot 1.21.6+"`(`:12`)。`tryLoad` 捕获 `ReflectiveOperationException`/`LinkageError` 返回 null(`:22-26`)。入口 `KaLogin.kt:132-133`。

### Paper/Folia 实现

`dialog/paper/PaperLoginDialogPlatform.kt`:
- `platformName="Paper"`、`supportsToast=true`(`:40-41`)。
- 用 `Dialog.create{}` + `DialogType.notice/confirmation/multiAction`;`DialogAction.customClick(..., lifetime 5min)`(`:307-311`)。
- 基岩玩家特判:`closeable = authDialog && GeyserCompat.isBedrockPlayer(player)`,`afterAction` 相应调整(`:321-330`)。
- `sendMessage`/`kick` 直接发送/踢出 `Component`,保留富文本(`:293-299`)。
- item body 用 `DialogBody.item(...).description().showDecorations().showTooltip().width().height()`(`:396-403`)。

### Spigot 实现

`src/spigot/kotlin/.../dialog/spigot/`:
- `SpigotLoginDialogPlatform.kt`:`platformName="Spigot"`、`supportsToast=false`(`:49-50`)。
- 自建回调表 `ConcurrentHashMap<String, CallbackSession>` + 玩家索引(`:52-62`),按钮用 `CustomClickAction(kalogin:dialog_<hex>)`,由 `PlayerCustomClickEvent` 监听器消费(`:353-375`、`:245-253`)。
- `sendMessage` 经 `BungeeComponentSerializer` 转 `BaseComponent`(`:231-233`);`kick` 转 legacy 字符串后 `kickPlayer(String)`,**丢失 hover/click 富文本**(`:235-238`)。
- `showLoading` 用 `NoticeDialog` + no-op 按钮(`:216-221`)。
- item 渲染走 `SpigotItemDialogBody.kt` + `SpigotPublicItemMapper.kt`;映射失败回退基础物品并去重告警(`:329-346`)。

### 能力差异

| 能力 | Paper | Spigot |
|---|---|---|
| 原生 Dialog | 1.21.7+ | 1.21.6+ |
| `supportsToast` | true | false(toast 回退 body) |
| 回调 | Paper 托管(5min) | 自建 Map + 事件,无超时 |
| kick 富文本 | 保留 | 降级为 legacy |
| item 字段 | 完整 | 公共 API 子集 + JSON 补丁 |
| 基岩版适配 | 有 | 无 |

### UI Body 渲染

- `LoginUI.bodyElements(player, fileName)`(`LoginUI.kt:132-184`):读 `dataFolder/ui/<fileName>.yml` 的 `Body` 段,按 key 顺序取 `type`(缺省 `message`,忽略大小写):
  - `none` 跳过;`message` 读 `text` 解析为可点击组件;`item` 构造 `DialogBodyElement.Item`(`:141-179`)。
  - 未识别 type 静默忽略(无 default 分支)。
- `message` 字段:`text`(字符串/多行/列表)、`width`(默认 -1)(`:146`)。
- `item` 字段(material 默认 `apple`,非法跳过):`amount`、`name`、`lore`、`description`、`description_width`、`item_model`、`custom_model_data`、`show_overlays`(默认 false)、`show_tooltip`(默认 true)、`width`/`height`(默认 16)(`:149-178`)。
- 文本解析:
  - `resolveVariables` 先替换 `{player_name}`/`%player_name%`,再走 PAPI(`:89-95`)。
  - `parseText` 判断是否含 MiniMessage 标签;含标签且含 legacy 时先转 MiniMessage,否则按 MiniMessage;无标签走 legacy(`:74-84`)。
  - `parseClickableText` 解析 `<text=...;hover=...;command=...;url=...>`(`:97-130`)。
- 输入框外观来自 `config.yml` 的 `inputs.*`(`LoginUI.textInput` `:212-223`、`boolInitial` `:225-226`)。

### MessageManager

`MessageManager.kt`:
- `init` 确保 `lang/`,依次加载 `zh_CN`、`en_US`,默认语言取 `config.language`(`:21-31`)。
- `loadLanguageFile` 缺文件时释放默认语言文件,并 `updateLanguageFileIfNeeded` 合并缺失键(`:33-82`);`mergeMissingKeys` 按 `getKeys(true)` 补键(`:88-113`)。
- 取用:`getMessage`(缺键返回 key),`getComponent`/`getComponentFromMessage`(含 `&/§` 走 legacy,否则含 `<` 走 MiniMessage,否则纯文本),`sendMessage`/`sendComponent`/`kickPlayer`(`:127-232`)。
- `getPlayerLanguage` 目前是 stub,恒返回 `defaultLanguage`(`:118-122`)。
- `sendComponent`/`kickPlayer` 委托 `dialogPlatform`,故 Spigot kick 会退化。

### ConfigUpdater

`ConfigUpdater.kt`:
- `CURRENT_CONFIG_VERSION = 10`(`:52`),`config.yml:4` 为 10。
- `checkAndUpdateConfig`(`:66-101`):提取用户标量/列表值 → 读版本 → 检测废弃 `ui.*` 节点 → 已是新版且无废弃返回 false → 备份 `config_v<old>_backup_<ts>.yml` → jar 内 `config.yml` 整体覆盖 → 回写用户值(跳过 `config-version`)。
- 升级策略为"覆盖 + 保留同键值",无逐版本增量迁移;语言文件补全由 MessageManager 独立完成。

### 弹窗时序与错误提示

- 内置模式时序见 `LoginListener.onJoin`(`:28-141`);AuthMe 模式不显示 loading,弹窗有 2 秒防抖(`AuthMeLoginListener.kt:251-257`)。
- 欢迎弹窗 `WelcomeManager.showWelcomeIfNeeded`/`showWelcomeDialog`(`:10-63`):未接受条款则弹窗,未勾选提示 `welcome.must-accept`,保存失败提示 `welcome.save-failed`。
- 错误提示分发 `KaLogin.kt`:常量 none/body/toast(`:31-33`),`getErrorPromptType`(`:298-311`),`resolveDialogErrorComponent`(`:313-331`);toast 仅 Paper 支持,否则回退 body;toast 由 `EventActionExecutor.parseAndSendToast` 用 `Bukkit.getUnsafe().loadAdvancement` 动态实现(`:157-216`)。

## 关键决策与原因

- **平台抽象 + 反射加载**:主包不直接引用 Paper/Spigot 专属 Dialog 类,避免类加载冲突,并支持单一 JAR 跨平台。
- **Body 与输入框分离**:文本/物品布局放 `ui/*.yml`,输入框属性放 `config.yml`,便于统一调整输入控件。
- **配置整体覆盖式升级**:实现简单、避免遗漏新增键;代价是废弃键只能整体清理。

## 踩过的坑

1. **`ui-en/` 全部为死资源**:代码只读写 `dataFolder/ui/`,只释放 `ui/`,UI 语言与 `language` 配置未联动;`ui-en/` 还缺 `welcome.yml`。
2. **`en_US.yml` 缺 `config_update.*` 键**(`zh_CN.yml:203` 有,`en_US.yml` 无),英文环境会显示原始 key;`config_update.backup_failed`/`save_failed` 两种语言都缺。
3. **两套文本解析器行为不一致**:`LoginUI.parseText` 支持 MiniMessage 与 legacy 混用;`MessageManager.getComponent` 只要含 `&/§` 就整串按 legacy 处理,MiniMessage 标签会原样显示。
4. **玩家语言未实现**:`getPlayerLanguage` 恒返回默认语言。
5. **Spigot kick 丢富文本**;Spigot item 映射能力受限,复杂属性静默丢失。
6. **toast 依赖内部 API** `Bukkit.getUnsafe().loadAdvancement`,未来 Paper 版本有兼容风险。
7. **可点击文本正则脆弱**:值不能含 `; ' "` 或换行,`command` 原样注入无前缀校验(`LoginUI.kt:99-115`)。
8. **`type: none`/拼写错误静默无输出也无告警**,排错困难。
9. **`/kl reload` 不重载 UI 文件**:但 `bodyElements` 每次弹窗重读磁盘,UI 改动下次弹窗即生效;语言改动需 reload。文档曾称 reload 会重载界面，需注意表述。
10. **回调生命周期不对称**:Paper 5 分钟过期,Spigot 自建表仅退出/关闭/`shutdown` 清理。
11. **平台探测副作用**:若某 Paper 版本无原生 dialog 但有 Bungee dialog 类,会选中 Spigot 适配器;两者都缺则直接 enable 失败。

## 当前待办

- [ ] 清理或实现 `ui-en/`(多语言 UI),当前为死资源。
- [ ] 补齐 `en_US.yml` 的 `config_update.*` 键，并为两种语言补 `backup_failed`/`save_failed`。
- [ ] 统一 `LoginUI` 与 `MessageManager` 的文本解析策略。
- [ ] 实现 `getPlayerLanguage`，或移除该 stub 避免误导。
- [ ] 评估以 Paper Adventure `AsyncChatEvent` 替代反作弊的 `AsyncPlayerChatEvent`(见 `features.md`)。
- [ ] 评估 toast 实现去除 `UnsafeValues` 依赖的替代方案。
