# Permissions

| Permission | Default | Description |
|---|---|---|
| `kalogin.admin` | OP | Use `/kalogin` and `/kl` admin commands |
| `kalogin.changepassword` | All players | Use password change command |
| `kalogin.logout` | All players | Use logout command |
| `kalogin.bindemail` | All players | Use email binding command |
| `kalogin.recoverpassword` | All players | Use password recovery command |
| `kalogin.usercenter` | All players | Use user center command |

## LuckPerms examples

Disable password change for the default group:

```bash
/lp group default permission set kalogin.changepassword false
```

Give the admin group KaLogin admin permission:

```bash
/lp group admin permission set kalogin.admin true
```
