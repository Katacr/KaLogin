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

UI files are read from disk each time a screen opens, so `/kl reload` is not required. If the screen is already open, have the player rejoin or trigger it again.

If you changed authentication mode, database, or dependency plugins, restart the server.

## Should public servers enable same IP auto login?

Usually no. On public networks, the same IP does not always mean the same device. Recommended setting:

```yaml
login:
  show-auto-login-checkbox: false
```

## Players still have to log in again after switching servers?

Check each of the following:

- KaProxy is installed on the proxy with `modules.kalogin` enabled;
- every backend sets `proxy.enabled: true` and connects to the same MySQL;
- in AuthMe mode, every backend's AuthMe shares the same database as well;
- the session is bound to the login IP (KaProxy's `bind-ip`), so a changed IP refuses the restore;
- when the proxy is unavailable, `proxy.require-proxy` decides between kicking the player and falling back to the local flow; the default is to fall back.

## The player is not returned to the last position after login?

- `last-seen.enabled` defaults to `true` but requires `proxy.enabled`; both must be on;
- `proxy.server-name` must be this backend's real proxy registration name (matching `velocity.toml`); backends in the same group must not copy each other's value;
- servers listed in `last-seen.blacklist` (`pve` and `pvp` by default) store no coordinates, so the next login starts at the default server's spawn;
- if the stored world does not exist or the coordinates are out of bounds, the proxy falls back to `default-server`;
- the return mode is decided by the proxy's KaProxy `modules.lastseen.mode` (`direct` / `manual` / `auto`); KaLogin needs no mode setting.

## I did not receive the "another device is trying to log in" alert?

- The alert is produced by the proxy: KaProxy needs `modules.kalogin.security-alert` enabled, and it is only raised when the conflicting login hits an account that already has an authenticated session;
- it is delivered only to the still-online, already authenticated owner, is rate-limited per player UUID (600 seconds by default), and never shows the attacker's IP;
- BungeeCord has no matching conflicting-login status, so the alert is only produced on Velocity;
- the temporary failed-login IP ban is controlled by the proxy's `modules.kalogin.ip-ban`; KaLogin reports the source IP on every wrong password.

## The new KaProxy configuration did not take effect after the update?

KaProxy does not merge new configuration nodes into an existing `config.yml`; it only writes the default file when the configuration does not exist. After the update, add the `modules.kalogin`, `modules.lastseen` and `database` sections manually. New KaLogin keys are added automatically after `/kl reload` or a restart.
