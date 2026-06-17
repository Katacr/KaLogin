# UI 界面配置

KaLogin 使用 Paper 1.21+ 的 Dialog API 实现登录/注册界面。UI 配置文件位于 `plugins/KaLogin/ui/` 目录下。

---

## 文件结构

```
plugins/KaLogin/
├── ui/                    # 中文界面（language: zh_CN 时使用）
│   ├── login.yml          # 登录对话框
│   ├── register.yml       # 注册对话框
│   ├── change-password.yml # 修改密码对话框
│   └── welcome.yml        # 欢迎/条款对话框
└── ui-en/                 # 英文界面（language: en_US 时使用）
    ├── login.yml
    ├── register.yml
    └── change-password.yml
```

插件会根据 `config.yml` 中的 `language` 设置自动选择对应的 UI 目录。

---

## Body 元素类型

每个 UI 文件的 `Body` 部分由多个元素组成，支持以下类型：

### 1. message（文本消息）

显示一段文本，支持颜色代码和 MiniMessage 格式。

```yaml
welcome:
  type: 'message'
  text: |
    <gradient:gold:yellow>欢迎回到服务器！<reset>
    请输入你的密码以继续游戏
```

**属性：**

| 属性 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定为 `message` |
| `text` | string | 显示的文本内容，支持颜色代码和 MiniMessage |

### 2. item（物品展示）

在对话框中展示一个 Minecraft 物品。

```yaml
apple_item:
  type: 'item'
  material: 'apple'
  name: '&a&l服务器图标'
  description: '&f这是服务器图标的介绍'
  item_model: ''
```

**属性：**

| 属性 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定为 `item` |
| `material` | string | 物品材质 ID（如 `apple`、`iron_door`、`diamond`） |
| `name` | string | 物品显示名称，支持颜色代码 |
| `description` | string | 物品描述/Lore，支持颜色代码 |
| `item_model` | string | 自定义物品模型（1.21.7+），格式为 `namespace:path`。留空表示不使用自定义模型 |

**item_model 示例：**
```yaml
item_model: 'minecraft:diamond'         # 使用钻石模型
item_model: 'myplugin:custom_sword'     # 使用自定义插件物品模型
item_model: ''                          # 不使用自定义模型（默认）
```

---

## 文本格式

### 颜色代码

支持传统 `&` 颜色代码：

| 代码 | 颜色 | 代码 | 样式 |
|------|------|------|------|
| `&0` | 黑色 | `&l` | **粗体** |
| `&1` | 深蓝 | `&o` | *斜体* |
| `&2` | 深绿 | `&n` | <u>下划线</u> |
| `&3` | 深青 | `&m` | ~~删除线~~ |
| `&4` | 深红 | `&r` | 重置 |
| `&5` | 紫色 | | |
| `&6` | 金色 | | |
| `&7` | 灰色 | | |
| `&8` | 深灰 | | |
| `&9` | 蓝色 | | |
| `&a` | 绿色 | | |
| `&b` | 青色 | | |
| `&c` | 红色 | | |
| `&d` | 粉色 | | |
| `&e` | 黄色 | | |
| `&f` | 白色 | | |

### MiniMessage 格式

支持 Paper 的 MiniMessage 格式：

```yaml
# 渐变色
text: '<gradient:gold:yellow>渐变文本<reset>'

# 单色
text: '<red>红色文本'
text: '<green>绿色文本'

# 样式
text: '<bold>粗体</bold>'
text: '<italic>斜体</italic>'
```

### HoverText（可交互文本）

在 `message` 类型中可以嵌入可点击/悬浮的文本：

```yaml
text: '&7点击 <text=&b[ 官网 ];hover=&7打开官网;url=https://example.com> 访问官网'
```

**语法：** `<text=显示文本;hover=悬浮提示;url=链接地址>`

| 参数 | 说明 |
|------|------|
| `text` | 显示的文本内容 |
| `hover` | 鼠标悬浮时显示的提示文本 |
| `url` | 点击后打开的 URL 链接 |
| `command` | 点击后执行的命令（与 `url` 二选一） |

**示例：**
```yaml
# 打开链接
text: '<text=&b[官网];hover=&7点击打开;url=https://example.com>'

# 执行命令
text: '<text=&a[绑定邮箱];hover=&7点击打开邮箱绑定;command=/bindemail>'
```

---

## 各界面配置示例

### 登录界面 (`ui/login.yml`)

```yaml
Body:
  welcome:
    type: 'message'
    text: |
      <gradient:gold:yellow>欢迎回到服务器！<reset>
      请输入你的密码以继续游戏

  apple_item:
    type: 'item'
    material: 'apple'
    name: '&a&l服务器图标'
    description: '&f这是服务器图标的介绍'
    item_model: ''

  intro:
    type: 'message'
    text: '&7请在下方输入密码，然后点击确认。'

  hovertext_demo:
    type: 'message'
    text: '&7点击 <text=&b[ 官网 ];hover=&7打开官网;url=https://example.com> 访问官网'
```

### 注册界面 (`ui/register.yml`)

```yaml
Body:
  welcome:
    type: 'message'
    text: |
      <gradient:green:aqua>欢迎来到服务器！<reset>
      请设置你的密码以开始游戏

  apple_item:
    type: 'item'
    material: 'apple'
    name: '&a&l服务器图标'
    description: '&f这是服务器图标的介绍'
    item_model: 'minecraft:diamond'

  intro:
    type: 'message'
    text: '&7请在下方设置您的密码，确保密码强度足够。'

  password_requirements:
    type: 'message'
    text: '&e密码要求：&f最少6位，最多20位'

  hovertext_demo:
    type: 'message'
    text: '&7点击 <text=&b[ 官网 ];hover=&7打开官网;url=https://example.com> 访问官网'
```

### 修改密码界面 (`ui/change-password.yml`)

```yaml
Body:
  welcome:
    type: 'message'
    text: |
      <gradient:gold:red>修改密码<reset>
      &9为了账号安全，请定期修改密码

  lock_item:
    type: 'item'
    material: 'iron_door'
    name: '&a&l安全提示'
    description: '&f修改密码是保护账号安全的重要措施'
    item_model: ''

  intro:
    type: 'message'
    text: '&7请在下方依次输入旧密码、新密码和确认新密码。'

  password_requirements:
    type: 'message'
    text: '&e密码要求：&f最少6位，最多20位，需符合密码复杂度要求'
```

### 欢迎/条款界面 (`ui/welcome.yml`)

```yaml
Body:
  welcome:
    type: 'message'
    text: |
      <gradient:gold:yellow>欢迎来到服务器！<reset>
      在继续游戏前，请先阅读以下说明与服务器条款。

  rules:
    type: 'message'
    text: |
      &7- 请遵守服务器规则
      &7- 禁止作弊、恶意破坏与骚扰他人
      &7- 如继续游戏，则视为你已阅读并同意服务器条款

  website:
    type: 'message'
    text: '&7点击 <text=&b[官网/规则页面];hover=&7打开规则页面;url=https://example.com> 查看完整规则'
```

---

## 自定义技巧

1. **元素顺序**：Body 中的元素按 YAML 中的书写顺序从上到下排列
2. **元素命名**：每个元素的 key（如 `welcome`、`apple_item`）可以自定义，只要不重复即可
3. **添加/删除元素**：可以自由增减 Body 中的元素数量
4. **多行文本**：使用 YAML 的 `|` 语法编写多行文本
5. **物品选择**：`material` 使用 Minecraft 物品 ID，如 `diamond`、`golden_apple`、`book` 等
