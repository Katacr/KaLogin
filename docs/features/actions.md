# 登录/注册后动作

KaLogin 可以在玩家登录或注册完成后执行动作，例如发送提示、执行命令或显示 Toast。

## 配置位置

```yaml
events:
  login:
    - 'console: say 玩家%player_name%已登录!'
    - 'toast: type=task;icon=paper;title=<green>登录成功;description=<gray>欢迎回来, %player_name%'
    - 'wait: 20'
    - 'command: spawn'
  register:
    - 'console: say 玩家%player_name%已注册!'
    - 'toast: type=goal;icon=emerald;title=<aqua>注册成功;description=<gray>欢迎加入服务器, %player_name%'
    - 'wait: 20'
    - 'command: help'
```

## 常用动作

| 动作 | 说明 |
|---|---|
| `console: <命令>` | 由控制台执行命令 |
| `command: <命令>` | 由玩家执行命令 |
| `toast: ...` | 显示 Toast 提示 |
| `wait: <tick>` | 等待指定 tick 后继续 |

## 变量

动作中可以使用 `%player_name%` 表示玩家名。

示例：

```yaml
events:
  login:
    - 'console: tell %player_name% 欢迎回来'
```
