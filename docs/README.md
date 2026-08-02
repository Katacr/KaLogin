---
description: KaLogin - 面向 Paper 服务器的现代化登录、注册与账号安全插件
---

# 首页

**KaLogin** 是一款 Minecraft 登录插件，支持 Paper、Folia 和 Spigot，提供基于原生 Dialog 的登录、注册、修改密码、邮箱绑定、密码找回和欢迎条款确认体验。

插件可以单独作为账号系统使用，也可以配合 AuthMe 使用，让 AuthMe 负责账号数据与认证，KaLogin 负责更现代的界面与用户交互。

## 适合谁使用

- 想要替换传统聊天命令登录体验的服务器
- 已经使用 AuthMe，但希望提供更友好的登录和注册界面
- 需要邮箱绑定、验证码找回密码、条款确认的服务器
- 需要可自定义登录、注册、改密界面的服务器

## 核心功能

- 登录、注册、修改密码、登出
- AuthMe 模式和 KaLogin 内置认证模式
- SQLite 和 MySQL 数据库
- 每个 IP 注册账号数量限制
- 同 IP 自动登录开关
- 邮箱绑定和邮箱验证码找回密码
- 欢迎/服务器条款确认界面
- 多语言：简体中文和英文
- PlaceholderAPI 变量
- 登录/注册完成后的动作配置

## 推荐阅读顺序

1. 阅读 [快速开始](home/start.md)，完成安装和首次启动。
2. 阅读 [认证模式](features/auth-modes.md)，选择 AuthMe 模式或 KaLogin 模式。
3. 阅读 [配置文件](config/config.md)，按服务器需求调整登录限制、密码策略和数据库。
4. 阅读 [界面自定义](features/ui.md)，修改玩家看到的登录、注册和欢迎界面。
5. 阅读 [命令列表](perm/commands.md)，了解管理员和玩家可用命令。

## 支持与反馈

如果遇到问题，请优先检查控制台日志、`plugins/KaLogin/config.yml` 和对应语言/UI 文件是否正确。
