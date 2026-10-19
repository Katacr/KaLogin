# 常见问题

## 开启 AuthMe 模式后为什么回退到 KaLogin 模式？

请确认 AuthMe `.jar` 已放入 `plugins` 文件夹，并且服务器启动日志中 AuthMe 已正常启用。

## 为什么玩家没有收到邮箱验证码？

请检查：

- SMTP 地址、端口、账号和授权码是否正确
- 服务器是否允许连接 SMTP 端口
- 邮件是否进入垃圾箱
- `email-binding.enabled` 是否为 `true`

## 为什么登录/注册界面没有弹出？

可以尝试把 `dialog-delay-ticks` 调大，例如：

```yaml
login:
  dialog-delay-ticks: 5
```

如果仍然无效，请检查服务器版本是否满足要求。

## 修改 UI 后没有生效怎么办？

界面文件在每次弹出界面时从磁盘读取，无需执行 `/kl reload`。如果界面已经打开，请让玩家退出重进，或再次触发对应界面。

如果修改的是认证模式、数据库或插件依赖，建议完整重启服务器。

## 公网服务器是否建议开启同 IP 自动登录？

不建议。公网环境下，同 IP 不一定代表同一台设备。建议设置：

```yaml
login:
  show-auto-login-checkbox: false
```

## 切换子服后仍然要求重新登录？

逐项检查：

- 代理端是否安装 KaProxy 并启用了 `modules.kalogin`；
- 每台后端是否都设置了 `proxy.enabled: true`，且都连接同一个 MySQL；
- AuthMe 模式下，各后端的 AuthMe 是否也共用同一数据库；
- 会话按登录时的 IP 绑定（KaProxy 的 `bind-ip`），玩家 IP 变化时会被拒绝恢复；
- 代理不可用时按 `proxy.require-proxy` 决定是踢出还是回退本地登录，默认回退本地。

## 登录后没有回到上次的位置？

- `last-seen.enabled` 默认为 `true`，但依赖 `proxy.enabled`，两者都要开启；
- `proxy.server-name` 必须填写本服在代理中的真实注册名（与 `velocity.toml` 一致），群组内每台后端不能互相复制；
- 位于 `last-seen.blacklist`（默认 `pve`、`pvp`）的子服退服时不记录坐标，下次登录回默认服出生点；
- 上次位置所在的世界不存在或坐标越界时，代理会回退到 `default-server`；
- 回位模式由代理端 KaProxy 的 `modules.lastseen.mode` 决定（`direct` / `manual` / `auto`），KaLogin 侧无需配置模式。

## 没有收到“其他设备正在尝试登录”的安全提醒？

- 告警由代理产生：KaProxy 需启用 `modules.kalogin.security-alert`，且冲突登录必须发生在“该账户已有已认证会话”时才会发出；
- 告警只发给仍在线且已登录的账户本人，按玩家 UUID 冷却（默认 600 秒），不会展示攻击者 IP；
- BungeeCord 没有对应的冲突登录状态，只有 Velocity 侧会触发该告警；
- 登录失败临时封禁由代理的 `modules.kalogin.ip-ban` 控制，KaLogin 每次密码错误都会上报来源 IP。

## 升级后 KaProxy 的新配置没有生效？

KaProxy 不会把新配置节点自动合并进已有的 `config.yml`，只会在配置文件不存在时释放默认文件。升级后请手动补齐 `modules.kalogin`、`modules.lastseen` 与 `database` 段；KaLogin 侧新增的键会在 `/kl reload` 或重启后自动补全。
