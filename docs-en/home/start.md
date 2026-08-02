# Getting Started

This page explains how to install KaLogin and complete the first usable setup.

## Requirements

| Item | Requirement |
|---|---|
| Minecraft server | Paper/Folia 1.21.7 or newer, Spigot 1.21.6 or newer |
| Java | Java 21 or newer |
| Database | SQLite by default, MySQL optional |
| Optional plugins | AuthMe, PlaceholderAPI, Geyser-Spigot |

## Installation

1. Put the KaLogin `.jar` file into the server `plugins` folder.
2. Start the server.
3. KaLogin generates `plugins/KaLogin/config.yml`, language files, and UI files.
4. Edit `config.yml` for your server.
5. Run `/kl reload` or restart the server.

## Choose an authentication mode

| Mode | Use when |
|---|---|
| AuthMe mode | Your server already uses AuthMe |
| KaLogin mode | You want KaLogin to manage accounts directly |

Set this in `config.yml`:

```yaml
use-AuthMe: true
```

Use `true` for AuthMe mode. Use `false` for KaLogin built in mode.

## Verify the installation

After the server starts, the console should show that KaLogin is enabled. Players should see a login or registration screen when joining.

Admins can run:

```bash
/kl reload
```

If the command works, the plugin is loaded.

## Recommended first settings

For public servers, check these settings first:

```yaml
login:
  max-login-attempts: 3
  max-accounts-per-ip: 3
  show-auto-login-checkbox: false
```

For password recovery, continue with [Email Binding and Password Recovery](../features/email.md).
