# Commands

## Admin commands

Main commands:

```bash
/kalogin
/kl
```

| Command | Description |
|---|---|
| `/kl delete <player>` | Delete account data for a player |
| `/kl register <player> <password>` | Set or reset password for a player |
| `/kl resetterms <player>` | Reset terms confirmation for one player |
| `/kl resetterms all` | Reset terms confirmation for all players |
| `/kl reload` | Reload KaLogin configuration and language files (UI files are read on each dialog open, no reload needed) |

## Player commands

| Command | Alias | Description |
|---|---|---|
| `/changepassword` | `/cp` | Change your password |
| `/logout` | None | Log out of the current account |
| `/bindemail` | None | Bind or change email |
| `/bindemail dismiss` | None | Stop the email binding reminder |
| `/recoverpassword` | `/rp` | Recover password by email code |
| `/usercenter` | `/uc` | Open the user center |

## AuthMe mode notes

In AuthMe mode, account delete and register commands use AuthMe account data. Terms, email, and user center data are still managed by KaLogin.
