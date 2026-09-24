# 认证模式

KaLogin 支持两种认证模式。

## AuthMe 模式

适合已经安装 AuthMe 的服务器。

```yaml
use-AuthMe: true
```

在这个模式下：

- 玩家账号、密码和登录状态由 AuthMe 管理
- KaLogin 提供登录、注册、修改密码等界面
- KaLogin 仍会保存邮箱、条款确认、自动登录设置等扩展数据

> 注意：AuthMe 模式下，同 IP 自动登录由 AuthMe 的会话（session）机制处理；KaLogin 界面上的"同 IP 自动登录"勾选框不会影响 AuthMe 的登录判定。AuthMe 模式下 KaLogin 的登录/注册超时设置同样不生效，超时由 AuthMe 控制。

安装时请确保 AuthMe 插件已放入 `plugins` 文件夹并正常启用。否则 KaLogin 会回退到内置认证模式。

## KaLogin 模式

适合不使用 AuthMe 的服务器。

```yaml
use-AuthMe: false
```

在这个模式下：

- KaLogin 独立管理注册、登录和密码
- 密码使用安全散列保存
- 可使用 SQLite 或 MySQL 保存账号数据

## 如何选择

| 需求 | 推荐模式 |
|---|---|
| 已经有 AuthMe 数据 | AuthMe 模式 |
| 新服，没有旧账号系统 | KaLogin 模式 |
| 希望最大程度兼容 AuthMe 插件生态 | AuthMe 模式 |
| 希望减少插件依赖 | KaLogin 模式 |
