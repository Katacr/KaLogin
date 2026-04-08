# KaLogin

一个强大的 Minecraft Paper 服务器登录插件，提供安全的用户认证和反作弊功能。

## 支持版本
Paper 1.21.7 及以上

## 最新版本
**v1.2.0** - [查看更新日志](CHANGELOG.md)

## 功能特性

- 🔐 安全的密码加密（使用 bcrypt）
- 🌍 多语言支持（中文/英文）
- 🚫 IP 账号数量限制
- 🔑 **用户级自动登录设置** - 每个用户独立选择是否同 IP 自动登录
- 🔒 **修改密码功能** - 支持玩家修改自己的密码
- 👁️ 登录/注册期间反作弊机制
- ⏱️ 登录/注册超时限制
- 📊 密码强度验证
- 💾 支持 SQLite 和 MySQL 数据库
- ✨ **可自定义的UI界面** - 支持MiniMessage格式、PAPI变量、hovertext等高级功能
- 📦 **轻量级依赖管理** - 运行时自动下载依赖，减小插件体积

## 快速开始

### 安装

1. 下载最新版本的 KaLogin JAR 文件
2. 将文件放入服务器的 `plugins/` 目录
3. 启动服务器，插件会自动生成配置文件

### 首次使用

服务器首次启动后：

1. 玩家加入服务器时会自动弹出注册界面
2. 输入密码完成注册
3. 下次登录时输入密码即可
4. 可以在登录时勾选"同 IP 自动登录"选项

### 常用命令

- `/kalogin` 或 `/kl` - 主命令
  - `/kl delete <玩家>` - 删除玩家数据
  - `/kl register <玩家> <密码>` - 为玩家设置密码
  - `/kl reload` - 重载配置文件
- `/changepassword` 或 `/cp` - 修改自己的密码

## 配置

插件首次运行后会生成配置文件 `plugins/KaLogin/config.yml`。

### 数据库配置

```yaml
database:
  type: "sqlite"  # 或 "mysql"

  mysql:
    host: "localhost"
    port: 3306
    database: "kalogin"
    username: "root"
    password: "password"
```

### 密码策略配置

```yaml
settings:
  min-password-length: 6
  max-password-length: 20
  has-uppercase: false
  has-lowercase: false
  has-number: false
  has-symbol: false
```

### 登录配置

```yaml
login:
  register-timeout: 90        # 注册超时时间（秒）
  login-timeout: 60           # 登录超时时间（秒）
  max-login-attempts: 3       # 最大登录尝试次数
  max-accounts-per-ip: 3     # 每个 IP 最大账号数量
```


## 命令

- `/kalogin` 或 `/kl` - 主命令
  - `/kl delete <玩家>` - 删除玩家数据
  - `/kl register <玩家> <密码>` - 为玩家设置密码
  - `/kl reload` - 重载配置文件
- `/changepassword` 或 `/cp` - 修改自己的密码

## 权限

- `kalogin.admin` - 允许使用管理命令（默认：OP）
- `kalogin.changepassword` - 允许修改密码（默认：所有玩家）

## 自动登录说明

从 1.2.0 版本开始，自动登录改为用户级设置：

- 玩家可以在登录界面勾选"记住此设备，同 IP 自动登录"
- 每个用户的选择会保存在数据库中
- 不同用户可以有不同的自动登录设置
- 删除数据库记录后需要重新设置

## UI 界面自定义

KaLogin 支持通过 YAML 配置文件自定义登录和注册界面的 Body 区域。配置文件位于 `plugins/KaLogin/ui/` 目录下。

### 支持的功能

- ✅ **MiniMessage 格式** - 支持丰富的文本格式 `<red>`, `<bold>`, `<gradient:red:blue>` 等
- ✅ **Legacy 颜色代码** - 兼容传统的 `&a`, `&b` 等颜色代码
- ✅ **PAPI 变量** - 支持 PlaceholderAPI 变量（需要安装 PlaceholderAPI）
- ✅ **Hovertext** - 支持可点击的悬停文本（可执行命令、打开链接等）

### 配置文件说明

#### login.yml - 登录界面配置

