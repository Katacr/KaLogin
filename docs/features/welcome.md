# 欢迎和条款确认

KaLogin 可以在玩家首次登录或注册后显示欢迎和服务器条款界面。

## 开关

```yaml
welcome-dialog:
  enabled: true
```

设为 `false` 后，玩家不会看到欢迎/条款确认界面。

## 修改条款内容

编辑：

```text
plugins/KaLogin/ui/welcome.yml
```

示例：

```yaml
Body:
  welcome:
    type: 'message'
    text: |
      <gradient:gold:yellow>欢迎来到服务器！<reset>
      在继续游戏前，请先阅读以下说明与服务器条款。

  rules:
    type: 'message'
    text: |
      &7- 请遵守服务器规则
      &7- 禁止作弊、恶意破坏与骚扰他人
      &7- 如继续游戏，则视为你已阅读并同意服务器条款
```

## 重置条款状态

让指定玩家重新确认条款：

```bash
/kl resetterms <玩家名>
```

让所有玩家重新确认条款：

```bash
/kl resetterms all
```
