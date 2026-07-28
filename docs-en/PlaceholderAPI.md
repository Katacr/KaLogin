# PlaceholderAPI

If PlaceholderAPI is installed, KaLogin registers `%kalogin_*%` placeholders.

## Placeholders

| Placeholder | Description |
|---|---|
| `%kalogin_email_masked%` | Masked email address |
| `%kalogin_email_plain%` | Plain email address |
| `%kalogin_accepted_terms%` | Whether the player accepted terms |
| `%kalogin_last_login_ip%` | Last login IP |
| `%kalogin_auto_login_by_ip%` | Whether same IP auto login is enabled |
| `%kalogin_register_time%` | Registration time |

## Usage

These placeholders can be used in scoreboards, chat formats, Tab lists, menu plugins, and other plugins that support PlaceholderAPI.

Example:

```text
Email: %kalogin_email_masked%
Terms: %kalogin_accepted_terms%
```
