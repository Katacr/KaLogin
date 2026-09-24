# 常见问题

## 开启 AuthMe 模式后为什么回退到 KaLogin 模式？

请确认 AuthMe `.jar` 已放入 `plugins` 文件夹，并且服务器启动日志中 AuthMe 已正常启用。

## 为什么玩家没有收到邮箱验证码？

请检查：

- SMTP 地址、端口、账号和授权码是否正确
- 服务器是否允许连接 SMTP 端口
- 邮件是否进入垃圾箱
- `email-binding.enabled` 是否为 `true`

## 为什么登录/注册界面没有弹出？

可以尝试把 `dialog-delay-ticks` 调大，例如：

```yaml
login:
  dialog-delay-ticks: 5
```

如果仍然无效，请检查服务器版本是否满足要求。

## 修改 UI 后没有生效怎么办？

界面文件在每次弹出界面时从磁盘读取，无需执行 `/kl reload`。如果界面已经打开，请让玩家退出重进，或再次触发对应界面。

如果修改的是认证模式、数据库或插件依赖，建议完整重启服务器。

## 公网服务器是否建议开启同 IP 自动登录？

不建议。公网环境下，同 IP 不一定代表同一台设备。建议设置：

```yaml
login:
  show-auto-login-checkbox: false
```
