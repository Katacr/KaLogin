package org.katacr.kalogin

import fr.xephi.authme.api.v3.AuthMeApi
import fr.xephi.authme.events.LoginEvent
import fr.xephi.authme.events.RegisterEvent
import fr.xephi.authme.events.RestoreSessionEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.katacr.kalogin.dialog.LoginResponse
import org.katacr.kalogin.dialog.RegisterResponse
import org.katacr.kalogin.listener.KaLoginAPI
import org.katacr.kalogin.proxy.ProxySessionManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * AuthMe 模式下的登录监听器
 */
class AuthMeLoginListener(private val plugin: KaLogin) : Listener {

    private val passwordValidator = PasswordValidator(plugin)

    // 跟踪正在处理的玩家，防止重复触发
    private val processingPlayers = ConcurrentHashMap.newKeySet<UUID>()

    // 跟踪玩家的登录错误次数
    private val loginAttempts = ConcurrentHashMap<UUID, Int>()

    // 跟踪上次重新显示对话框的时间（防抖机制）
    private val lastDialogReshowTimes = ConcurrentHashMap<UUID, Long>()

    // 跟踪通过 Session 自动登录的玩家（用于区分手动登录和自动登录）
    private val sessionAutoLoginPlayers = ConcurrentHashMap.newKeySet<UUID>()

    // 跟踪刚完成注册并等待 LoginEvent 收尾的玩家
    private val pendingRegisterPlayers = ConcurrentHashMap.newKeySet<UUID>()

    // 跟踪通过 KaProxy 跨服会话恢复登录的玩家（用于短路 forceLogin 触发的 LoginEvent）
    private val proxyRestoredPlayers = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * 将 AuthMe 回调中的玩家操作切换到该玩家所属的调度线程。
     */
    private fun runOnPlayerThread(player: Player, action: () -> Unit) {
        if (KaLoginScheduler.isPlayerThread(player)) {
            action()
        } else {
            KaLoginScheduler.runPlayer(player, Runnable {
                if (player.isOnline) {
                    action()
                }
            })
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 玩家重新进入服务器时重置登录失败计数，避免上次被踢后再次进服立即触发上限
        loginAttempts.remove(uuid)

        // 防止重复处理
        if (uuid in processingPlayers) {
            return
        }
        processingPlayers.add(uuid)

        val authMeApi = AuthMeApi.getInstance()
        if (authMeApi == null) {
            plugin.logger.warning("AuthMe API not available for player ${player.name}")
            processingPlayers.remove(uuid)
            return
        }

        // 检查玩家是否已注册
        val isRegistered = authMeApi.isRegistered(player.name)
        processingPlayers.remove(uuid)

        // 群组模式：先向 KaProxy 查询是否已有跨服登录会话
        if (plugin.proxySessionManager.isEnabled()) {
            plugin.antiCheatManager.startAuthenticating(player)
            plugin.antiCheatManager.setPlayerDialogType(player, "loading")
            plugin.proxySessionManager.query(player) { result ->
                if (!player.isOnline) return@query
                when (result) {
                    ProxySessionManager.QueryResult.AUTHENTICATED -> restoreProxySession(player)
                    ProxySessionManager.QueryResult.UNAVAILABLE ->
                        if (plugin.proxySessionManager.isRequireProxy()) {
                            kickProxyUnavailable(player)
                        } else {
                            beginLocalFlow(player, isRegistered)
                        }
                    ProxySessionManager.QueryResult.NOT_AUTHENTICATED ->
                        beginLocalFlow(player, isRegistered)
                }
            }
            return
        }

        beginLocalFlow(player, isRegistered)
    }

    /**
     * 走本地的登录/注册界面流程。
     */
    private fun beginLocalFlow(player: Player, isRegistered: Boolean) {
        plugin.authMeManager.initPlayerInDatabase(player)

        if (isRegistered) {
            // 已注册：延迟显示登录对话框
            // 如果玩家已经登录（如 Session 自动登录），LoginEvent 会关闭对话框
            // 如果玩家未登录，对话框会等待玩家输入密码
            showLoginDialogDelayed(player)
        } else {
            // 未注册：延迟显示注册对话框
            showRegisterDialogDelayed(player)
        }
    }

    /**
     * 代理确认已登录：恢复 AuthMe 登录态并跳过登录/注册界面。
     */
    private fun restoreProxySession(player: Player) {
        if (!player.isOnline) return
        val uuid = player.uniqueId
        proxyRestoredPlayers.add(uuid)
        plugin.antiCheatManager.markProgrammaticClose(player)
        plugin.dialogPlatform.close(player)
        loginAttempts.remove(uuid)
        lastDialogReshowTimes.remove(uuid)
        // 恢复 AuthMe 认证状态；触发的 LoginEvent 由 proxyRestoredPlayers 短路
        val forced = runCatching { plugin.authMeManager.forceLogin(player) }.isSuccess
        if (!forced) {
            // AuthMe 无法恢复（例如未共享数据库/未注册），回退本地登录/注册界面
            proxyRestoredPlayers.remove(uuid)
            val registered = AuthMeApi.getInstance()?.isRegistered(player.name) ?: false
            beginLocalFlow(player, registered)
            return
        }
        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
        plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("proxy.session-restored"))
        val tail = Runnable {
            if (!player.isOnline) return@Runnable
            if (plugin.antiCheatManager.isAuthenticating(player)) {
                plugin.antiCheatManager.endAuthenticating(player)
            }
            if (plugin.proxySessionManager.isReplayLoginActions()) {
                plugin.eventActionExecutor.execute(player, "login")
            }
            if (plugin.proxySessionManager.isRestoreEmailPrompt()) {
                plugin.emailBindManager.showPromptIfNeeded(player)
            }
            KaLoginAPI.getInstance()?.callPlayerProxyRestore(player, currentIp)
            plugin.lastSeenManager.onAuthenticated(player)
        }
        if (plugin.proxySessionManager.isRestoreWelcome()) {
            plugin.welcomeManager.showWelcomeIfNeeded(player) { tail.run() }
        } else {
            tail.run()
        }
    }