```yaml
Body:
  # 欢迎语（使用MiniMessage格式）
  welcome:
    type: 'message'
    text: '<gradient:gold:yellow>&l欢迎回到服务器！<reset>\n&f请输入你的密码以继续游戏'

  # 物品显示
  apple_item:
    type: 'item'
    material: 'apple'
    name: '&a&l服务器图标'
    description: '&f这是服务器图标的介绍'
    # item_model: 'minecraft:diamond'  # 可选：自定义物品模型（1.21.7+）
    # item_model: 'minecraft:diamond'  # 可选：自定义物品模型（1.21.7+）

  # 纯文本消息
  intro:
    type: 'message'
    text: '&7请在下方输入密码，然后点击确认。'

  # 带hovertext的文本（可点击文本）
  hovertext_demo:
    type: 'message'
    text: '&7点击 <text=&b[ 官网 ];hover=&7打开官网;url=https://example.com> 访问官网'
```

#### change-password.yml - 修改密码界面配置

```yaml
Body:
  # 欢迎语
  welcome:
    type: 'message'
    text: '<gradient:gold:orange>&l修改密码<reset>\n&f为了账户安全，请定期修改密码'

  # 提示信息
  tips:
    type: 'message'
    text: '&7请输入旧密码和新密码，新密码需要输入两次确认。'
```

#### register.yml - 注册界面配置

```yaml
Body:
  # 欢迎语（使用MiniMessage格式）
  welcome:
    type: 'message'
    text: '<gradient:green:aqua>&l欢迎来到服务器！<reset>\n&f请设置你的密码以开始游戏'

  # 物品显示
  apple_item:
    type: 'item'
    material: 'apple'
    name: '&a&l服务器图标'
    description: '&f这是服务器图标的介绍'
    # item_model: 'minecraft:diamond'  # 可选：自定义物品模型（1.21.7+）
    # item_model: 'minecraft:diamond'  # 可选：自定义物品模型（1.21.7+）

  # 纯文本消息
  intro:
    type: 'message'
    text: '&7请在下方设置您的密码，确保密码强度足够。'

  # 密码要求提示
  password_requirements:
    type: 'message'
    text: '&e密码要求：&f最少6位，最多20位'
```

### 注意事项

⚠️ **欢迎语已移除语言文件**：之前的欢迎语配置（`login.welcome-message` 和 `register.welcome-message`）已从语言文件中移除。现在欢迎语需要在对应的 `ui/login.yml` 或 `ui/register.yml` 的 `Body` 区域中自定义。

### Hovertext 格式说明

格式: `<text='显示文字';hover='悬停文字';command='指令';url='链接'>`

示例:
```yaml
# 执行命令
text: '&7点击 <text=&a[传送主城 ];hover=&7传送到主城;command=/spawn> 传送到主城'

# 打开链接
text: '&7访问 <text=&b[ 官网 ];hover=&7打开官网;url=https://example.com> 查看更多信息'

# 纯文本（无点击事件）
text: '&7欢迎来到服务器！'
```

### 支持的Body类型

#### 1. message - 文本消息
```yaml
message_key:
  type: 'message'
  text: '&7你的文本内容'
  width: 200  # 可选，文本宽度（像素）
```

#### 2. item - 物品显示
```yaml
item_key:
  type: 'item'
  material: 'apple'  # 物品材质
  name: '&a&l物品名称'  # 可选，物品显示名称
  description: '&f物品描述文本'  # 可选，物品描述
  item_model: ''  # 可选，自定义物品模型（1.21.7+），格式: namespace:path
  width: 16  # 可选，渲染宽度（像素）
  height: 16  # 可选，渲染高度（像素）
  decorations: true  # 可选，是否显示装饰（耐久、数量等）
  tooltip: true  # 可选，是否显示悬停提示
```

**item_model 参数说明**：
- 该参数仅支持 Minecraft 1.21.7 及以上版本
- 用于设置自定义物品模型，格式为 `namespace:path`
- 示例：
  - `item_model: 'minecraft:diamond'` - 使用钻石模型
  - `item_model: 'myplugin:custom_model'` - 使用自定义插件模型
  - 如果为空或不设置，则使用默认模型

### PAPI 变量示例

如果安装了 PlaceholderAPI，可以在配置中使用任何 PAPI 变量：

