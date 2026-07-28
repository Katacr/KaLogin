# 快速开始

本页介绍如何安装 KaLogin，并完成第一次可用配置。

## 系统要求

| 项目 | 要求 |
|---|---|
| Minecraft | Paper 1.21.7+ |
| Java | Java 21+ |
| 数据库 | SQLite 默认可用；也可使用 MySQL |
| 可选插件 | AuthMe、PlaceholderAPI、Geyser-Spigot |

## 安装步骤

1. 将 KaLogin `.jar` 放入服务器 `plugins` 文件夹。
2. 启动服务器。
3. 插件会自动生成 `plugins/KaLogin/config.yml`、语言文件和 UI 文件。
4. 根据需要修改 `config.yml`。
5. 修改后执行 `/kl reload`，或重启服务器。

## 选择认证模式

首次使用前，请先决定使用哪种认证模式：

| 模式 | 适用场景 |
|---|---|
| AuthMe 模式 | 服务器已经使用 AuthMe，想让 KaLogin 提供登录/注册界面 |
| KaLogin 模式 | 不使用 AuthMe，希望 KaLogin 独立管理账号 |

在 `config.yml` 中设置：

```yaml
use-AuthMe: true
```

设为 `true` 时使用 AuthMe 模式。设为 `false` 时使用 KaLogin 内置认证模式。

## 验证安装

服务器启动后，控制台应出现 KaLogin 启用日志。玩家进入服务器时会自动弹出登录或注册界面。

管理员可以执行：

```bash
/kl reload
```

如果命令正常返回，说明插件已正确加载。

## 推荐首次配置

公网服务器建议优先检查这些配置：

```yaml
login:
  max-login-attempts: 3
  max-accounts-per-ip: 3
  show-auto-login-checkbox: false
```

如果你需要邮箱找回密码，请继续阅读 [邮箱绑定与找回密码](../features/email.md)。