    /**
     * 代理不可用且配置要求必须走代理时，踢出玩家。
     */
    private fun kickProxyUnavailable(player: Player) {
        plugin.antiCheatManager.markProgrammaticClose(player)
        plugin.dialogPlatform.close(player)
        plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("proxy.unavailable-kick"))
        plugin.antiCheatManager.endAuthenticating(player)
    }

    @EventHandler
    fun onAuthMeLogin(event: LoginEvent) {
        val player = event.player
        runOnPlayerThread(player, authMeLogin@{
            val uuid = player.uniqueId

            // 跨服恢复：forceLogin 触发的 LoginEvent 由恢复流程接管，避免重复处理
            if (proxyRestoredPlayers.remove(uuid)) {
                return@authMeLogin
            }

            // 检查是否是通过 Session 自动登录的
            if (uuid in sessionAutoLoginPlayers) {
                // 这是通过 Session 自动登录的
                sessionAutoLoginPlayers.remove(uuid)
                loginAttempts.remove(uuid)
                val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
                lastDialogReshowTimes.remove(uuid)
                plugin.welcomeManager.showWelcomeIfNeeded(player) {
                    if (plugin.antiCheatManager.isAuthenticating(player)) {
                        plugin.antiCheatManager.endAuthenticating(player)
                    }
                    plugin.eventActionExecutor.execute(player, "login")
                    KaLoginAPI.getInstance()?.callPlayerAutoLogin(player, currentIp)
                    plugin.proxySessionManager.reportAuth(player)
                    plugin.lastSeenManager.onAuthenticated(player)
                    KaLoginScheduler.runPlayerLater(player, 1L, Runnable {
                        if (player.isOnline) {
                            plugin.antiCheatManager.markProgrammaticClose(player)
                            plugin.dialogPlatform.close(player)
                            plugin.emailBindManager.showPromptIfNeeded(player)
                        }
                    })
                }
                return@authMeLogin
            }

            if (completePendingRegisterLogin(player)) {
                return@authMeLogin
            }

            // 这是手动登录
            val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
            loginAttempts.remove(uuid)
            lastDialogReshowTimes.remove(uuid)
            plugin.welcomeManager.showWelcomeIfNeeded(player) {
                if (plugin.antiCheatManager.isAuthenticating(player)) {
                    plugin.antiCheatManager.endAuthenticating(player)
                }
                plugin.eventActionExecutor.execute(player, "login")
                plugin.emailBindManager.showPromptIfNeeded(player)
                KaLoginAPI.getInstance()?.callPlayerLoginSuccess(player, currentIp, false)
                plugin.proxySessionManager.reportAuth(player)
                plugin.lastSeenManager.onAuthenticated(player)
                KaLoginScheduler.runPlayerLater(player, 1L, Runnable {
                    if (player.isOnline) {
                        plugin.antiCheatManager.markProgrammaticClose(player)
                        plugin.dialogPlatform.close(player)
                    }
                })
            }
        })
    }

    /**
     * AuthMe 确认注册完成后再发起登录，避免异步注册和自动登录之间发生竞态。
     */
    @EventHandler
    fun onAuthMeRegister(event: RegisterEvent) {
        val player = event.player
        if (!pendingRegisterPlayers.contains(player.uniqueId)) {
            return
        }

        KaLoginScheduler.runPlayer(player, Runnable {
            if (!player.isOnline || !pendingRegisterPlayers.contains(player.uniqueId)) {
                return@Runnable
            }

            val authMeApi = AuthMeApi.getInstance() ?: return@Runnable
            if (!authMeApi.isRegistered(player.name)) {
                pendingRegisterPlayers.remove(player.uniqueId)
                reopenRegisterDialog(player, plugin.messageManager.getMessage("register.failed"))
                return@Runnable
            }

            authMeApi.forceLogin(player)
        })
    }

    @EventHandler
    fun onRestoreSession(event: RestoreSessionEvent) {
        val player = event.player

        // 标记这个玩家是通过 Session 自动登录的
        sessionAutoLoginPlayers.add(player.uniqueId)

        // 清理防抖记录
        lastDialogReshowTimes.remove(player.uniqueId)
    }

    @EventHandler
    fun onAuthMeLogout(event: fr.xephi.authme.events.LogoutEvent) {
        val player = event.player

        runOnPlayerThread(player) {
            // 玩家通过 AuthMe 登出后直接断开连接，不再重新显示登录界面
            lastDialogReshowTimes.remove(player.uniqueId)
            loginAttempts.remove(player.uniqueId)

            // 触发登出事件
            KaLoginAPI.getInstance()?.callPlayerLogout(player)

            plugin.antiCheatManager.markProgrammaticClose(player)

            // 群组模式：会话失效由 /logout 命令或代理断线处理；
            // AuthMe 的 LogoutEvent 也可能因切服/退服触发，故此处既不销毁会话也不在后端踢出
            if (plugin.proxySessionManager.isEnabled()) {
                return@runOnPlayerThread
            }
            plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("logout.kick-message"))
        }
    }

    @EventHandler
    fun onAuthMeUnregister(event: fr.xephi.authme.events.UnregisterByPlayerEvent) {
        val player = event.player

        runOnPlayerThread(player) {
            // 玩家通过 AuthMe 注销注册，显示注册界面
            // 清理防抖记录，允许立即显示注册对话框
            lastDialogReshowTimes.remove(player.uniqueId)

            // 触发注销账户事件
            KaLoginAPI.getInstance()?.callPlayerUnregister(player)
            plugin.proxySessionManager.reportUnregister(player.uniqueId, player.name)

            // 显示注册对话框
            showRegisterDialog(player)
        }
    }

    @EventHandler
    fun onAuthMeUnregisterByAdmin(event: fr.xephi.authme.events.UnregisterByAdminEvent) {
        val player = event.player
        val playerName = event.playerName

        if (player != null && player.isOnline) {
            runOnPlayerThread(player) {
                // 触发管理员注销账户事件
                KaLoginAPI.getInstance()?.callPlayerAdminUnregister(playerName)
                plugin.proxySessionManager.reportUnregister(player.uniqueId, playerName)

                // 清理防抖记录，允许立即显示注册对话框
                lastDialogReshowTimes.remove(player.uniqueId)

                // 管理员删除玩家数据后，直接将在线玩家踢出，避免继续停留在服务器内
                plugin.antiCheatManager.markProgrammaticClose(player)
                plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("command.delete.kicked"))
            }
        } else {
            KaLoginScheduler.runGlobal(Runnable {
                KaLoginAPI.getInstance()?.callPlayerAdminUnregister(playerName)
                plugin.proxySessionManager.reportUnregister(Bukkit.getOfflinePlayer(playerName).uniqueId, playerName)
            })
        }
    }

    /**
     * 显示登录对话框
     */
    private fun showLoginDialog(player: Player, errorMessage: String? = null) {
        if (!plugin.antiCheatManager.isAuthenticating(player)) {
            plugin.antiCheatManager.startAuthenticating(player)
        }
        plugin.antiCheatManager.setPlayerDialogType(player, "login")

        // 防抖机制：2秒内不重复显示对话框
        val now = System.currentTimeMillis()
        val lastReshowTime = lastDialogReshowTimes[player.uniqueId] ?: 0
        if (now - lastReshowTime < 2000) {
            return
        }
        lastDialogReshowTimes[player.uniqueId] = now

        val maxAttempts = plugin.config.getInt("login.max-login-attempts", 3)
        val attemptsLeft = maxAttempts - (loginAttempts[player.uniqueId] ?: 0)

        if (attemptsLeft <= 0) {
            plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("login.too-many-attempts"))
            return
        }

        val authMeApi = AuthMeApi.getInstance() ?: return

        val loginAction: (LoginResponse) -> Unit = loginAction@{ response ->
                val password = response.password
                val autoLoginCheckbox = response.autoLoginByIp

                if (password.isNullOrBlank()) {
                    reopenLoginDialog(player, plugin.messageManager.getMessage("login.password-empty"))
                    return@loginAction
                }

                // 使用 AuthMe API 验证密码
                try {
                    if (authMeApi.checkPassword(player.name, password)) {
                        // 验证成功，强制登录
                        authMeApi.forceLogin(player)
                        plugin.antiCheatManager.markProgrammaticClose(player)
                        plugin.dialogPlatform.close(player)
                        plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("login.success"))

                        // 更新数据库：最后登录 IP 和自动登录设置
                        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
                        plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
                        plugin.dbManager.updateAutoLoginByIp(player.uniqueId, autoLoginCheckbox)

                        // 清理防抖记录
                        lastDialogReshowTimes.remove(player.uniqueId)

                    } else {
                        // 密码错误
                        val currentAttempts = (loginAttempts[player.uniqueId] ?: 0) + 1
                        loginAttempts[player.uniqueId] = currentAttempts

                        val remainingAttempts = maxAttempts - currentAttempts
                        if (remainingAttempts > 0) {
                            // 触发登录失败事件
                            KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, remainingAttempts)
                            reopenLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to remainingAttempts))
                        } else {
                            plugin.messageManager.kickPlayer(player, plugin.messageManager.getComponent("login.too-many-attempts"))
                            // 触发登录失败事件（剩余次数为0）
                            KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, 0)
                        }
                    }
                } catch (e: Exception) {
                    // 捕获 AuthMe 验证异常
                    plugin.logger.warning("AuthMe login check failed for player ${player.name}: ${e.message}")
                    e.printStackTrace()

                    // 触发登录失败事件
                    KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, maxAttempts - (loginAttempts[player.uniqueId] ?: 0))

                    // 显示通用错误消息
                    reopenLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to (maxAttempts - (loginAttempts[player.uniqueId] ?: 0))))
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

    private fun reopenLoginDialog(player: Player, errorMessage: String? = null) {
        lastDialogReshowTimes.remove(player.uniqueId)
        plugin.antiCheatManager.markDialogClosed(player)
        showLoginDialog(player, errorMessage)
    }

    private fun completePendingRegisterLogin(player: Player): Boolean {
        val uuid = player.uniqueId
        if (!pendingRegisterPlayers.remove(uuid)) {
            return false
        }

        loginAttempts.remove(uuid)
        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
        lastDialogReshowTimes.remove(uuid)
        plugin.welcomeManager.showWelcomeIfNeeded(player) {
            plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("register.success"))
            if (plugin.antiCheatManager.isAuthenticating(player)) {
                plugin.antiCheatManager.endAuthenticating(player)
            }
            plugin.eventActionExecutor.execute(player, "register")
            plugin.emailBindManager.showPromptIfNeeded(player)
            KaLoginAPI.getInstance()?.callPlayerRegisterSuccess(player, currentIp)
            plugin.proxySessionManager.reportAuth(player)
            plugin.lastSeenManager.onAuthenticated(player)
            KaLoginScheduler.runPlayerLater(player, 1L, Runnable {
                if (player.isOnline) {
                    plugin.antiCheatManager.markProgrammaticClose(player)
                    plugin.dialogPlatform.close(player)
                }
            })
        }
        return true
    }

    fun showLoginDialogFromExternal(player: Player, errorMessage: String? = null) {
        showLoginDialog(player, errorMessage)
    }

    fun showRegisterDialogFromExternal(player: Player, errorMessage: String? = null) {
        showRegisterDialog(player, errorMessage)
    }

    /**
     * 显示注册对话框
     */
    private fun showRegisterDialog(player: Player, errorMessage: String? = null) {
        if (!plugin.antiCheatManager.isAuthenticating(player)) {
            plugin.antiCheatManager.startAuthenticating(player)
        }
        plugin.antiCheatManager.setPlayerDialogType(player, "register")

        // 防抖机制：2秒内不重复显示对话框
        val now = System.currentTimeMillis()
        val lastReshowTime = lastDialogReshowTimes[player.uniqueId] ?: 0
        if (now - lastReshowTime < 2000) {
            return
        }
        lastDialogReshowTimes[player.uniqueId] = now

        val authMeApi = AuthMeApi.getInstance() ?: return

        val registerAction: (RegisterResponse) -> Unit = registerAction@{ response ->
                val password = response.password
                val confirmPassword = response.confirmPassword

                if (password.isNullOrBlank()) {
                    reopenRegisterDialog(player, plugin.messageManager.getMessage("register.password-empty"))
                    return@registerAction
                }

                val validationError = passwordValidator.validate(password)
                if (validationError != null) {
                    reopenRegisterDialog(player, plugin.messageManager.getMessage("register.password-invalid", "error" to validationError))
                    return@registerAction
                }

                if (password != confirmPassword) {
                    reopenRegisterDialog(player, plugin.messageManager.getMessage("register.password-mismatch"))
                    return@registerAction
                }

                plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("register.saving"))
                val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
                plugin.dbManager.initPlayerForAuthMe(player.uniqueId, player.name, currentIp).thenAccept { dbSaved ->
                    KaLoginScheduler.runPlayer(player, Runnable {
                        if (!player.isOnline) return@Runnable

                        try {
                            pendingRegisterPlayers.add(player.uniqueId)
                            loginAttempts.remove(player.uniqueId)
                            authMeApi.forceRegister(player, password, false)
                            plugin.antiCheatManager.markProgrammaticClose(player)
                            plugin.dialogPlatform.close(player)
                            lastDialogReshowTimes.remove(player.uniqueId)

                            if (!dbSaved) {
                                plugin.logger.warning("AuthMe registration succeeded for ${player.name}, but KaLogin database init failed")
                            }

                        } catch (e: Exception) {
                            pendingRegisterPlayers.remove(player.uniqueId)
                            // 捕获 AuthMe 注册异常，获取具体错误信息
                            plugin.logger.warning("AuthMe registration failed for player ${player.name}: ${e.message}")
                            e.printStackTrace()

                            // 触发注册失败事件
                            KaLoginAPI.getInstance()?.callPlayerRegisterFailed(player, e.message ?: "Unknown error")

                            // 根据异常类型显示不同的错误消息
                            val errorMessage = when {
                                e.message?.contains("already registered", ignoreCase = true) == true -> {
                                    plugin.messageManager.getMessage("register.failed")
                                }
                                e.message?.contains("too many accounts", ignoreCase = true) == true -> {
                                    plugin.messageManager.getMessage("ip-limit.exceeded", "count" to plugin.config.getInt("ip-limit.max-accounts", 3))
                                }
                                else -> {
                                    plugin.messageManager.getMessage("register.failed")
                                }
                            }

                            reopenRegisterDialog(player, errorMessage)
                        }
                    })
                }
            }

        val errorComponent = plugin.resolveDialogErrorComponent(player, errorMessage)
        plugin.antiCheatManager.markDialogOpened(player)
        plugin.dialogPlatform.showRegister(
            player,
            plugin.messageManager.getComponent("register.dialog-title"),
            null,
            errorComponent,
            registerAction
        )
    }

    private fun reopenRegisterDialog(player: Player, errorMessage: String? = null) {
        lastDialogReshowTimes.remove(player.uniqueId)
        plugin.antiCheatManager.markDialogClosed(player)
        showRegisterDialog(player, errorMessage)
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
    private fun showRegisterDialogDelayed(player: Player) {
        val delayTicks = plugin.config.getLong("login.dialog-delay-ticks", 0)
        if (delayTicks > 0) {
            KaLoginScheduler.runPlayerLater(player, delayTicks, Runnable {
                if (player.isOnline) {
                    showRegisterDialog(player)
                }
            })
        } else {
            showRegisterDialog(player)
        }
    }
}
