# Email Binding and Password Recovery

KaLogin can let players bind an email address and recover their password with a verification code.

## Enable email features

```yaml
email-binding:
  enabled: true
  code-expire-seconds: 300
```

## Configure SMTP

Fill in your mail server settings in `config.yml`:

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

Different email providers use different SMTP hosts, ports, and app passwords. Check your provider settings. Many providers do not allow using the normal login password for SMTP.

## Player commands

| Command | Use |
|---|---|
| `/bindemail` | Open the email binding screen |
| `/bindemail dismiss` | Stop the email binding reminder |
| `/recoverpassword` | Recover password by email code |
| `/rp` | Alias of `/recoverpassword` |

## Tips

| Situation | Check |
|---|---|
| Codes are not received | SMTP host, port, username, password, and spam folder |
| SMTP send fails | Whether the server can connect to the SMTP port |
| Public server setup | Ask players to bind email early |

Test email sending with your own account before relying on password recovery.
