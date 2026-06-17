# 主配置文件 (config.yml)

KaLogin 的主配置文件位于 `plugins/KaLogin/config.yml`，首次启动时自动生成。

---

## 基础设置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `config-version` | int | 10 | 配置文件版本号，**请勿手动修改** |
| `language` | string | `zh_CN` | 语言设置，可选 `zh_CN`（简体中文）或 `en_US`（English） |
| `use-AuthMe` | boolean | `true` | 是否使用 AuthMe 作为登录验证后端。设为 `true` 时 KaLogin 仅作为 AuthMe 的 UI 前端；设为 `false` 时使用 KaLogin 内置认证系统 |
| `check-update` | boolean | `true` | OP 玩家进入服务器时是否检查 GitHub 上的新版本 |

---

## 数据库配置 (`database`)

KaLogin 支持 SQLite 和 MySQL 两种存储方式。

### SQLite（默认）

```yaml
database:
  type: "sqlite"
  sqlite:
    file_name: "data.db"
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `type` | string | `sqlite` | 存储类型，可选 `sqlite` 或 `mysql` |
| `sqlite.file_name` | string | `data.db` | SQLite 数据库文件名，存放在插件目录下 |

### MySQL

```yaml
database:
  type: "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "kalogin"
    username: "root"
    password: "password"
    params: "?useSSL=false&serverTimezone=UTC"
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `mysql.host` | string | `localhost` | MySQL 服务器地址 |
| `mysql.port` | int | `3306` | MySQL 端口 |
| `mysql.database` | string | `kalogin` | 数据库名称 |
| `mysql.username` | string | `root` | 数据库用户名 |
| `mysql.password` | string | `password` | 数据库密码 |
| `mysql.params` | string | `?useSSL=false&serverTimezone=UTC` | JDBC 连接参数 |

---

## 密码策略 (`settings`)

控制玩家注册时的密码强度要求。

```yaml
settings:
  min-password-length: 6
  max-password-length: 20
  password-regex: "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`]+$"
  password-blacklist:
    - "PASSWORD"
    - "password"
    - "123456"
    - "12345678"
  has-uppercase: false
  has-lowercase: false
  has-symbol: false
  has-number: false
  max-consecutive-same-digits: 3
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `min-password-length` | int | `6` | 密码最小长度 |
| `max-password-length` | int | `20` | 密码最大长度 |
| `password-regex` | string | 见上 | 密码允许的字符正则表达式 |
| `password-blacklist` | list | 见上 | 密码黑名单列表，包含这些字符串的密码将被拒绝 |
| `has-uppercase` | boolean | `false` | 是否必须包含大写字母 |
| `has-lowercase` | boolean | `false` | 是否必须包含小写字母 |
| `has-symbol` | boolean | `false` | 是否必须包含符号 |
| `has-number` | boolean | `false` | 是否必须包含数字 |
| `max-consecutive-same-digits` | int | `3` | 不允许连续 N 个相同数字，`0` 表示不限制 |

---

## 登录设置 (`login`)

```yaml
login:
  register-timeout: 90
  login-timeout: 90
  max-login-attempts: 3
  max-accounts-per-ip: 3
  show-auto-login-checkbox: true
  dialog-delay-ticks: 1
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `register-timeout` | int | `90` | 注册超时时间（秒），超时后踢出玩家 |
| `login-timeout` | int | `90` | 登录超时时间（秒），超时后踢出玩家 |
| `max-login-attempts` | int | `3` | 密码错误最大次数，超过后踢出玩家 |
| `max-accounts-per-ip` | int | `3` | 每个 IP 最多注册的账号数量，`0` 表示不限制 |
| `show-auto-login-checkbox` | boolean | `true` | 是否在登录界面显示"同IP自动登录"勾选框。**公网环境建议关闭** |
| `dialog-delay-ticks` | int | `1` | 进入服务器后延迟多少 tick 显示登录/注册界面。用于防止其他插件或网络延迟导致无法正常弹出界面 |

---

## 修改密码设置 (`change-password`)

```yaml
change-password:
  max-attempts: 3
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `max-attempts` | int | `3` | 旧密码验证最大失败次数，超过后取消修改操作 |

