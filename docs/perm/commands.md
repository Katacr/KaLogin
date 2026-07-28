# 命令列表

## 管理员命令

主命令：

```bash
/kalogin
/kl
```

| 命令 | 说明 |
|---|---|
| `/kl delete <玩家名>` | 删除指定玩家的账号数据 |
| `/kl register <玩家名> <密码>` | 为指定玩家设置或重置密码 |
| `/kl resetterms <玩家名>` | 重置指定玩家的条款确认状态 |
| `/kl resetterms all` | 重置所有玩家的条款确认状态 |
| `/kl reload` | 重载 KaLogin 配置、语言和界面 |

## 玩家命令

| 命令 | 别名 | 说明 |
|---|---|---|
| `/changepassword` | `/cp` | 修改自己的密码 |
| `/logout` | 无 | 登出当前账号 |
| `/bindemail` | 无 | 绑定或换绑邮箱 |
| `/bindemail dismiss` | 无 | 不再提示绑定邮箱 |
| `/recoverpassword` | `/rp` | 通过邮箱验证码找回密码 |
| `/usercenter` | `/uc` | 打开用户中心 |

## AuthMe 模式说明

在 AuthMe 模式下，删除账号和注册账号会调用 AuthMe 的账号系统。条款、邮箱和用户中心仍由 KaLogin 管理。
