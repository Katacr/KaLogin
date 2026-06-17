# 事件动作系统

KaLogin 支持在玩家登录或注册成功后自动执行一系列动作，通过 `config.yml` 中的 `events` 配置。

---

## 配置位置

```yaml
events:
  login:
    - '动作1'
    - '动作2'
  register:
    - '动作1'
    - '动作2'
```

---

## 支持的事件

| 事件 | 触发时机 |
|------|----------|
| `login` | 玩家登录成功后 |
| `register` | 玩家注册成功后 |

---

## 动作类型

### 1. console（控制台命令）

以控制台身份执行命令。

```yaml
- 'console: say 玩家%player_name%已登录!'
- 'console: give %player_name% diamond 1'
- 'console: lp user %player_name% parent set member'
```

**格式：** `console: <命令>`

### 2. command（玩家命令）

以玩家身份执行命令。

```yaml
- 'command: spawn'
- 'command: help'
- 'command: warp lobby'
```

**格式：** `command: <命令>`

### 3. toast（Toast 通知）

在屏幕右上角显示一个成就风格的通知。

```yaml
- 'toast: type=task;icon=paper;title=<green>登录成功;description=<gray>欢迎回来, %player_name%'
- 'toast: type=goal;icon=emerald;title=<aqua>注册成功;description=<gray>欢迎加入服务器'
- 'toast: type=challenge;icon=nether_star;title=<gold>VIP 欢迎;description=<yellow>尊贵的 VIP 玩家'
```

**格式：** `toast: type=<类型>;icon=<物品>;title=<标题>;description=<描述>`

**参数说明：**

| 参数 | 可选值 | 说明 |
|------|--------|------|
| `type` | `task`、`goal`、`challenge` | 通知框架样式 |
| `icon` | 任意物品 ID | 通知图标（如 `paper`、`emerald`、`diamond`） |
| `title` | 文本 | 通知标题，支持 MiniMessage 颜色 |
| `description` | 文本 | 通知描述，支持 MiniMessage 颜色 |

**Toast 类型对比：**

| 类型 | 外观 |
|------|------|
| `task` | 普通任务完成样式（方框） |
| `goal` | 目标达成样式（圆角框） |
| `challenge` | 挑战完成样式（紫色边框） |

### 4. wait（等待）

暂停指定 tick 数后再执行后续动作。

```yaml
- 'wait: 20'    # 等待 20 tick（1 秒）
- 'wait: 100'   # 等待 100 tick（5 秒）
```

**格式：** `wait: <tick数>`

> 注：20 tick = 1 秒

---

## 变量支持

动作中可以使用以下变量：

| 变量 | 说明 |
|------|------|
| `%player_name%` | 玩家名称 |

如果服务器安装了 PlaceholderAPI，还可以使用所有 PAPI 变量。

---

## 完整配置示例

### 基础示例

```yaml
events:
  login:
    - 'toast: type=task;icon=paper;title=<green>登录成功;description=<gray>欢迎回来'
    - 'command: spawn'
  register:
    - 'toast: type=goal;icon=emerald;title=<aqua>注册成功;description=<gray>欢迎加入'
    - 'console: give %player_name% bread 16'
    - 'command: spawn'
```

### 进阶示例（带延迟）

```yaml
events:
  login:
    - 'console: say &a%player_name% 加入了游戏！'
    - 'toast: type=task;icon=paper;title=<green>登录成功;description=<gray>欢迎回来, %player_name%'
    - 'wait: 20'
    - 'command: spawn'
    - 'wait: 40'
    - 'console: title %player_name% subtitle {"text":"欢迎回来！","color":"green"}'
  register:
    - 'console: say &e新玩家 %player_name% 加入了服务器！'
    - 'toast: type=goal;icon=emerald;title=<aqua>注册成功;description=<gray>欢迎加入服务器, %player_name%'
    - 'console: give %player_name% bread 32'
    - 'console: give %player_name% wooden_sword 1'
    - 'wait: 20'
    - 'command: spawn'
    - 'wait: 60'
    - 'console: title %player_name% title {"text":"欢迎新玩家！","color":"gold"}'
```

### 与权限插件联动

```yaml
events:
  register:
    - 'console: lp user %player_name% parent set member'
    - 'console: lp user %player_name% permission set essentials.spawn true'
    - 'toast: type=goal;icon=emerald;title=<aqua>注册成功;description=<gray>已自动分配 Member 组'
```

---

## 注意事项

1. **动作按顺序执行**：列表中的动作从上到下依次执行
2. **wait 会阻塞后续动作**：`wait` 之后的动作会在等待结束后才执行
3. **命令不需要 `/`**：`command` 和 `console` 中的命令不需要加 `/` 前缀
4. **留空禁用**：将事件列表设为空即可禁用该事件的动作

```yaml
events:
  login: []      # 登录后不执行任何动作
  register: []   # 注册后不执行任何动作
```