---

## 欢迎对话框 (`welcome-dialog`)

```yaml
welcome-dialog:
  enabled: true
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用欢迎/服务器条款对话框。启用后新玩家首次登录需确认条款 |

---

## 错误提示方式 (`error-prompt-type`)

```yaml
error-prompt-type: "body"
```

| 可选值 | 说明 |
|--------|------|
| `none` | 不显示任何错误提示 |
| `body` | 在对话框 Body 中显示错误提示（默认） |
| `toast` | 使用 Toast 弹出错误提示，不改动对话框布局 |

---

## 输入框自定义属性 (`inputs`)

可以自定义各对话框中输入框的外观和默认值。

### 通用属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `width` | int | `200` | 输入框宽度（1-1024） |
| `height` | int | `17` | 输入框高度（1-512）。设为 `-1` 则禁用多行模式 |
| `labelVisible` | boolean | `true` | 是否显示输入框标签文本 |
| `initial` | string | `''` | 输入框内的初始值 |

### 支持的对话框和输入框

**登录对话框 (`inputs.login`)**
- `login_password` — 密码输入框
- `auto_login_by_ip` — 同IP自动登录勾选框（`initial` 为 `true`/`false`）

**注册对话框 (`inputs.register`)**
- `reg_password` — 密码输入框
- `reg_confirm_password` — 确认密码输入框

**修改密码对话框 (`inputs.change-password`)**
- `old_password` — 旧密码输入框
- `new_password` — 新密码输入框
- `confirm_new_password` — 确认新密码输入框

**邮箱绑定对话框 (`inputs.bind-email`)**
- `email` — 邮箱输入框（默认宽度 240）
- `code` — 验证码输入框（默认宽度 160）

**密码找回对话框 (`inputs.recover-password`)**
- `code` — 验证码输入框（默认宽度 160）
- `new_password` — 新密码输入框
- `confirm_new_password` — 确认新密码输入框

**欢迎对话框 (`inputs.welcome`)**
- `accept_terms` — 条款确认勾选框（`initial` 为 `true`/`false`）

---

## 邮箱绑定 (`email-binding`)

```yaml
email-binding:
  enabled: true
  code-expire-seconds: 300
  smtp:
    host: "smtp.qq.com"
    port: 587
    auth: true
    starttls: true
    ssl: true
    username: "xxx@qq.com"
    password: "xxx"
    from-email: "xxx@qq.com"
    from-name: "Server"
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用邮箱绑定功能 |
| `code-expire-seconds` | int | `300` | 验证码过期时间（秒） |
| `smtp.host` | string | `smtp.qq.com` | SMTP 服务器地址 |
| `smtp.port` | int | `587` | SMTP 端口 |
| `smtp.auth` | boolean | `true` | 是否需要 SMTP 认证 |
| `smtp.starttls` | boolean | `true` | 是否启用 STARTTLS |
| `smtp.ssl` | boolean | `true` | 是否启用 SSL |
| `smtp.username` | string | — | SMTP 登录用户名（通常为邮箱地址） |
| `smtp.password` | string | — | SMTP 登录密码（QQ邮箱需使用授权码） |
| `smtp.from-email` | string | — | 发件人邮箱地址 |
| `smtp.from-name` | string | `Server` | 发件人显示名称 |

### 常见邮箱 SMTP 配置

| 邮箱 | host | port | 备注 |
|------|------|------|------|
| QQ 邮箱 | `smtp.qq.com` | `587` | 需在 QQ 邮箱设置中开启 SMTP 并获取授权码 |
| 163 邮箱 | `smtp.163.com` | `465` | 需开启 SMTP 服务并设置客户端授权密码 |
| Gmail | `smtp.gmail.com` | `587` | 需开启"应用专用密码" |

---

## 事件动作 (`events`)

登录/注册成功后自动执行的动作列表。详见 [事件动作系统文档](events.md)。

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
