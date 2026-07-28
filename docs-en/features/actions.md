# Login and Registration Actions

KaLogin can run actions after a player logs in or registers, such as sending prompts, running commands, or showing Toast notifications.

## Configuration location

```yaml
events:
  login:
    - 'console: say Player %player_name% logged in!'
    - 'toast: type=task;icon=paper;title=<green>Login successful;description=<gray>Welcome back, %player_name%'
    - 'wait: 20'
    - 'command: spawn'
  register:
    - 'console: say Player %player_name% registered!'
    - 'toast: type=goal;icon=emerald;title=<aqua>Registration successful;description=<gray>Welcome, %player_name%'
    - 'wait: 20'
    - 'command: help'
```

## Actions

| Action | Description |
|---|---|
| `console: <command>` | Run a command as console |
| `command: <command>` | Run a command as the player |
| `toast: ...` | Show a Toast notification |
| `wait: <ticks>` | Wait before continuing |

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
    - 'console: tell %player_name% Welcome back'
```

If PlaceholderAPI is installed, PAPI placeholders can also be used in action text.
