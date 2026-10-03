# config.yml

KaLogin main configuration file is:

```text
plugins/KaLogin/config.yml
```

After editing it, run `/kl reload`. Restart the server after changing database type, authentication mode, or dependency plugins.

## Basic settings

```yaml
language: "en_US"
use-AuthMe: true
check-update: true
```

| Option | Description |
|---|---|
| `language` | Message language. Built in values are `zh_CN` and `en_US` |
| `use-AuthMe` | Use AuthMe as the authentication backend |
| `check-update` | Check for updates when OP players join |

## Database

SQLite works by default:

```yaml
database:
  type: "sqlite"
  sqlite:
    file_name: "data.db"
```

Use MySQL if multiple servers need shared account data:

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

| Option | Description |
|---|---|
| `database.type` | Storage type, `sqlite` or `mysql` |
| `database.sqlite.file_name` | SQLite database file name |
| `database.mysql.host` | MySQL host |
| `database.mysql.port` | MySQL port |
| `database.mysql.database` | MySQL database name |
| `database.mysql.username` | MySQL username |
| `database.mysql.password` | MySQL password |
| `database.mysql.params` | JDBC connection parameters |

## Password policy

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

| Option | Description |
|---|---|
| `min-password-length` | Minimum password length |
| `max-password-length` | Maximum password length |
| `password-regex` | Allowed password characters |
| `password-blacklist` | Text that cannot appear in passwords |
| `has-uppercase` | Require uppercase letters |
| `has-lowercase` | Require lowercase letters |
| `has-symbol` | Require symbols |
| `has-number` | Require numbers |
| `max-consecutive-same-digits` | Maximum repeated same digit count. `0` disables this limit |

## Login and registration limits

```yaml
login:
  register-timeout: 90
  login-timeout: 90
  max-login-attempts: 3
  max-accounts-per-ip: 3
  show-auto-login-checkbox: true
  dialog-delay-ticks: 1
```

| Option | Description |
|---|---|
| `register-timeout` | Registration timeout in seconds |
| `login-timeout` | Login timeout in seconds |
| `max-login-attempts` | Maximum wrong password attempts before kick |
| `max-accounts-per-ip` | Maximum accounts registered from one IP. `0` disables this limit |
| `show-auto-login-checkbox` | Show the same IP auto login checkbox |
| `dialog-delay-ticks` | Delay before opening the login or registration screen |

For public servers, disabling same IP auto login is recommended:

```yaml
login:
  show-auto-login-checkbox: false
```

## Change password

```yaml
change-password:
  max-attempts: 3
```

| Option | Description |
|---|---|
| `max-attempts` | Maximum old password failures during password change |

## Welcome terms

```yaml
welcome-dialog:
  enabled: true
```

| Option | Description |
|---|---|
| `enabled` | Enable the welcome and terms confirmation screen |

Terms text is edited in `plugins/KaLogin/ui/welcome.yml`. See [Welcome and Terms](../features/welcome.md).

## Error prompt type

```yaml
error-prompt-type: "body"
```

| Value | Effect |
|---|---|
| `none` | Do not show error prompts |
| `body` | Show errors inside the screen body |
| `toast` | Show errors as Toast prompts; Spigot falls back to body text |

## Input settings

Input width, height, labels, and defaults are configured under `inputs`.

| Property | Description |
|---|---|
| `width` | Input width |
| `height` | Input height. Use `-1` to disable multiline mode |
| `labelVisible` | Show the input label |
| `initial` | Default value or default checkbox state |

Common input paths:

| Path | Use |
|---|---|
| `inputs.login.login_password` | Login password input |
| `inputs.login.auto_login_by_ip` | Same IP auto login checkbox |
| `inputs.register.reg_password` | Registration password input |
| `inputs.register.reg_confirm_password` | Registration confirm password input |
| `inputs.change-password.old_password` | Old password input |
| `inputs.change-password.new_password` | New password input |
| `inputs.change-password.confirm_new_password` | Confirm new password input |
| `inputs.bind-email.email` | Email input |
| `inputs.bind-email.code` | Email code input |
| `inputs.recover-password.code` | Recovery code input |
| `inputs.recover-password.new_password` | Recovery new password input |
| `inputs.recover-password.confirm_new_password` | Recovery confirm password input |
| `inputs.welcome.accept_terms` | Terms confirmation checkbox |

## Email binding

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

| Option | Description |
|---|---|
| `enabled` | Enable email binding and password recovery |
| `code-expire-seconds` | Verification code lifetime in seconds |
| `smtp.host` | SMTP server host |
| `smtp.port` | SMTP server port |
| `smtp.auth` | Use SMTP authentication |
| `smtp.starttls` | Use STARTTLS |
| `smtp.ssl` | Use SSL |
| `smtp.username` | SMTP username |
| `smtp.password` | SMTP password or app password |
| `smtp.from-email` | Sender email address |
| `smtp.from-name` | Sender display name |

See [Email Binding and Password Recovery](../features/email.md).

## Cross-server login session

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

| Option | Description |
|---|---|
| `proxy.enabled` | Enable the cross-server login session |
| `proxy.server-name` | This backend's registered proxy name (must match velocity.toml); every backend must use its own actual name and must not copy Lobby's value. Empty means no recording |
| `proxy.require-proxy` | Reject entry when the proxy is unavailable |
| `proxy.query-timeout-ms` | Session query response timeout (ms) |
| `proxy.query-delay-ticks` | Ticks to wait after join before querying |
| `proxy.restore-welcome` | Show the welcome screen on restore based on the terms state |
| `proxy.restore-email-prompt` | Repeat the email prompt on restore |
| `proxy.replay-login-actions` | Replay `events.login` on restore |

See [Group Servers and Cross-Server Login](../features/proxy.md).

## Last server and position

```yaml
last-seen:
  enabled: true
  blacklist:
    - pve
    - pvp
```

| Option | Description |
|---|---|
| `last-seen.enabled` | Return to the last server and position after login (requires `proxy.enabled`) |
| `last-seen.blacklist` | Backends where no position is recorded (must match `proxy.server-name`/the proxy registration name); leaving one stores no coordinates, so the next login starts at the default server's spawn |

See [Group Servers and Cross-Server Login](../features/proxy.md).

## Login and registration actions

```yaml
events:
  login:
    - 'console: say Player %player_name% logged in!'
    - 'tell: <green>Login successful <gray>Welcome back, %player_name%'
    - 'wait: 20'
    - 'command: spawn'
  register:
    - 'console: say Player %player_name% registered!'
    - 'tell: <aqua>Registration successful <gray>Welcome, %player_name%'
    - 'wait: 20'
    - 'command: help'
```

See [Login and Registration Actions](../features/actions.md).
