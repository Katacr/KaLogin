# 邮箱绑定与找回密码

KaLogin 可以让玩家绑定邮箱，并通过验证码找回密码。

## 开启邮箱功能

```yaml
email-binding:
  enabled: true
  code-expire-seconds: 300
```

## 配置 SMTP

在 `config.yml` 中填写邮箱服务信息：

```yaml
email-binding:
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

不同邮箱服务商的 SMTP 地址和授权码不同，请以邮箱服务商后台说明为准。很多邮箱不能直接使用登录密码，需要单独生成 SMTP 授权码。

## 玩家使用方式

| 命令 | 用途 |
|---|---|
| `/bindemail` | 打开邮箱绑定界面 |
| `/bindemail dismiss` | 不再提示绑定邮箱 |
| `/recoverpassword` | 通过邮箱验证码找回密码 |
| `/rp` | `/recoverpassword` 的别名 |

## 建议

- 启用邮箱找回密码前，请先用自己的账号测试验证码是否能收到。
- 公网服务器建议提醒玩家尽早绑定邮箱。
- 如果 SMTP 发送失败，请先检查服务器是否允许连接邮箱服务商端口。