```yaml
Body:
  welcome:
    type: 'message'
    text: '&a欢迎, %player_name%!'

  level_info:
    type: 'message'
    text: '&7你的等级: &f%player_level%'
```

## 常见问题

### Q: 插件体积很小，会不会缺少依赖？
A: 不会。从 1.2.0 版本开始，插件采用 Libby 运行时下载依赖，首次启动时会自动下载所需依赖（kotlin-stdlib、jbcrypt、sqlite-jdbc），所以插件体积更小。

### Q: 如何修改密码？
A: 使用 `/changepassword` 或 `/cp` 命令，通过修改密码界面进行操作，输入旧密码和新密码即可。

### Q: 自动登录在哪里设置？
A: 在登录界面底部有一个"同 IP 自动登录"的复选框，勾选后即可启用自动登录。每个用户可以独立设置。


## 开放 API

KaLogin 提供开放的 API 接口，允许其他插件监听玩家的登录、注册、修改密码等操作。

### 添加依赖

将`KaLogin-1.3.1.jar`插件本体复制到你项目内的libs文件夹，并在你的插件的 `build.gradle.kts` 中添加 KaLogin 作为软依赖：

```kotlin
dependencies {
    compileOnly(fileTree("libs") { include("KaLogin-1.3.1.jar") })
}
```

在你的插件的 `plugin.yml` 中添加软依赖：

```yaml
softdepend:
  - KaLogin
```

### API 概述

KaLogin 提供两种使用方式：
1. **Bukkit 事件系统** - 使用标准的 Bukkit Event API
2. **KaLoginListener 接口** - 使用 KaLogin 提供的专用监听器接口

### 使用 Bukkit 事件系统

KaLogin 定义了以下事件类，位于 `org.katacr.kalogin.listener` 包中：

| 事件类 | 说明 | 包含数据 |
|--------|------|----------|
| `PlayerLoginSuccessEvent` | 玩家登录成功 | `player`, `ip`, `isAutoLogin` |
| `PlayerLoginFailedEvent` | 玩家登录失败 | `player`, `remainingAttempts` |
| `PlayerAutoLoginEvent` | 玩家自动登录成功 | `player`, `ip` |
| `PlayerRegisterSuccessEvent` | 玩家注册成功 | `player`, `ip` |
| `PlayerRegisterFailedEvent` | 玩家注册失败 | `player`, `reason` |
| `PlayerChangePasswordSuccessEvent` | 修改密码成功 | `player` |
| `PlayerChangePasswordFailedEvent` | 修改密码失败 | `player`, `reason` |
| `PlayerLogoutEvent` | 玩家登出 | `player` |
| `PlayerUnregisterEvent` | 玩家注销账户 | `player` |
| `PlayerAdminUnregisterEvent` | 管理员注销账户 | `playerName` |

**示例代码：**

```kotlin
package com.example.plugin

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kalogin.listener.*

class MyPlugin : JavaPlugin(), Listener {

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
    }

    @EventHandler
    fun onPlayerLoginSuccess(event: PlayerLoginSuccessEvent) {
        val player = event.player
        val ip = event.ip
        val isAutoLogin = event.isAutoLogin

        if (isAutoLogin) {
            logger.info("玩家 ${player.name} 通过自动登录成功")
        } else {
            logger.info("玩家 ${player.name} 登录成功，IP: $ip")
        }
    }

    @EventHandler
    fun onPlayerLoginFailed(event: PlayerLoginFailedEvent) {
        val player = event.player
        val remaining = event.remainingAttempts

        logger.warning("玩家 ${player.name} 登录失败，剩余尝试次数: $remaining")
    }

    @EventHandler
    fun onPlayerRegisterSuccess(event: PlayerRegisterSuccessEvent) {
        logger.info("玩家 ${event.player.name} 注册成功")
    }

    @EventHandler
    fun onPlayerLogout(event: PlayerLogoutEvent) {
        logger.info("玩家 ${event.player.name} 登出")
    }
}
```

### 使用 KaLoginListener 接口

除了 Bukkit 事件系统，你还可以使用 KaLogin 提供的专用监听器接口：

**示例代码：**

