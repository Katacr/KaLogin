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

| Need | Recommended mode |
|---|---|
| You already have AuthMe account data | AuthMe mode |
| You are starting a new server | KaLogin built in mode |
| You need compatibility with AuthMe related plugins | AuthMe mode |
| You want fewer plugin dependencies | KaLogin built in mode |
