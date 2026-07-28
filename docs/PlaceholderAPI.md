# PlaceholderAPI

安装 PlaceholderAPI 后，KaLogin 会注册 `%kalogin_*%` 变量。

## 变量列表

| 变量 | 说明 |
|---|---|
| `%kalogin_email_masked%` | 已脱敏的邮箱地址 |
| `%kalogin_email_plain%` | 明文邮箱地址 |
| `%kalogin_accepted_terms%` | 是否已确认条款 |
| `%kalogin_last_login_ip%` | 最后登录 IP |
| `%kalogin_auto_login_by_ip%` | 是否开启同 IP 自动登录 |
| `%kalogin_register_time%` | 注册时间 |

## 使用场景

这些变量可以用于计分板、聊天格式、Tab 列表、菜单插件等支持 PlaceholderAPI 的插件。

示例：

```text
邮箱: %kalogin_email_masked%
条款: %kalogin_accepted_terms%
```
