# 界面自定义

KaLogin 的登录、注册、修改密码和欢迎条款界面都可以通过 YAML 文件修改。

## 文件位置

```text
plugins/KaLogin/ui/
```

| 文件 | 用途 |
|---|---|
| `login.yml` | 登录界面 |
| `register.yml` | 注册界面 |
| `change-password.yml` | 修改密码界面 |
| `welcome.yml` | 欢迎和条款确认界面 |

这些文件由插件首次启动时生成。修改后执行 `/kl reload`。

## 正文内容

每个 UI 文件都有 `Body` 节点。`Body` 中可以添加文本和物品展示。

### 文本

```yaml
Body:
  welcome:
    type: 'message'
    text: '<gradient:gold:yellow>欢迎回到服务器！<reset>'
    width: 300
```

| 属性 | 说明 |
|---|---|
| `type` | 使用 `message` |
| `text` | 显示的文字 |
| `width` | 可选，文字区域宽度 |

### 物品

```yaml
Body:
  icon:
    type: 'item'
    material: 'apple'
    name: '&a服务器图标'
    lore:
      - '&7欢迎来到服务器'
    description: '&7请按提示完成登录'
    description_width: 300
    item_model: ''
    custom_model_data: 1001
```

| 属性 | 说明 |
|---|---|
| `type` | 使用 `item` |
| `material` | 物品 ID，例如 `apple`、`diamond` |
| `name` | 物品名称 |
| `lore` | 物品 Lore |
| `description` | 物品下方说明，可写文本或列表 |
| `description_width` | 可选，说明文字宽度 |
| `item_model` | 自定义物品模型，格式为 `namespace:path` |
| `custom_model_data` | 旧版 CustomModelData 数值 |

## 文本格式

支持传统颜色代码：

```yaml
text: '&a欢迎回来，&f请输入密码'
```

也支持 MiniMessage：

```yaml
text: '<green>欢迎回来</green>'
```

## 可点击文本

可以在文本中添加可点击链接或命令：

```yaml
text: '&7点击 <text=&b[官网];hover=&7打开官网;url=https://example.com> 查看规则'
```

```yaml
text: '&7点击 <text=&a[绑定邮箱];hover=&7打开邮箱绑定;command=/bindemail>'
```

| 参数 | 说明 |
|---|---|
| `text` | 玩家看到的文字 |
| `hover` | 鼠标悬浮提示 |
| `url` | 点击后打开链接 |
| `command` | 点击后执行命令 |

`url` 和 `command` 一般选择一个使用。

## 输入框外观

输入框不在 UI 文件中配置，而是在 `config.yml` 的 `inputs` 中配置。

```yaml
inputs:
  login:
    login_password:
      width: 200
      height: 17
      labelVisible: true
      initial: ''
```

详见 [配置文件](../config/config.md)。
