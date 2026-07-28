# Welcome and Terms

KaLogin can show a welcome and server terms screen when players first complete login or registration.

## Enable or disable

```yaml
welcome-dialog:
  enabled: true
```

Set it to `false` if you do not want to show this screen.

## Edit terms text

Edit:

```text
plugins/KaLogin/ui/welcome.yml
```

Example:

```yaml
Body:
  welcome:
    type: 'message'
    text: |
      <gradient:gold:yellow>Welcome to the server!<reset>
      Please read the notes and server terms before continuing.

  rules:
    type: 'message'
    text: |
      &7- Follow server rules
      &7- No cheating, griefing, or harassment
      &7- Continuing means you have read and accepted the terms
```

## Reset terms status

Ask one player to accept the terms again:

```bash
/kl resetterms <player>
```

Ask all players to accept the terms again:

```bash
/kl resetterms all
```
