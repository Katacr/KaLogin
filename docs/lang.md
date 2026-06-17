# 语言文件配置

KaLogin 支持多语言，语言文件位于 `plugins/KaLogin/lang/` 目录下。

---

## 支持的语言

| 文件 | 语言 |
|------|------|
| `lang/zh_CN.yml` | 简体中文 |
| `lang/en_US.yml` | English |

通过 `config.yml` 中的 `language` 配置项切换：

```yaml
language: "zh_CN"   # 或 "en_US"
```

---

## 文件结构

语言文件按功能模块分组，使用 YAML 层级结构：

```yaml
command:          # 命令相关消息
login:            # 登录相关消息
register:         # 注册相关消息
change-password:  # 修改密码相关消息
ip-limit:         # IP 限制相关消息
password-validation:  # 密码验证相关消息
plugin:           # 插件相关消息
logout:           # 登出相关消息
bind-email:       # 邮箱绑定相关消息
recover-password: # 密码找回相关消息
welcome:          # 欢迎/条款相关消息
email-verification:   # 邮件内容
anti-cheat:       # 防作弊相关消息
authme:           # AuthMe 集成相关消息
config_update:    # 配置更新相关消息
user-center:      # 用户中心相关消息
```

---

## 格式支持

### 颜色代码

```yaml
success: "&a登录成功！欢迎回来！"        # &a = 绿色
error: "&c密码错误，请重试。"            # &c = 红色
warning: "&e正在验证密码..."             # &e = 黄色
info: "&f用户名: &e{username}"          # 混合颜色
```

### MiniMessage 格式

```yaml
text: "<green>登录成功"
text: "<gradient:gold:yellow>欢迎<reset>"
```

### 占位符变量

语言文件中使用 `{变量名}` 作为占位符，运行时会被替换为实际值：

| 变量 | 说明 | 出现位置 |
|------|------|----------|
| `{player}` | 玩家名称 | 命令消息 |
| `{attempts}` | 剩余尝试次数 | 登录/修改密码 |
| `{seconds}` | 超时秒数 | 登录/注册超时 |
| `{count}` | 数量 | IP 限制、批量操作 |
| `{error}` | 错误详情 | 密码验证失败 |
| `{min}` | 最小值 | 密码长度验证 |
| `{max}` | 最大值 | 密码长度验证 |
| `{word}` | 匹配的黑名单词 | 密码黑名单 |
| `{email}` | 邮箱地址 | 邮箱绑定/找回 |
| `{code}` | 验证码 | 邮件内容 |
| `{latest}` | 最新版本号 | 更新检查 |
| `{current}` | 当前版本号 | 更新检查 |
| `{old}` | 旧配置版本 | 配置更新 |
| `{new}` | 新配置版本 | 配置更新 |
| `{path}` | 文件路径 | 配置备份 |
| `{username}` | 用户名 | 用户中心 |
| `{date}` | 注册日期 | 用户中心 |
| `{ip}` | IP 地址 | 用户中心 |
| `{description}` | 动态描述 | 注册对话框 |

### HoverText 语法

在语言文件中也可以使用可交互文本：

```yaml
# 点击执行命令
prompt-message: "&e建议绑定邮箱 <text=' &a[前往绑定]';hover='点击打开邮箱绑定';command='/bindemail'>"

# 点击打开链接
update-available: "&a[MineBBS] &8| &b[SpigotMC]"
```

---

## 自定义指南

### 修改消息内容

直接编辑对应的语言文件即可。修改后使用 `/kl reload` 重载配置。

```yaml
# 修改前
login:
  success: "&a登录成功！欢迎回来！"

# 修改后
login:
  success: "&a&l欢迎回到我的服务器！"
```

### 添加新语言

1. 复制 `lang/zh_CN.yml` 为新文件（如 `lang/ja_JP.yml`）
2. 翻译所有消息内容
3. 在 `config.yml` 中设置 `language: "ja_JP"`
4. 同时需要创建对应的 UI 目录（如 `ui-ja/`）

### 注意事项

- **不要删除任何 key**：缺少的 key 会导致插件报错
- **保留占位符**：确保 `{变量名}` 格式的占位符不被删除或修改
- **YAML 特殊字符**：包含 `:` 或 `#` 的文本需要用引号包裹
- **多行文本**：使用 `|` 语法
