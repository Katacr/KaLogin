# UI Customization

KaLogin login, registration, password change, and welcome terms screens can be edited with YAML files.

## File location

```text
plugins/KaLogin/ui/
```

| File | Use |
|---|---|
| `login.yml` | Login screen |
| `register.yml` | Registration screen |
| `change-password.yml` | Password change screen |
| `welcome.yml` | Welcome and terms screen |

These files are generated on first startup. After editing them, run `/kl reload`.

## Body content

Each UI file has a `Body` section. You can add text and item displays.

### Text

```yaml
Body:
  welcome:
    type: 'message'
    text: '<gradient:gold:yellow>Welcome back!<reset>'
    width: 300

  tips:
    type: 'message'
    text:
      - '&7Enter your password to continue'
      - '&7Contact an administrator if you need help'
```

| Property | Description |
|---|---|
| `type` | Use `message` |
| `text` | Text shown to the player, as a single string or a string list |
| `width` | Optional text width |

### Item

```yaml
Body:
  icon:
    type: 'item'
    material: 'apple'
    amount: 1
    name: '&aServer Icon'
    lore:
      - '&7Welcome to the server'
    description: '&7Follow the prompts to continue'
    description_width: 300
    item_model: ''
    custom_model_data: 1001
    show_overlays: false
    show_tooltip: true
    width: 16
    height: 16
```

| Property | Description |
|---|---|
| `type` | Use `item` |
| `material` | Item ID, such as `apple` or `diamond` |
| `amount` | Item amount, default `1` |
| `name` | Item display name |
| `lore` | Item lore |
| `description` | Extra text below the item, as text or list |
| `description_width` | Optional description width |
| `item_model` | Custom item model, format `namespace:path` |
| `custom_model_data` | Legacy CustomModelData number |
| `show_overlays` | Shows count, durability, and other overlays, default `false` |
| `show_tooltip` | Shows the vanilla item tooltip on hover, default `true` |
| `width` / `height` | Item icon size, default `16` |

## Text format

Legacy color codes are supported:

```yaml
text: '&aWelcome back, &fplease enter your password'
```

MiniMessage is also supported:

```yaml
text: '<green>Welcome back</green>'
```

## Clickable text

You can add clickable links or commands inside text:

```yaml
text: '&7Click <text=&b[Website];hover=&7Open website;url=https://example.com> to view rules'
```

```yaml
text: '&7Click <text=&a[Bind Email];hover=&7Open email binding;command=/bindemail>'
```

| Parameter | Description |
|---|---|
| `text` | Visible text |
| `hover` | Hover text |
| `url` | Open a URL |
| `command` | Run a command |

Use either `url` or `command` in most cases.

## Input appearance

Inputs are not configured in UI files. They are configured in `config.yml` under `inputs`.

```yaml
inputs:
  login:
    login_password:
      width: 200
      height: 17
      labelVisible: true
      initial: ''
```

See [Configuration](../config/config.md).
