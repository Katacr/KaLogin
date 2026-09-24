# 群组服务器与跨服登录

KaLogin 可以通过 KaProxy 在群组服务器（Velocity / BungeeCord）中共享登录状态：玩家在任意子服登录后，切换到其它子服时无需重复登录。

## 前置条件

- 代理端安装 KaProxy，并启用 `modules.kalogin`（详见 KaProxy 文档）。
- 所有子服安装 KaLogin，并设置 `proxy.enabled: true`。
- 所有子服共享同一 MySQL 数据库。
- 使用 AuthMe 模式时，各子服的 AuthMe 也需共享同一数据库。

## 配置

```yaml
proxy:
  enabled: false
  server-name: ""
  require-proxy: false
  query-timeout-ms: 5000
  query-delay-ticks: 3
  restore-welcome: true
  restore-email-prompt: false
  replay-login-actions: false

last-seen:
  enabled: true
  blacklist:
    - pve
    - pvp
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `proxy.enabled` | `false` | 是否启用群组登录会话。 |
| `proxy.server-name` | 空 | 本服在代理中的注册名（须与 velocity.toml 一致），用于写上次位置；留空则不记录。 |
| `proxy.require-proxy` | `false` | 代理不可用时是否拒绝进入；`false` 时回退本地登录/注册。 |
| `proxy.query-timeout-ms` | `5000` | 查询代理会话的应答超时（毫秒），超时按代理不可用处理。 |
| `proxy.query-delay-ticks` | `3` | 进服后延迟多少 tick 再发送查询，避开连接 configuration 阶段的丢包。 |
| `proxy.restore-welcome` | `true` | 跨服恢复时，是否仍按数据库条款状态决定是否弹欢迎界面。 |
| `proxy.restore-email-prompt` | `false` | 跨服恢复时是否重复弹出邮箱绑定提示。 |
| `proxy.replay-login-actions` | `false` | 跨服恢复时是否重放 `events.login` 动作。 |
| `last-seen.enabled` | `true` | 登录完成后是否自动回到上次下线的子服与坐标（依赖 `proxy.enabled`）。 |
| `last-seen.blacklist` | `[pve, pvp]` | 不记录位置的子服名列表（须与 `proxy.server-name`/代理注册名一致）；从这些子服退服时不写坐标，下次登录回默认服出生点。 |

## 工作方式

1. 玩家登录/注册成功，KaLogin 向 KaProxy 上报会话。
2. 玩家切换子服，目标服 KaLogin 进服后向 KaProxy 查询会话。
3. KaProxy 命中会话且 IP 一致时返回已认证，目标服恢复登录态；否则回退本地登录/注册。
4. 玩家真正断线时 KaProxy 销毁会话；子服切换不销毁。
5. 玩家执行 `/logout` 时，KaProxy 使会话失效并断开整个群组连接。

## 行为说明

- 会话绑定登录时的 IP（由 KaProxy 的 `bind-ip` 控制），IP 变化时会拒绝恢复。
- AuthMe 模式下，目标服会调用 AuthMe 恢复登录态，因此各服 AuthMe 数据库必须一致。
- 代理不可用且 `require-proxy: true` 时，玩家会被踢出；否则回退本地流程。

## 自动回到上次位置

启用 `last-seen.enabled` 后，玩家会回到上次下线的子服与坐标，替代每次都从默认服（如 Lobby）开始：

1. 玩家退服或切服时，KaLogin 把当前坐标与所在子服名**原子写入共享数据库** `kalogin_lastseen`（不依赖插件消息，因此单人子服退服也能记录）；位于 `last-seen.blacklist` 的子服不写坐标。
2. 玩家下次连接时，KaProxy 默认开启 `connect-directly`：在登录阶段异步读库，直接把玩家连到上次所在子服，不再先落默认服再切换（避免双重进服）。
3. 玩家在该子服完成登录/跨服恢复后，KaLogin 通知代理可以前往，该子服把玩家传送到记录的坐标。
4. 目标世界不存在或坐标越界时，KaProxy 会把玩家回退到其配置的 `default-server`。
5. `/logout` 或注销会删除位置记录，下次登录从默认流程开始。

> 直连、黑名单与回退由 KaProxy 的 `modules.lastseen` 控制；位置存于共享 MySQL（与 KaLogin 同一库）。`server` 列由 KaLogin 退服时随坐标一并写入，代理在玩家真正离开时再写一次作为权威值。KaLogin 侧需配置 `proxy.server-name`（须与代理注册名一致）与 `last-seen.blacklist`。

## 升级注意

插件会自动把 `config.yml` 升级到最新版本并补上 `proxy` 节点（含 `server-name`，默认空，需手动填写）与 `last-seen.blacklist`；代理端 KaProxy 的 `modules.kalogin`、`modules.lastseen`、`database` 需要手动补充（KaProxy 不会自动合并已有配置）。
