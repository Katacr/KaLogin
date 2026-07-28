# 权限列表

| 权限 | 默认 | 说明 |
|---|---|---|
| `kalogin.admin` | OP | 使用 `/kalogin` 和 `/kl` 管理命令 |
| `kalogin.changepassword` | 所有玩家 | 使用修改密码命令 |
| `kalogin.logout` | 所有玩家 | 使用登出命令 |
| `kalogin.bindemail` | 所有玩家 | 使用邮箱绑定命令 |
| `kalogin.recoverpassword` | 所有玩家 | 使用邮箱找回密码命令 |
| `kalogin.usercenter` | 所有玩家 | 使用用户中心命令 |

## LuckPerms 示例

禁止默认组使用修改密码：

```bash
/lp group default permission set kalogin.changepassword false
```

给管理员组 KaLogin 管理权限：

```bash
/lp group admin permission set kalogin.admin true
```
