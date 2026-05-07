package org.katacr.kalogin

import fr.xephi.authme.api.v3.AuthMeApi
import fr.xephi.authme.events.LoginEvent
import fr.xephi.authme.events.RestoreSessionEvent
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

/**
 * AuthMe 模式下的登录监听器
 */
class AuthMeLoginListener(private val plugin: KaLogin) : Listener {

    // 跟踪正在处理的玩家，防止重复触发
    private val processingPlayers = mutableSetOf<java.util.UUID>()

    // 跟踪玩家的登录错误次数
    private val loginAttempts = mutableMapOf<java.util.UUID, Int>()

    // 跟踪上次重新显示对话框的时间（防抖机制）
    private val lastDialogReshowTimes = mutableMapOf<java.util.UUID, Long>()

    // 跟踪通过 Session 自动登录的玩家（用于区分手动登录和自动登录）
    private val sessionAutoLoginPlayers = mutableSetOf<java.util.UUID>()

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

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

    @EventHandler
    fun onAuthMeLogin(event: LoginEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 检查是否是通过 Session 自动登录的
        if (uuid in sessionAutoLoginPlayers) {
            // 这是通过 Session 自动登录的
            sessionAutoLoginPlayers.remove(uuid)

            // 触发自动登录事件
            val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
            KaLoginAPI.getInstance()?.callPlayerAutoLogin(player, currentIp)

            // 清理防抖记录
            lastDialogReshowTimes.remove(uuid)

            // 关闭对话框
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (player.isOnline) {
                    player.closeInventory()
                }
            }, 1L)
            return
        }

        // 这是手动登录
        // 清理防抖记录
        lastDialogReshowTimes.remove(uuid)

        // 关闭对话框
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                player.closeInventory()
            }
        }, 1L)
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

        // 玩家通过 AuthMe 登出，显示登录界面
        // 清理防抖记录，允许立即显示登录对话框
        lastDialogReshowTimes.remove(player.uniqueId)

        // 触发登出事件
        KaLoginAPI.getInstance()?.callPlayerLogout(player)

        // 显示登录对话框
        showLoginDialog(player)
    }

    @EventHandler
    fun onAuthMeUnregister(event: fr.xephi.authme.events.UnregisterByPlayerEvent) {
        val player = event.player

        // 玩家通过 AuthMe 注销注册，显示注册界面
        // 清理防抖记录，允许立即显示注册对话框
        lastDialogReshowTimes.remove(player.uniqueId)

        // 触发注销账户事件
        KaLoginAPI.getInstance()?.callPlayerUnregister(player)

        // 显示注册对话框
        showRegisterDialog(player)
    }

    @EventHandler
    fun onAuthMeUnregisterByAdmin(event: fr.xephi.authme.events.UnregisterByAdminEvent) {
        val player = event.player
        val playerName = event.playerName

        // 触发管理员注销账户事件
        KaLoginAPI.getInstance()?.callPlayerAdminUnregister(playerName)

        // 如果玩家在线，显示注册界面
        if (player != null && player.isOnline) {
            // 清理防抖记录，允许立即显示注册对话框
            lastDialogReshowTimes.remove(player.uniqueId)

            // 显示注册对话框
            showRegisterDialog(player)
        }
    }

    /**
     * 显示登录对话框
     */
    private fun showLoginDialog(player: Player, errorMessage: String? = null) {
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
            player.kick(plugin.messageManager.getComponent("login.too-many-attempts"))
            return
        }

        val authMeApi = AuthMeApi.getInstance() ?: return

        val loginAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val password = response.getText("login_password")
                val autoLoginCheckbox = response.getBoolean("auto_login_by_ip") ?: false

                if (password.isNullOrBlank()) {
                    showLoginDialog(player, plugin.messageManager.getMessage("login.password-empty"))
                    return@DialogActionCallback
                }

                // 使用 AuthMe API 验证密码
                try {
                    if (authMeApi.checkPassword(player.name, password)) {
                        // 验证成功，强制登录
                        authMeApi.forceLogin(player)
                        player.sendMessage(plugin.messageManager.getComponent("login.success"))

                        // 更新数据库：最后登录 IP 和自动登录设置
                        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
                        plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
                        plugin.dbManager.updateAutoLoginByIp(player.uniqueId, autoLoginCheckbox)

                        // 清理防抖记录
                        lastDialogReshowTimes.remove(player.uniqueId)

                        // 触发登录成功事件
                        KaLoginAPI.getInstance()?.callPlayerLoginSuccess(player, currentIp, false)
                    } else {
                        // 密码错误
                        val currentAttempts = (loginAttempts[player.uniqueId] ?: 0) + 1
                        loginAttempts[player.uniqueId] = currentAttempts

                        val remainingAttempts = maxAttempts - currentAttempts
                        if (remainingAttempts > 0) {
                            // 触发登录失败事件
                            KaLoginAPI.getInstance()?.callPlayerLoginFailed(player, remainingAttempts)
                            showLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to remainingAttempts))
                        } else {
                            player.kick(plugin.messageManager.getComponent("login.too-many-attempts"))
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
                    showLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to (maxAttempts - (loginAttempts[player.uniqueId] ?: 0))))
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
            null,
            errorComponent,
            confirmButton
        )
        player.showDialog(dialog)
    }

    /**
     * 显示注册对话框
     */
    private fun showRegisterDialog(player: Player, errorMessage: String? = null) {
        // 防抖机制：2秒内不重复显示对话框
        val now = System.currentTimeMillis()
        val lastReshowTime = lastDialogReshowTimes[player.uniqueId] ?: 0
        if (now - lastReshowTime < 2000) {
            return
        }
        lastDialogReshowTimes[player.uniqueId] = now

        val authMeApi = AuthMeApi.getInstance() ?: return

        val registerAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val password = response.getText("reg_password")
                val confirmPassword = response.getText("reg_confirm_password")

                if (password.isNullOrBlank()) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-empty"))
                    return@DialogActionCallback
                }

                if (password != confirmPassword) {
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-mismatch"))
                    return@DialogActionCallback
                }

                // 使用 AuthMe API 注册
                try {
                    val success = authMeApi.registerPlayer(player.name, password)
                    if (success) {
                        // 注册成功，强制登录
                        authMeApi.forceLogin(player)
                        player.sendMessage(plugin.messageManager.getComponent("register.success"))

                        // 初始化数据库记录
                        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
                        plugin.dbManager.initPlayerForAuthMe(player.uniqueId, player.name, currentIp)

                        // 清理防抖记录
                        lastDialogReshowTimes.remove(player.uniqueId)

                        // 触发注册成功事件
                        KaLoginAPI.getInstance()?.callPlayerRegisterSuccess(player, currentIp)
                    } else {
                        // 注册失败，重新显示注册界面
                        // 触发注册失败事件
                        KaLoginAPI.getInstance()?.callPlayerRegisterFailed(player, "Registration failed")
                        showRegisterDialog(player, plugin.messageManager.getMessage("register.failed"))
                    }
                } catch (e: Exception) {
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

                    showRegisterDialog(player, errorMessage)
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
            null,
            errorComponent,
            confirmButton
        )
        player.showDialog(dialog)
    }

    /**
     * 根据配置延迟显示登录对话框
     * 如果 dialog-delay-ticks <= 0，立即显示；否则延迟指定 tick 数
     */
    private fun showLoginDialogDelayed(player: Player) {
        val delayTicks = plugin.config.getLong("login.dialog-delay-ticks", 0)
        if (delayTicks > 0) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (player.isOnline) {
                    showLoginDialog(player)
                }
            }, delayTicks)
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
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (player.isOnline) {
                    showRegisterDialog(player)
                }
            }, delayTicks)
        } else {
            showRegisterDialog(player)
        }
    }
}
