# Login and Registration Actions

KaLogin can send messages, run commands, or delay subsequent actions after a player logs in or registers.

## Configuration location

```yaml
events:
  login:
    - 'console: say Player %player_name% logged in!'
    - 'tell: <green>Login successful <gray>Welcome back, %player_name%'
    - 'wait: 20'
    - 'command: spawn'
  register:
    - 'console: say Player %player_name% registered!'
    - 'tell: <aqua>Registration successful <gray>Welcome, %player_name%'
    - 'wait: 20'
    - 'command: help'
```

## Actions

| Action | Description |
|---|---|
| `console: <command>` | Run a command as console |
| `command: <command>` | Run a command as the player |
| `tell: <message>` | Send a message to the player with color and MiniMessage support |
| `toast: ...` | Show a Toast notification on Paper/Folia only |
| `wait: <ticks>` | Wait before continuing |

Spigot does not support Toast actions. KaLogin skips the action and logs one console warning; use `tell:` when the configuration must work on every supported platform.

## Toast parameters

```yaml
- 'toast: type=task;icon=paper;title=<green>Login successful;description=<gray>Welcome back'
```

| Parameter | Description |
|---|---|
| `type` | `task`, `goal`, or `challenge` |
| `icon` | Item ID used as the Toast icon |
| `title` | Toast title |
| `description` | Toast description |

## Variables

Use `%player_name%` for the player name.

```yaml
events:
  login:
    - 'tell: <green>Welcome back, %player_name%'
```

If PlaceholderAPI is installed, PAPI placeholders can also be used in action text.
