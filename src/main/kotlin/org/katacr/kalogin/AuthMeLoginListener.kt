package org.katacr.kalogin

import fr.xephi.authme.api.v3.AuthMeApi
import fr.xephi.authme.events.LoginEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
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

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 防止重复处理
        if (uuid in processingPlayers) {
            return
        }
        processingPlayers.add(uuid)

        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
        val authMeApi = AuthMeApi.getInstance()

        if (authMeApi == null) {
            plugin.logger.warning("AuthMe API not available for player ${player.name}")
            processingPlayers.remove(uuid)
            return
        }

        // 检查玩家是否已注册
        val isRegistered = authMeApi.isRegistered(player.name)

        if (isRegistered) {
            // 玩家已注册，检查是否已登录
            val isAuthenticated = authMeApi.isAuthenticated(player)

            if (isAuthenticated) {
                // 已登录，不需要处理
                processingPlayers.remove(uuid)
                return
            }

            // 检查是否可以自动登录（IP 相同且勾选了自动登录）
            plugin.dbManager.canAutoLogin(player.uniqueId, currentIp).thenAccept { canAutoLogin ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    processingPlayers.remove(uuid)

                    if (canAutoLogin) {
                        // 自动登录前再次检查是否已登录
                        // 延迟 10 tick (0.5秒) 执行，等待 AuthMe Session 处理完成
                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                            val isAuthenticatedAgain = authMeApi.isAuthenticated(player)

                            if (isAuthenticatedAgain) {
                                // 已经通过其他方式登录（如 Session），不需要自动登录
                                return@Runnable
                            }

                            // 自动登录
                            try {
                                // 清理防抖记录，防止 LoginEvent 触发时冲突
                                lastDialogReshowTimes.remove(player.uniqueId)

                                // 先保存登录状态
                                val wasAuthenticatedBefore = authMeApi.isAuthenticated(player)

                                authMeApi.forceLogin(player)

                                // 检查是否真的登录了
                                val isAuthenticatedAfter = authMeApi.isAuthenticated(player)

                                if (isAuthenticatedAfter && !wasAuthenticatedBefore) {
                                    // 成功登录
                                    player.sendMessage(plugin.messageManager.getComponent("login.auto-login-success"))
                                }
                            } catch (e: Exception) {
                                // 自动登录失败，显示登录对话框
                                plugin.logger.warning("Auto-login failed for player ${player.name}: ${e.message}")
                                e.printStackTrace()
                                showLoginDialog(player)
                            }
                        }, 10L)
                    } else {
                        // 不能自动登录，显示登录对话框
                        showLoginDialog(player)
                    }
                })
            }
        } else {
            // 未注册，显示注册对话框
            processingPlayers.remove(uuid)
            showRegisterDialog(player)
        }
    }

    @EventHandler
    fun onAuthMeLogin(event: LoginEvent) {
        val player = event.player

        // 玩家通过 AuthMe 登录成功，关闭登录界面
        // 清理防抖记录
        lastDialogReshowTimes.remove(player.uniqueId)

        // 关闭对话框
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                player.closeInventory()
            }
        }, 1L)
    }

    @EventHandler
    fun onAuthMeLogout(event: fr.xephi.authme.events.LogoutEvent) {
        val player = event.player

        // 玩家通过 AuthMe 登出，显示登录界面
        // 清理防抖记录，允许立即显示登录对话框
        lastDialogReshowTimes.remove(player.uniqueId)

        // 显示登录对话框
        showLoginDialog(player)
    }

    @EventHandler
    fun onAuthMeUnregister(event: fr.xephi.authme.events.UnregisterByPlayerEvent) {
        val player = event.player

        // 玩家通过 AuthMe 注销注册，显示注册界面
        // 清理防抖记录，允许立即显示注册对话框
        lastDialogReshowTimes.remove(player.uniqueId)

        // 显示注册对话框
        showRegisterDialog(player)
    }

    @EventHandler
    fun onAuthMeUnregisterByAdmin(event: fr.xephi.authme.events.UnregisterByAdminEvent) {
        val player = event.player

        // 管理员通过 AuthMe 注销玩家，通知在线玩家显示注册界面
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
                    } else {
                        // 密码错误
                        val currentAttempts = (loginAttempts[player.uniqueId] ?: 0) + 1
                        loginAttempts[player.uniqueId] = currentAttempts

                        val remainingAttempts = maxAttempts - currentAttempts
                        if (remainingAttempts > 0) {
                            showLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to remainingAttempts))
                        } else {
                            player.kick(plugin.messageManager.getComponent("login.too-many-attempts"))
                        }
                    }
                } catch (e: Exception) {
                    // 捕获 AuthMe 验证异常
                    plugin.logger.warning("AuthMe login check failed for player ${player.name}: ${e.message}")
                    e.printStackTrace()

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
                    } else {
                        // 注册失败，重新显示注册界面
                        showRegisterDialog(player, plugin.messageManager.getMessage("register.failed"))
                    }
                } catch (e: Exception) {
                    // 捕获 AuthMe 注册异常，获取具体错误信息
                    plugin.logger.warning("AuthMe registration failed for player ${player.name}: ${e.message}")
                    e.printStackTrace()

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
}
