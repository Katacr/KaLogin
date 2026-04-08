package org.katacr.kalogin

import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.katacr.kalogin.listener.KaLoginAPI
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LoginListener(private val plugin: KaLogin) : Listener {

    // 密码验证器
    private val passwordValidator = PasswordValidator(plugin)



    // 用于跟踪已登录的玩家
    private val loggedInPlayers = ConcurrentHashMap<UUID, Boolean>()

    // 跟踪玩家的登录错误次数
    private val loginAttempts = ConcurrentHashMap<UUID, Int>()



    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"

        // 如果使用 AuthMe 模式，由 AuthMeLoginListener 处理自动登录检查
        if (plugin.authMeManager.useAuthMe) {
            // AuthMe 模式下初始化玩家记录
            plugin.authMeManager.initPlayerInDatabase(player)
            return
        }

        // KaLogin 模式：以下逻辑
        // 重置登录错误次数
        loginAttempts.remove(player.uniqueId)

        // 检查玩家是否已注册
        plugin.dbManager.isPlayerRegistered(player.uniqueId).thenAccept { registered ->
            if (registered) {
                // 已注册，检查玩家是否启用同IP自动登录
                plugin.dbManager.canAutoLogin(player.uniqueId, currentIp).thenAccept { canAutoLogin ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (canAutoLogin) {
                        // IP 相同且玩家启用了自动登录，自动登录
                        player.sendMessage(plugin.messageManager.getComponent("login.auto-login-success"))
                        loggedInPlayers[player.uniqueId] = true
                        plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
                        // 结束防作弊状态
                        plugin.antiCheatManager.endAuthenticating(player)
                        // 触发自动登录事件
                        KaLoginAPI.getInstance()?.callPlayerAutoLogin(player, currentIp)
                    } else {
                            // 显示登录对话框
                            showLoginDialog(player)
                        }
                    })
                }
            } else {
                // 未注册，检查 IP 注册数量限制
                val maxAccountsPerIp = plugin.config.getInt("login.max-accounts-per-ip", 0)
                if (maxAccountsPerIp > 0) {
                    plugin.dbManager.countAccountsByIp(currentIp).thenAccept { count ->
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            if (count >= maxAccountsPerIp) {
                                player.kick(plugin.messageManager.getComponent("ip-limit.exceeded", "count" to maxAccountsPerIp))
                            } else {
                                showRegisterDialog(player, plugin.messageManager.getMessage("register.welcome", "seconds" to 90))
                            }
                        })
                    }
                } else {
                    // 未启用限制，显示注册对话框
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        showRegisterDialog(player, plugin.messageManager.getMessage("register.welcome", "seconds" to 90))
                    })
                }
            }
        }
    }

    /**
     * 显示登录对话框
     */
    fun showLoginDialog(player: Player, errorMessage: String? = null) {
        // 开始防作弊状态（仅在首次调用时）
        if (!plugin.antiCheatManager.isAuthenticating(player)) {
            plugin.antiCheatManager.startAuthenticating(player)
        }

        // 设置对话框类型
        plugin.antiCheatManager.setPlayerDialogType(player, "login")

        // 取消之前的超时任务（如果有）
        plugin.antiCheatManager.loginTimeoutTasks[player.uniqueId]?.let { taskId ->
            plugin.server.scheduler.cancelTask(taskId)
            plugin.antiCheatManager.loginTimeoutTasks.remove(player.uniqueId)
        }

        // 启动登录超时任务
        val timeoutSeconds = plugin.config.getInt("login.login-timeout", 60)
        val taskId = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                player.kick(plugin.messageManager.getComponent("login.timeout-kick", "seconds" to timeoutSeconds))
                plugin.antiCheatManager.endAuthenticating(player)
            }
            plugin.antiCheatManager.loginTimeoutTasks.remove(player.uniqueId)
        }, timeoutSeconds * 20L).taskId
        plugin.antiCheatManager.loginTimeoutTasks[player.uniqueId] = taskId


        val maxAttempts = plugin.config.getInt("login.max-login-attempts", 3)
        val attemptsLeft = maxAttempts - (loginAttempts[player.uniqueId] ?: 0)

        if (attemptsLeft <= 0) {
            player.kick(plugin.messageManager.getComponent("login.too-many-attempts"))
            plugin.antiCheatManager.endAuthenticating(player)
            return
        }

        val loginAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val password = response.getText("login_password")
                val autoLoginCheckbox = response.getBoolean("auto_login_by_ip") ?: false

                if (password.isNullOrBlank()) {
                    showLoginDialog(player, plugin.messageManager.getMessage("login.password-empty"))
                    return@DialogActionCallback
                }

                // 异步验证密码
                plugin.dbManager.verifyPassword(player.uniqueId, password).thenAccept { isValid: Boolean ->
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            if (isValid) {
                                // 取消登录超时任务
                                plugin.antiCheatManager.loginTimeoutTasks[player.uniqueId]?.let { taskId ->
                                    plugin.server.scheduler.cancelTask(taskId)
                                    plugin.antiCheatManager.loginTimeoutTasks.remove(player.uniqueId)
                                }

                                val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"

                                player.sendMessage(plugin.messageManager.getComponent("login.success"))
                                loggedInPlayers[player.uniqueId] = true
                                loginAttempts.remove(player.uniqueId)
                                // 更新最后登录 IP
                                plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
                                // 更新自动登录设置
                                plugin.dbManager.updateAutoLoginByIp(player.uniqueId, autoLoginCheckbox)
                                // 结束防作弊状态
                                plugin.antiCheatManager.endAuthenticating(player)

                                // 触发登录成功事件
                                KaLoginAPI.getInstance()?.callPlayerLoginSuccess(player, currentIp, false)
                            } else {
                            val currentAttempts = (loginAttempts[player.uniqueId] ?: 0) + 1
                            loginAttempts[player.uniqueId] = currentAttempts

                            val remainingAttempts = maxAttempts - currentAttempts
                            if (remainingAttempts > 0) {
                                // 触发登录失败事件
                                KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, remainingAttempts)
                                showLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to remainingAttempts))
                            } else {
                                player.kick(plugin.messageManager.getComponent("login.too-many-attempts"))
                                plugin.antiCheatManager.endAuthenticating(player)
                                // 触发登录失败事件（剩余次数为0）
                                KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, 0)
                            }
                        }
                    })
                }
            },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
        )

        val errorComponent = errorMessage?.let { plugin.messageManager.getComponentFromMessage(it) }
        val confirmButton = ActionButton.builder(plugin.messageManager.getComponent("login.dialog-button"))
            .action(loginAction)
            .build()

        val dialog = LoginUI.buildLoginDialog(
            player,
            plugin.messageManager.getComponent("login.dialog-title"),
            null,  // welcomeMessage已移除，可在UI配置文件中自定义
            errorComponent,
            confirmButton
        )
        player.showDialog(dialog)
    }


    /**
     * 显示注册对话框（包含密码和确认密码两个输入框）
     */
    fun showRegisterDialog(player: Player, description: String, errorMessage: String? = null) {
        // 开始防作弊状态（仅在第一次调用时）
        if (!plugin.antiCheatManager.isAuthenticating(player)) {
            plugin.antiCheatManager.startAuthenticating(player)
        }

        // 设置对话框类型
        plugin.antiCheatManager.setPlayerDialogType(player, "register")

        // 取消之前的超时任务（如果有）
        plugin.antiCheatManager.registerTimeoutTasks[player.uniqueId]?.let { taskId ->
            plugin.server.scheduler.cancelTask(taskId)
            plugin.antiCheatManager.registerTimeoutTasks.remove(player.uniqueId)
        }

        // 启动注册超时任务
        val timeoutSeconds = plugin.config.getInt("login.register-timeout", 90)
        val taskId = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                player.kick(plugin.messageManager.getComponent("register.timeout-kick", "seconds" to timeoutSeconds))
                plugin.antiCheatManager.endAuthenticating(player)
            }
            plugin.antiCheatManager.registerTimeoutTasks.remove(player.uniqueId)
        }, timeoutSeconds * 20L).taskId
        plugin.antiCheatManager.registerTimeoutTasks[player.uniqueId] = taskId

        val registerAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val password = response.getText("reg_password")
                val confirmPassword = response.getText("reg_confirm_password")

                if (password.isNullOrBlank()) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-empty-retry"), plugin.messageManager.getMessage("register.password-empty"))
                    return@DialogActionCallback
                }

                // 验证密码格式
                val validationError = passwordValidator.validate(password)
                if (validationError != null) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-invalid", "error" to validationError), plugin.messageManager.getMessage("register.password-invalid", "error" to validationError))
                    return@DialogActionCallback
                }

                // 验证两次密码是否一致
                if (password != confirmPassword) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-mismatch-retry"), plugin.messageManager.getMessage("register.password-mismatch"))
                    return@DialogActionCallback
                }

                // 取消注册超时任务
                plugin.antiCheatManager.registerTimeoutTasks[player.uniqueId]?.let { taskId ->
                    plugin.server.scheduler.cancelTask(taskId)
                    plugin.antiCheatManager.registerTimeoutTasks.remove(player.uniqueId)
                }

                player.sendMessage(plugin.messageManager.getComponent("register.saving"))

                // 异步执行注册
                plugin.dbManager.registerPlayer(
                    player.uniqueId,
                    player.name,
                    password,
                    player.address?.address?.hostAddress ?: "127.0.0.1"
                ).thenAccept { success: Boolean ->
                    // 返回主线程给玩家发送反馈
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (success) {
                            player.sendMessage(plugin.messageManager.getComponent("register.success"))
                            // 标记玩家为已登录
                            loggedInPlayers[player.uniqueId] = true
                            // 结束防作弊状态
                            plugin.antiCheatManager.endAuthenticating(player)
                            // 触发注册成功事件
                            val ip = player.address?.address?.hostAddress ?: "127.0.0.1"
                            KaLoginAPI.getInstance()?.callPlayerRegisterSuccess(player, ip)
                        } else {
                            player.sendMessage(plugin.messageManager.getComponent("register.failed"))
                            // 触发注册失败事件
                            KaLoginAPI.getInstance()?.callPlayerRegisterFailed(player, "Database error")
                        }
                    })
                }
            },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
        )

        val errorComponent = errorMessage?.let { plugin.messageManager.getComponentFromMessage(it) }
        val confirmButton = ActionButton.builder(plugin.messageManager.getComponent("register.dialog-button"))
            .action(registerAction)
            .build()

        val dialog = LoginUI.buildRegisterDialog(
            player,
            plugin.messageManager.getComponent("register.dialog-title"),
            null,  // welcomeMessage已移除，可在UI配置文件中自定义
            errorComponent,
            confirmButton
        )
        player.showDialog(dialog)
    }


    /**
     * 清理玩家的登录数据（供 AntiCheatManager 调用）
     */
    fun clearPlayerData(uuid: UUID) {
        loginAttempts.remove(uuid)
        loggedInPlayers.remove(uuid)
    }

    /**
     * 检查玩家是否已登录
     */
    fun isLoggedIn(uuid: UUID): Boolean {
        return loggedInPlayers[uuid] == true
    }
}