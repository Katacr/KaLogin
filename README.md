# KaLogin

一个强大的 Minecraft Paper 服务器登录插件，提供安全的用户认证和反作弊功能。

## 支持版本
Paper 1.21.7 及以上

## 功能特性

- 🔐 安全的密码加密（使用 bcrypt）
- 🌍 多语言支持（中文/英文）
- 🚫 IP 账号数量限制
- 🤖 相同 IP 自动登录
- 👁️ 登录/注册期间反作弊机制
- ⏱️ 登录/注册超时限制
- 📊 密码强度验证
- 💾 支持 SQLite 和 MySQL 数据库

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
  register-timeout: 90
  login-timeout: 60
  max-login-attempts: 3
  auto-login-by-ip: false
  max-accounts-per-ip: 3
```


## 命令

- `/kalogin` 或 `/kl` - 主命令
  - `/kl delete <玩家>` - 删除玩家数据
  - `/kl register <玩家> <密码>` - 为玩家设置密码
  - `/kl reload` - 重载配置文件


