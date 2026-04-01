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


## 开源协议

本项目采用开源协议，欢迎贡献代码。

## 技术支持

如有问题或建议，请提交 Issue 或 Pull Request。


