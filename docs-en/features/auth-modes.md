# Authentication Modes

KaLogin supports two authentication modes.

## AuthMe mode

Use this mode if your server already uses AuthMe.

```yaml
use-AuthMe: true
```

In this mode:

| Item | Managed by |
|---|---|
| Account password and login state | AuthMe |
| Login, registration, and password change screens | KaLogin |
| Email, terms, and auto login settings | KaLogin |

> Note: In AuthMe mode, same IP auto login is handled by AuthMe sessions. The "same IP auto login" checkbox in the KaLogin screen does not affect AuthMe login. KaLogin login/registration timeouts also do not apply in AuthMe mode; AuthMe controls timeouts.

Make sure AuthMe is installed and enabled. If AuthMe is not available, KaLogin falls back to its built in mode.

## KaLogin built in mode

Use this mode if you do not use AuthMe.

```yaml
use-AuthMe: false
```

In this mode:

| Item | Description |
|---|---|
| Accounts | Managed by KaLogin |
| Passwords | Stored with secure hashes |
| Storage | SQLite or MySQL |

## Which mode should I choose

In either authentication mode, KaLogin's authentication protection neither changes a player's game mode temporarily nor restores an earlier game mode after authentication. On a standalone server or without a world manager, the current mode set by the server or other plugins is retained. With a world manager such as Multiverse-Core, its destination-world rules determine the mode after a cross-world teleport. No HuskSync or other synchronization plugin is required for this behavior.

| Need | Recommended mode |
|---|---|
| You already have AuthMe account data | AuthMe mode |
| You are starting a new server | KaLogin built in mode |
| You need compatibility with AuthMe related plugins | AuthMe mode |
| You want fewer plugin dependencies | KaLogin built in mode |
