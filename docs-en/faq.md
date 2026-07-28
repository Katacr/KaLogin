# FAQ

## Why does AuthMe mode fall back to KaLogin mode?

Make sure the AuthMe `.jar` file is in the `plugins` folder and AuthMe is enabled successfully during server startup.

## Why do players not receive email codes?

Check:

| Item | What to check |
|---|---|
| SMTP settings | Host, port, username, password or app password |
| Network | Whether the server can connect to the SMTP port |
| Mailbox | Spam folder or provider filtering |
| KaLogin config | `email-binding.enabled` is `true` |

## Why does the login or registration screen not open?

Try increasing `dialog-delay-ticks`:

```yaml
login:
  dialog-delay-ticks: 5
```

If it still does not open, check that your server version meets the requirement.

## Why did my UI changes not apply?

Run:

```bash
/kl reload
```

If you changed authentication mode, database, or dependency plugins, restart the server.

## Should public servers enable same IP auto login?

Usually no. On public networks, the same IP does not always mean the same device. Recommended setting:

```yaml
login:
  show-auto-login-checkbox: false
```
