---
description: KaLogin - A modern login, registration, and account security plugin for Paper servers
---

# Home

**KaLogin** is a Minecraft Paper login plugin with modern Dialog based login, registration, password change, email binding, password recovery, and welcome terms flows.

KaLogin can work as a standalone account system, or work with AuthMe. In AuthMe mode, AuthMe stores account and authentication data, while KaLogin provides the user interface and extra account features.

## Who should use it

| Need | KaLogin helps with |
|---|---|
| Replace chat command login | Use Dialog based login and registration screens |
| Keep existing AuthMe accounts | Use AuthMe mode |
| Add email recovery | Let players bind email and recover passwords |
| Show server terms | Ask new players to accept welcome terms |
| Customize screens | Edit YAML based UI files |

## Main features

| Feature | Description |
|---|---|
| Login and registration | Built in mode or AuthMe mode |
| Password change and logout | Player account management commands |
| SQLite and MySQL | Local or shared account storage |
| IP account limit | Limit how many accounts can register from one IP |
| Same IP auto login | Optional checkbox for trusted environments |
| Email binding | Verification code based email binding and password recovery |
| Welcome terms | First login welcome and terms confirmation |
| Languages | Chinese and English language files |
| PlaceholderAPI | Account placeholders for other plugins |
| Actions | Run commands, Toasts, and delays after login or registration |

## Recommended reading order

1. Read [Getting Started](home/start.md).
2. Read [Authentication Modes](features/auth-modes.md).
3. Read [Configuration](config/config.md).
4. Read [UI Customization](features/ui.md).
5. Read [Commands](perm/commands.md).

## Support

If something does not work, check the server console, `plugins/KaLogin/config.yml`, language files, and UI files first.
