# Group Servers and Cross-Server Login

KaLogin can share login state across a proxy network (Velocity / BungeeCord) through KaProxy: after a player logs in on any backend, switching to another backend does not require logging in again.

## Requirements

- KaProxy is installed on the proxy with `modules.kalogin` enabled (see the KaProxy documentation).
- Every backend installs KaLogin and sets `proxy.enabled: true`.
- Every backend shares the same MySQL database.
- In AuthMe mode, each backend's AuthMe must also share the same database.

## Configuration

```yaml
proxy:
  enabled: false
  server-name: ""
  require-proxy: false
  query-timeout-ms: 5000
  query-delay-ticks: 3
  restore-welcome: true
  restore-email-prompt: false
  replay-login-actions: false

last-seen:
  enabled: true
  blacklist:
    - pve
    - pvp
```

| Option | Default | Description |
|--------|---------|-------------|
| `proxy.enabled` | `false` | Enables the cross-server login session. |
| `proxy.server-name` | empty | This backend's registered proxy name (must match velocity.toml); used for last-position writes. Empty means no recording. |
| `proxy.require-proxy` | `false` | Reject entry when the proxy is unavailable; when `false`, fall back to local login/registration. |
| `proxy.query-timeout-ms` | `5000` | Response timeout for the session query (ms); a timeout is treated as proxy unavailable. |
| `proxy.query-delay-ticks` | `3` | Ticks to wait after join before sending the query, avoiding packet loss during the configuration phase. |
| `proxy.restore-welcome` | `true` | On restore, still show the welcome screen depending on the stored terms state. |
| `proxy.restore-email-prompt` | `false` | Repeat the email binding prompt on cross-server restore. |
| `proxy.replay-login-actions` | `false` | Replay `events.login` actions on cross-server restore. |
| `last-seen.enabled` | `true` | Return to the last server and position after login (requires `proxy.enabled`). |
| `last-seen.blacklist` | `[pve, pvp]` | Backends where no position is recorded (must match `proxy.server-name`/the proxy registration name); leaving one of them stores no coordinates, so the next login starts at the default server's spawn. |

## How It Works

1. On login/registration success, KaLogin reports the session to KaProxy.
2. When the player switches backends, the destination backend queries the session.
3. If KaProxy finds a session with a matching IP, it returns authenticated and the backend restores the login state; otherwise it falls back to local login/registration.
4. When the player truly disconnects, KaProxy destroys the session; backend switches do not.
5. When the player runs `/logout`, KaProxy invalidates the session and disconnects the player from the whole network.

## Behavior Notes

- The session is bound to the login IP (controlled by KaProxy's `bind-ip`); restore is rejected when the IP changes.
- In AuthMe mode the destination backend restores the AuthMe login state, so all AuthMe databases must be consistent.
- When the proxy is unavailable and `require-proxy: true`, the player is kicked; otherwise KaLogin falls back to the local flow.

## Returning to the Last Position

With `last-seen.enabled`, a player returns to the server and coordinates where they last logged off, instead of always starting on the default server (for example, Lobby):

1. When the player leaves or switches a backend, KaLogin writes the current coordinates **and the backend name atomically into the shared database** `kalogin_lastseen` (no plugin-message carrier, so even the last player on a backend is recorded); backends in `last-seen.blacklist` store no coordinates.
2. On the next connection, KaProxy's `connect-directly` (enabled by default) reads the database asynchronously during login and sets the initial server to the last backend, instead of landing on the default server first (avoiding the double join).
3. After the player logs in or restores the session on that backend, KaLogin tells the proxy it may travel, and that backend teleports the player to the stored coordinates.
4. If the target world does not exist or the coordinates are out of range, KaProxy moves the player to its configured `default-server`.
5. `/logout` and unregister delete the stored position, so the next login follows the default flow.

> Direct connect, blacklist, and fallback are controlled by KaProxy's `modules.lastseen`. Positions live in the shared MySQL database (same DB as KaLogin); the `server` column is written by KaLogin together with the coordinates on quit, and the proxy writes it again on a true disconnect as the authoritative value. KaLogin needs `proxy.server-name` (matching the proxy registration name) and `last-seen.blacklist`.

## Upgrade Note

KaLogin upgrades `config.yml` automatically and adds the `proxy` section (including `server-name`, empty by default; fill it in) and `last-seen.blacklist`. On KaProxy, `modules.kalogin`, `modules.lastseen`, and `database` must be added manually (KaProxy does not merge existing config files).