```kotlin
package com.example.plugin

import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kalogin.listener.*

class MyPlugin : JavaPlugin() {

    private lateinit var myListener: MyKaLoginListener

    override fun onEnable() {
        // 获取 API 实例
        val api = KaLoginAPI.getInstance()
        if (api == null) {
            logger.warning("KaLogin 未安装，API 功能不可用")
            return
        }

        if (!api.isEnabled()) {
            logger.warning("KaLogin 未启用，API 功能不可用")
            return
        }

        // 创建并注册监听器
        myListener = MyKaLoginListener(this)
        api.registerListener(this, myListener)

        logger.info("已注册 KaLogin 事件监听器")
    }

    override fun onDisable() {
        // 注销监听器
        if (::myListener.isInitialized) {
            KaLoginAPI.getInstance()?.unregisterListener(this, myListener)
        }
    }
}

class MyKaLoginListener(private val plugin: MyPlugin) : KaLoginListener {

    override fun onPlayerLoginSuccess(event: PlayerLoginSuccessEvent) {
        plugin.logger.info("玩家 ${event.player.name} 登录成功！")
    }

    override fun onPlayerAutoLogin(event: PlayerAutoLoginEvent) {
        plugin.logger.info("玩家 ${event.player.name} 自动登录成功！")
    }

    override fun onPlayerRegisterSuccess(event: PlayerRegisterSuccessEvent) {
        plugin.logger.info("玩家 ${event.player.name} 注册成功！")
    }

    override fun onPlayerChangePasswordSuccess(event: PlayerChangePasswordSuccessEvent) {
        plugin.logger.info("玩家 ${event.player.name} 修改密码成功！")
    }
}
```

### 检查玩家登录状态

你可以在其他插件中检查玩家是否已登录：

```kotlin
// 使用 KaLoginAPI
val api = KaLoginAPI.getInstance()
if (api != null && api.isEnabled()) {
    val player = server.getPlayer("Steve") ?: return
    if (api.isPlayerLoggedIn(player)) {
        player.sendMessage("你已经登录了！")
    }
}
```

### 事件参数详解

#### PlayerLoginSuccessEvent
```kotlin
val player: Player       // 登录的玩家
val ip: String           // 玩家 IP 地址
val isAutoLogin: Boolean // 是否为自动登录
```

#### PlayerLoginFailedEvent
```kotlin
val player: Player       // 尝试登录的玩家
val remainingAttempts: Int // 剩余尝试次数
```

#### PlayerAutoLoginEvent
```kotlin
val player: Player  // 自动登录的玩家
val ip: String       // 玩家 IP 地址
```

#### PlayerRegisterSuccessEvent
```kotlin
val player: Player  // 注册的玩家
val ip: String      // 玩家 IP 地址
```

#### PlayerRegisterFailedEvent
```kotlin
val player: Player  // 尝试注册的玩家
val reason: String  // 失败原因
```

#### PlayerChangePasswordSuccessEvent
```kotlin
val player: Player  // 修改密码的玩家
```

#### PlayerChangePasswordFailedEvent
```kotlin
val player: Player  // 尝试修改密码的玩家
val reason: String  // 失败原因，可能是：
                   // - "Old password incorrect" - 旧密码错误
                   // - "Too many attempts" - 尝试次数过多
                   // - "Invalid password format: xxx" - 新密码格式错误
                   // - "New password same as old password" - 新旧密码相同
                   // - "Passwords do not match" - 两次密码不一致
                   // - "Database error" - 数据库错误
```

### 注意事项

1. **软依赖**：确保在你的插件中正确配置 `softdepend`，以确保 KaLogin 在你的插件之前加载
2. **空值检查**：始终检查 `KaLoginAPI.getInstance()` 是否为 null
3. **启用状态**：使用 `api.isEnabled()` 检查 KaLogin 是否已启用
4. **事件线程**：所有事件都在主线程触发，可以安全地进行 Bukkit 操作
5. **AuthMe 兼容**：当 KaLogin 使用 AuthMe 模式时，事件仍会正常触发


## 开源协议

本项目采用开源协议，欢迎贡献代码。

## 技术支持

如有问题或建议，请提交 Issue 或 Pull Request。


