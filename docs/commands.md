# 命令与权限

KaLogin 提供管理命令和玩家命令，通过权限节点控制访问。

---

## 管理命令

主命令：`/kalogin`（别名：`/kl`）

| 命令 | 说明 | 用法 |
|------|------|------|
| `/kl delete <玩家名>` | 删除指定玩家的注册数据 | 玩家在线时会被踢出并要求重新注册 |
| `/kl register <玩家名> <密码>` | 为指定玩家设置/重置密码 | 密码需符合密码策略要求 |
| `/kl resetterms <玩家名\|all>` | 重置玩家的条款阅读状态 | 使用 `all` 重置所有玩家 |
| `/kl reload` | 重载配置文件 | 重载 config.yml、语言文件和 UI 文件 |

---

## 玩家命令

| 命令 | 别名 | 说明 |
|------|------|------|
| `/changepassword` | `/cp` | 打开修改密码对话框 |
| `/logout` | — | 登出当前账号（踢出后需重新登录） |
| `/bindemail` | — | 打开邮箱绑定对话框 |
| `/bindemail dismiss` | — | 关闭邮箱绑定提醒 |
| `/recoverpassword` | `/rp` | 通过邮箱验证码找回密码 |
| `/usercenter` | `/uc` | 打开用户中心（查看账号信息） |

---

## 权限节点

| 权限节点 | 默认值 | 说明 |
|----------|--------|------|
| `kalogin.admin` | OP | 允许使用 KaLogin 管理命令（`/kl`） |
| `kalogin.changepassword` | 所有玩家 | 允许使用修改密码命令 |
| `kalogin.logout` | 所有玩家 | 允许使用登出命令 |
| `kalogin.bindemail` | 所有玩家 | 允许使用邮箱绑定命令 |
| `kalogin.recoverpassword` | 所有玩家 | 允许使用邮箱找回密码命令 |
| `kalogin.usercenter` | 所有玩家 | 允许使用用户中心命令 |

---

## 权限配置示例

### 使用 LuckPerms 限制命令

```bash
# 禁止默认组使用修改密码
/lp group default permission set kalogin.changepassword false

# 允许 VIP 组使用邮箱绑定
/lp group vip permission set kalogin.bindemail true

# 给予指定玩家管理权限
/lp user Steve permission set kalogin.admin true
```

### 使用 permissions.yml（Bukkit 原生）

```yaml
groups:
  default:
    permissions:
      kalogin.changepassword: true
      kalogin.logout: true
      kalogin.bindemail: true
      kalogin.recoverpassword: true
      kalogin.usercenter: true
  admin:
    permissions:
      kalogin.admin: true
```

---

## AuthMe 模式下的命令

当 `use-AuthMe: true` 时，管理命令的行为会有所不同：

| 命令 | AuthMe 模式行为 |
|------|-----------------|
| `/kl delete <玩家>` | 调用 AuthMe API 删除玩家数据 |
| `/kl register <玩家> <密码>` | 调用 AuthMe API 为玩家设置密码 |
| `/kl resetterms <玩家\|all>` | 仅重置 KaLogin 的条款状态（不影响 AuthMe） |
| `/kl reload` | 仅重载 KaLogin 配置（不影响 AuthMe） |

---

## PlaceholderAPI 变量

KaLogin 提供以下 PAPI 变量（需安装 PlaceholderAPI）：

| 变量 | 说明 |
|------|------|
| `%kalogin_logged_in%` | 玩家是否已登录（`true`/`false`） |
| `%kalogin_registered%` | 玩家是否已注册（`true`/`false`） |
| `%kalogin_email_bound%` | 玩家是否已绑定邮箱（`true`/`false`） |

可在计分板、Tab 列表、聊天格式等支持 PAPI 的插件中使用。
