# 配置文件: config.yml

KaLogin 的主配置文件位于：

```text
plugins/KaLogin/config.yml
```

修改后可以执行 `/kl reload` 重载。数据库类型、认证模式或依赖插件变更时，建议完整重启服务器。

## 基础配置

```yaml
language: "zh_CN"
use-AuthMe: true
check-update: true
```

| 配置项 | 说明 |
|---|---|
| `language` | 插件消息语言。默认支持 `zh_CN` 和 `en_US` |
| `use-AuthMe` | 是否使用 AuthMe 作为认证后端 |
| `check-update` | OP 玩家进入服务器时是否检查新版本 |

## 数据库

默认使用 SQLite，无需额外安装数据库：

```yaml
database:
  type: "sqlite"
  sqlite:
    file_name: "data.db"
```

多个服务器需要共用账号数据时，可以改用 MySQL：

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

| 配置项 | 说明 |
|---|---|
| `database.type` | 存储类型，可选 `sqlite` 或 `mysql` |
| `database.sqlite.file_name` | SQLite 数据库文件名 |
| `database.mysql.host` | MySQL 地址 |
| `database.mysql.port` | MySQL 端口 |
| `database.mysql.database` | MySQL 数据库名 |
| `database.mysql.username` | MySQL 用户名 |
| `database.mysql.password` | MySQL 密码 |
| `database.mysql.params` | JDBC 连接参数 |

## 密码策略

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

| 配置项 | 说明 |
|---|---|
| `min-password-length` | 密码最小长度 |
| `max-password-length` | 密码最大长度 |
| `password-regex` | 允许的密码字符范围 |
| `password-blacklist` | 不允许出现在密码中的内容 |
| `has-uppercase` | 是否必须包含大写字母 |
| `has-lowercase` | 是否必须包含小写字母 |
| `has-symbol` | 是否必须包含符号 |
| `has-number` | 是否必须包含数字 |
| `max-consecutive-same-digits` | 不允许连续相同数字的数量，`0` 表示不限制 |

## 登录与注册限制

```yaml
login:
  register-timeout: 90
  login-timeout: 90
  max-login-attempts: 3
  max-accounts-per-ip: 3
  show-auto-login-checkbox: true
  dialog-delay-ticks: 1
```

| 配置项 | 说明 |
|---|---|
| `register-timeout` | 玩家注册超时时间，单位秒 |
| `login-timeout` | 玩家登录超时时间，单位秒 |
| `max-login-attempts` | 密码错误次数上限，超过后踢出 |
| `max-accounts-per-ip` | 同一 IP 最多注册账号数量，`0` 表示不限制 |
| `show-auto-login-checkbox` | 是否显示同 IP 自动登录勾选框 |
| `dialog-delay-ticks` | 玩家进入服务器后延迟多少 tick 弹出界面 |

公网服务器建议关闭同 IP 自动登录：

```yaml
login:
  show-auto-login-checkbox: false
```

## 修改密码

```yaml
change-password:
  max-attempts: 3
```

| 配置项 | 说明 |
|---|---|
| `max-attempts` | 修改密码时旧密码验证失败次数上限 |

## 欢迎和条款确认

```yaml
welcome-dialog:
  enabled: true
```

| 配置项 | 说明 |
|---|---|
| `enabled` | 是否启用欢迎和条款确认界面 |

条款正文在 `plugins/KaLogin/ui/welcome.yml` 中修改。详见 [欢迎和条款确认](../features/welcome.md)。

## 错误提示方式

```yaml
error-prompt-type: "body"
```

| 值 | 效果 |
|---|---|
| `none` | 不显示错误提示 |
| `body` | 在界面正文中显示错误 |
| `toast` | 使用 Toast 提示；Spigot 自动回退为正文提示 |

## 输入框设置

输入框宽度、高度、标签显示和默认值在 `inputs` 中设置。

| 属性 | 说明 |
|---|---|
| `width` | 输入框宽度 |
| `height` | 输入框高度，设为 `-1` 可禁用多行模式 |
| `labelVisible` | 是否显示输入框标签 |
| `initial` | 默认值或默认勾选状态 |

常用输入框位置：

| 位置 | 用途 |
|---|---|
| `inputs.login.login_password` | 登录密码输入框 |
| `inputs.login.auto_login_by_ip` | 同 IP 自动登录勾选框 |
| `inputs.register.reg_password` | 注册密码输入框 |
| `inputs.register.reg_confirm_password` | 注册确认密码输入框 |
| `inputs.change-password.old_password` | 旧密码输入框 |
| `inputs.change-password.new_password` | 新密码输入框 |
| `inputs.change-password.confirm_new_password` | 确认新密码输入框 |
| `inputs.bind-email.email` | 邮箱输入框 |
| `inputs.bind-email.code` | 邮箱验证码输入框 |
| `inputs.recover-password.code` | 找回密码验证码输入框 |
| `inputs.recover-password.new_password` | 找回密码的新密码输入框 |
| `inputs.recover-password.confirm_new_password` | 找回密码确认密码输入框 |
| `inputs.welcome.accept_terms` | 条款确认勾选框 |

## 邮箱绑定

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

| 配置项 | 说明 |
|---|---|
| `enabled` | 是否启用邮箱绑定和找回密码 |
| `code-expire-seconds` | 验证码有效时间，单位秒 |
| `smtp.host` | SMTP 服务器地址 |
| `smtp.port` | SMTP 端口 |
| `smtp.auth` | 是否需要认证 |
| `smtp.starttls` | 是否启用 STARTTLS |
| `smtp.ssl` | 是否启用 SSL |
| `smtp.username` | SMTP 用户名 |
| `smtp.password` | SMTP 密码或授权码 |
| `smtp.from-email` | 发件邮箱 |
| `smtp.from-name` | 发件人显示名称 |

详见 [邮箱绑定与找回密码](../features/email.md)。

## 群组登录会话

```yaml
proxy:
  enabled: false
  server-name: ""
  require-proxy: false
  query-timeout-ms: 5000
  query-delay-ticks: 3
  restore-welcome: true
  restore-email-prompt: false
  replay-login-actions: false
```

| 配置项 | 说明 |
|---|---|
| `proxy.enabled` | 是否启用群组登录会话 |
| `proxy.server-name` | 本服在代理中的注册名（须与 velocity.toml 一致），用于写上次位置；每台后端必须各自填写其真实名称，不能由 Lobby 配置复制；留空不记录 |
| `proxy.require-proxy` | 代理不可用时是否拒绝进入 |
| `proxy.query-timeout-ms` | 会话查询应答超时(毫秒) |
| `proxy.query-delay-ticks` | 进服后延迟查询的 tick 数 |
| `proxy.restore-welcome` | 跨服恢复时是否按条款状态弹欢迎 |
| `proxy.restore-email-prompt` | 跨服恢复时是否重复邮箱提示 |
| `proxy.replay-login-actions` | 跨服恢复时是否重放 `events.login` |

详见 [群组服务器与跨服登录](../features/proxy.md)。

## 上次下线位置

```yaml
last-seen:
  enabled: true
  blacklist:
    - pve
    - pvp
```

| 配置项 | 说明 |
|---|---|
| `last-seen.enabled` | 登录完成后是否自动回到上次下线的子服与坐标（依赖 `proxy.enabled`） |
| `last-seen.blacklist` | 不记录位置的子服名列表（须与 `proxy.server-name`/代理注册名一致）；从这些子服退服时不写坐标，下次登录回默认服出生点 |

详见 [群组服务器与跨服登录](../features/proxy.md)。

## 登录/注册后动作

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

详见 [登录/注册后动作](../features/actions.md)。
