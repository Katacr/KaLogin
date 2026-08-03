package org.katacr.kalogin

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.katacr.kalogin.dialog.LoginResponse
import org.katacr.kalogin.dialog.RegisterResponse
import org.katacr.kalogin.listener.KaLoginAPI
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
                KaLoginScheduler.runPlayer(player, Runnable {
                    if (canAutoLogin) {
                        // IP 相同且玩家启用了自动登录，自动登录
                        plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("login.auto-login-success"))
                        loggedInPlayers[player.uniqueId] = true
                        plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
                        plugin.welcomeManager.showWelcomeIfNeeded(player) {
                            plugin.antiCheatManager.endAuthenticating(player)
                            plugin.emailBindManager.showPromptIfNeeded(player)
                            KaLoginAPI.getInstance()?.callPlayerAutoLogin(player, currentIp)
                        }
                    } else {
                            // 根据配置延迟显示登录对话框
                            showLoginDialogDelayed(player)
                        }
                    })
                }
            } else {
                // 未注册，检查 IP 注册数量限制
                val maxAccountsPerIp = plugin.config.getInt("login.max-accounts-per-ip", 0)
                if (maxAccountsPerIp > 0) {
                    val registerTimeout = plugin.config.getInt("login.register-timeout", 90)
                    plugin.dbManager.countAccountsByIp(currentIp).thenAccept { count ->
                        KaLoginScheduler.runPlayer(player, Runnable {
                            if (count >= maxAccountsPerIp) {
                                plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("ip-limit.exceeded", "count" to maxAccountsPerIp))
                            } else {
                                showRegisterDialogDelayed(
                                    player,
                                    plugin.messageManager.getMessage("register.welcome", "seconds" to registerTimeout)
                                )
                            }
                        })
                    }
                } else {
                    // 未启用限制，显示注册对话框
                    val registerTimeout = plugin.config.getInt("login.register-timeout", 90)
                    KaLoginScheduler.runPlayer(player, Runnable {
                        showRegisterDialogDelayed(
                            player,
                            plugin.messageManager.getMessage("register.welcome", "seconds" to registerTimeout)
                        )
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
            taskId.cancel()
            plugin.antiCheatManager.loginTimeoutTasks.remove(player.uniqueId)
        }

        // 启动登录超时任务
        val timeoutSeconds = plugin.config.getInt("login.login-timeout", 60)
        val taskId = KaLoginScheduler.runPlayerLater(player, timeoutSeconds * 20L, Runnable {
            if (player.isOnline) {
                plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("login.timeout-kick", "seconds" to timeoutSeconds))
                plugin.antiCheatManager.endAuthenticating(player)
            }
            plugin.antiCheatManager.loginTimeoutTasks.remove(player.uniqueId)
        })
        plugin.antiCheatManager.loginTimeoutTasks[player.uniqueId] = taskId


        val maxAttempts = plugin.config.getInt("login.max-login-attempts", 3)
        val attemptsLeft = maxAttempts - (loginAttempts[player.uniqueId] ?: 0)

        if (attemptsLeft <= 0) {
            plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("login.too-many-attempts"))
            plugin.antiCheatManager.endAuthenticating(player)
            return
        }

        val loginAction: (LoginResponse) -> Unit = loginAction@{ response ->
                val password = response.password
                val autoLoginCheckbox = response.autoLoginByIp

                if (password.isNullOrBlank()) {
                    showLoginDialog(player, plugin.messageManager.getMessage("login.password-empty"))
                    return@loginAction
                }

                // 异步验证密码
                plugin.dbManager.verifyPassword(player.uniqueId, password).thenAccept { isValid: Boolean ->
                        KaLoginScheduler.runPlayer(player, Runnable {
                            if (isValid) {
                                // 取消登录超时任务
                                plugin.antiCheatManager.loginTimeoutTasks[player.uniqueId]?.let { taskId ->
                                    taskId.cancel()
                                    plugin.antiCheatManager.loginTimeoutTasks.remove(player.uniqueId)
                                }

                                val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"

                                plugin.antiCheatManager.markProgrammaticClose(player)
                                plugin.dialogPlatform.close(player)
                                plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("login.success"))
                                loggedInPlayers[player.uniqueId] = true
                                loginAttempts.remove(player.uniqueId)
                                // 更新最后登录 IP
                                plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
                                // 更新自动登录设置
                                plugin.dbManager.updateAutoLoginByIp(player.uniqueId, autoLoginCheckbox)
                                plugin.welcomeManager.showWelcomeIfNeeded(player) {
                                    plugin.antiCheatManager.endAuthenticating(player)
                                    plugin.eventActionExecutor.execute(player, "login")
                                    plugin.emailBindManager.showPromptIfNeeded(player)
                                    KaLoginAPI.getInstance()?.callPlayerLoginSuccess(player, currentIp, false)
                                }
                            } else {
                            val currentAttempts = (loginAttempts[player.uniqueId] ?: 0) + 1
                            loginAttempts[player.uniqueId] = currentAttempts

                            val remainingAttempts = maxAttempts - currentAttempts
                            if (remainingAttempts > 0) {
                                // 触发登录失败事件
                                KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, remainingAttempts)
                                showLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to remainingAttempts))
                            } else {
                                plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("login.too-many-attempts"))
                                plugin.antiCheatManager.endAuthenticating(player)
                                // 触发登录失败事件（剩余次数为0）
                                KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, 0)
                            }
                        }
                    })
                }
            }

        plugin.dbManager.getPlayerEmail(player.uniqueId).thenAccept { email ->
            KaLoginScheduler.runPlayer(player, Runnable {
                if (!player.isOnline) return@Runnable
                plugin.antiCheatManager.markDialogOpened(player)
                val errorComponent = plugin.resolveDialogErrorComponent(player, errorMessage)
                val description = if (email.isNullOrBlank()) {
                    null
                } else {
                    LoginUI.parseClickableText(plugin.messageManager.getMessage("login.recover-entry"), player)
                }
                plugin.dialogPlatform.showLogin(
                    player,
                    plugin.messageManager.getComponent("login.dialog-title"),
                    description,
                    errorComponent,
                    loginAction
                )
            })
        }
    }

    /**
     * 根据配置延迟显示登录对话框
     * 如果 dialog-delay-ticks <= 0，立即显示；否则延迟指定 tick 数
     */
    private fun showLoginDialogDelayed(player: Player) {
        val delayTicks = plugin.config.getLong("login.dialog-delay-ticks", 0)
        if (delayTicks > 0) {
            KaLoginScheduler.runPlayerLater(player, delayTicks, Runnable {
                if (player.isOnline) {
                    showLoginDialog(player)
                }
            })
        } else {
            showLoginDialog(player)
        }
    }

    /**
     * 根据配置延迟显示注册对话框
     * 如果 dialog-delay-ticks <= 0，立即显示；否则延迟指定 tick 数
     */
    private fun showRegisterDialogDelayed(player: Player, description: String) {
        val delayTicks = plugin.config.getLong("login.dialog-delay-ticks", 0)
        if (delayTicks > 0) {
            KaLoginScheduler.runPlayerLater(player, delayTicks, Runnable {
                if (player.isOnline) {
                    showRegisterDialog(player, description)
                }
            })
        } else {
            showRegisterDialog(player, description)
        }
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
            taskId.cancel()
            plugin.antiCheatManager.registerTimeoutTasks.remove(player.uniqueId)
        }

        // 启动注册超时任务
        val timeoutSeconds = plugin.config.getInt("login.register-timeout", 90)
        val taskId = KaLoginScheduler.runPlayerLater(player, timeoutSeconds * 20L, Runnable {
            if (player.isOnline) {
                plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("register.timeout-kick", "seconds" to timeoutSeconds))
                plugin.antiCheatManager.endAuthenticating(player)
            }
            plugin.antiCheatManager.registerTimeoutTasks.remove(player.uniqueId)
        })
        plugin.antiCheatManager.registerTimeoutTasks[player.uniqueId] = taskId

        val registerAction: (RegisterResponse) -> Unit = registerAction@{ response ->
                val password = response.password
                val confirmPassword = response.confirmPassword

                if (password.isNullOrBlank()) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-empty-retry"), plugin.messageManager.getMessage("register.password-empty"))
                    return@registerAction
                }

                // 验证密码格式
                val validationError = passwordValidator.validate(password)
                if (validationError != null) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-invalid", "error" to validationError), plugin.messageManager.getMessage("register.password-invalid", "error" to validationError))
                    return@registerAction
                }

                // 验证两次密码是否一致
                if (password != confirmPassword) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-mismatch-retry"), plugin.messageManager.getMessage("register.password-mismatch"))
                    return@registerAction
                }

                // 取消注册超时任务
                plugin.antiCheatManager.registerTimeoutTasks[player.uniqueId]?.let { taskId ->
                    taskId.cancel()
                    plugin.antiCheatManager.registerTimeoutTasks.remove(player.uniqueId)
                }

                plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("register.saving"))

                // 异步执行注册
                plugin.dbManager.registerPlayer(
                    player.uniqueId,
                    player.name,
                    password,
                    player.address?.address?.hostAddress ?: "127.0.0.1"
                ).thenAccept { success: Boolean ->
                    // 返回主线程给玩家发送反馈
                    KaLoginScheduler.runPlayer(player, Runnable {
                        if (success) {
                            plugin.antiCheatManager.markProgrammaticClose(player)
                            plugin.dialogPlatform.close(player)
                            plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("register.success"))
                            // 标记玩家为已登录
                            loggedInPlayers[player.uniqueId] = true
                            val ip = player.address?.address?.hostAddress ?: "127.0.0.1"
                            plugin.welcomeManager.showWelcomeIfNeeded(player) {
                                plugin.antiCheatManager.endAuthenticating(player)
                                plugin.eventActionExecutor.execute(player, "register")
                                plugin.emailBindManager.showPromptIfNeeded(player)
                                KaLoginAPI.getInstance()?.callPlayerRegisterSuccess(player, ip)
                            }
                        } else {
                            plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("register.failed"))
                            // 触发注册失败事件
                            KaLoginAPI.getInstance()?.callPlayerRegisterFailed(player, "Database error")
                        }
                    })
                }
            }

        val errorComponent = plugin.resolveDialogErrorComponent(player, errorMessage)
        plugin.antiCheatManager.markDialogOpened(player)
        plugin.dialogPlatform.showRegister(
            player,
            plugin.messageManager.getComponent("register.dialog-title"),
            null,  // welcomeMessage已移除，可在UI配置文件中自定义
            errorComponent,
            registerAction
        )
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
