# KaLogin

一个强大的 Minecraft Paper 服务器登录插件，提供安全的用户认证和反作弊功能。

## 功能特性

- 🔐 安全的密码加密（使用 bcrypt）
- 🌍 多语言支持（中文/英文）
- 🚫 IP 账号数量限制
- 🤖 相同 IP 自动登录
- 👁️ 登录/注册期间反作弊机制
- ⏱️ 登录/注册超时限制
- 📊 密码强度验证
- 💾 支持 SQLite 和 MySQL 数据库
- 🎨 支持颜色代码 (& 和 §)

## 安装

1. 从 [Releases](https://github.com/Katacr/KaLogin/releases) 下载最新的 `KaLogin-*-all.jar`
2. 将 jar 文件放入服务器的 `plugins` 文件夹
3. 重启服务器

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

### 权限

- `kalogin.admin` - 管理员权限

## 语言文件

语言文件位于 `plugins/KaLogin/lang/` 目录：

- `zh_CN.yml` - 简体中文
- `en_US.yml` - English

你可以修改这些文件来自定义消息内容，支持颜色代码和 MiniMessage 格式。

## 构建

从源码构建：

```bash
./gradlew shadowJar
```

构建产物位于 `build/libs/KaLogin-1.0-all.jar`

## 开发

### 环境要求

- JDK 21
- Kotlin 2.3.20

### 运行测试服务器

```bash
./gradlew runServer
```

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！
