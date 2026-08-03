# 登录/注册后动作

KaLogin 可以在玩家登录或注册完成后发送消息、执行命令或延迟后续动作。

## 配置位置

```yaml
events:
  login:
    - 'console: say 玩家%player_name%已登录!'
    - 'tell: <green>登录成功 <gray>欢迎回来, %player_name%'
    - 'wait: 20'
    - 'command: spawn'
  register:
    - 'console: say 玩家%player_name%已注册!'
    - 'tell: <aqua>注册成功 <gray>欢迎加入服务器, %player_name%'
    - 'wait: 20'
    - 'command: help'
```

## 常用动作

| 动作 | 说明 |
|---|---|
| `console: <命令>` | 由控制台执行命令 |
| `command: <命令>` | 由玩家执行命令 |
| `tell: <消息>` | 向玩家发送消息，支持颜色和 MiniMessage |
| `toast: ...` | 显示 Toast 提示，仅支持 Paper/Folia |
| `wait: <tick>` | 等待指定 tick 后继续 |

Spigot 不支持 Toast 动作。插件会跳过该动作并在控制台记录一次提示；需要兼容所有核心时请使用 `tell:`。

## Toast 参数

```yaml
- 'toast: type=task;icon=paper;title=<green>登录成功;description=<gray>欢迎回来'
```

| 参数 | 说明 |
|---|---|
| `type` | `task`、`goal` 或 `challenge` |
| `icon` | Toast 使用的物品 ID |
| `title` | Toast 标题 |
| `description` | Toast 描述 |

## 变量

动作中可以使用 `%player_name%` 表示玩家名。

示例：

```yaml
events:
  login:
    - 'tell: <green>欢迎回来, %player_name%'
```

如果已安装 PlaceholderAPI，消息和动作参数中也可以使用 PAPI 变量。
