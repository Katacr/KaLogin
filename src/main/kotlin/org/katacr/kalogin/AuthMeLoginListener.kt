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

        plugin.logger.info("[DEBUG] Player ${player.name} joined, UUID: $uuid")

        // 防止重复处理
        if (uuid in processingPlayers) {
            plugin.logger.info("[DEBUG] Player ${player.name} is already in processingPlayers, skipping")
            return
        }
        processingPlayers.add(uuid)

        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
        val authMeApi = AuthMeApi.getInstance()

        plugin.logger.info("[DEBUG] Player ${player.name} IP: $currentIp")

        if (authMeApi == null) {
            plugin.logger.warning("[DEBUG] AuthMe API not available for player ${player.name}")
            processingPlayers.remove(uuid)
            return
        }

        // 检查玩家是否已注册
        val isRegistered = authMeApi.isRegistered(player.name)
        plugin.logger.info("[DEBUG] Player ${player.name} isRegistered: $isRegistered")

        if (isRegistered) {
            // 玩家已注册，检查是否已登录
            val isAuthenticated = authMeApi.isAuthenticated(player)
            plugin.logger.info("[DEBUG] Player ${player.name} isAuthenticated: $isAuthenticated")

            if (isAuthenticated) {
                // 已登录，不需要处理
                plugin.logger.info("[DEBUG] Player ${player.name} already logged in, skipping")
                processingPlayers.remove(uuid)
                return
            }

            // 检查是否可以自动登录（IP 相同且勾选了自动登录）
            plugin.logger.info("[DEBUG] Checking auto-login for player ${player.name}")
            plugin.dbManager.canAutoLogin(player.uniqueId, currentIp).thenAccept { canAutoLogin ->
                plugin.logger.info("[DEBUG] canAutoLogin result for player ${player.name}: $canAutoLogin")

                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.logger.info("[DEBUG] Processing auto-login for player ${player.name}")
                    processingPlayers.remove(uuid)

                    if (canAutoLogin) {
                        plugin.logger.info("[DEBUG] Player ${player.name} can auto-login, scheduling")
                        // 自动登录前再次检查是否已登录
                        // 延迟 10 tick (0.5秒) 执行，等待 AuthMe Session 处理完成
                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                            val isAuthenticatedAgain = authMeApi.isAuthenticated(player)
                            plugin.logger.info("[DEBUG] Re-checking authentication for player ${player.name}: $isAuthenticatedAgain")

                            if (isAuthenticatedAgain) {
                                // 已经通过其他方式登录（如 Session），不需要自动登录
                                plugin.logger.info("[DEBUG] Player ${player.name} already authenticated after delay (possibly by Session), skipping auto-login")
                                return@Runnable
                            }

                            // 自动登录
                            try {
                                plugin.logger.info("[DEBUG] Force logging in player ${player.name}")

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
                                    plugin.logger.info("[DEBUG] Force login completed for player ${player.name}")
                                } else if (wasAuthenticatedBefore) {
                                    // 登录前就已经登录
                                    plugin.logger.info("[DEBUG] Player ${player.name} was already logged in, skipped")
                                } else {
                                    // 未能成功登录
                                    plugin.logger.warning("[DEBUG] Force login may have failed for player ${player.name}")
                                }
                            } catch (e: Exception) {
                                // 自动登录失败，显示登录对话框
                                plugin.logger.warning("[DEBUG] Auto-login failed for player ${player.name}: ${e.message}")
                                e.printStackTrace()
                                showLoginDialog(player)
                            }
                        }, 10L)
                    } else {
                        // 不能自动登录，显示登录对话框
                        plugin.logger.info("[DEBUG] Player ${player.name} cannot auto-login, showing login dialog")
                        showLoginDialog(player)
                    }
                })
            }
        } else {
            // 未注册，显示注册对话框
            plugin.logger.info("[DEBUG] Player ${player.name} not registered, showing register dialog")
            processingPlayers.remove(uuid)
            showRegisterDialog(player)
        }
    }

    @EventHandler
    fun onAuthMeLogin(event: LoginEvent) {
        val player = event.player

        // 玩家通过 AuthMe 登录成功，关闭登录界面
        plugin.logger.info("[DEBUG] LoginEvent fired for player ${player.name}")

        // 清理防抖记录
        lastDialogReshowTimes.remove(player.uniqueId)
        plugin.logger.info("[DEBUG] Cleared debounce record for player ${player.name}")

        // 关闭对话框
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                player.closeInventory()
                plugin.logger.info("[DEBUG] Closed dialog for player ${player.name}")
            }
        }, 1L)
    }

    @EventHandler
    fun onAuthMeLogout(event: fr.xephi.authme.events.LogoutEvent) {
        val player = event.player

        // 玩家通过 AuthMe 登出，显示登录界面
        plugin.logger.info("[DEBUG] LogoutEvent fired for player ${player.name}")

        // 清理防抖记录，允许立即显示登录对话框
        lastDialogReshowTimes.remove(player.uniqueId)
        plugin.logger.info("[DEBUG] Cleared debounce record for player ${player.name} on logout")

        // 显示登录对话框
        plugin.logger.info("[DEBUG] Showing login dialog for player ${player.name}")
        showLoginDialog(player)
    }

    @EventHandler
    fun onAuthMeUnregister(event: fr.xephi.authme.events.UnregisterByPlayerEvent) {
        val player = event.player

        // 玩家通过 AuthMe 注销注册，显示注册界面
        plugin.logger.info("[DEBUG] UnregisterByPlayerEvent fired for player ${player.name}")

        // 清理防抖记录，允许立即显示注册对话框
        lastDialogReshowTimes.remove(player.uniqueId)
        plugin.logger.info("[DEBUG] Cleared debounce record for player ${player.name} on unregister")

        // 显示注册对话框
        plugin.logger.info("[DEBUG] Showing register dialog for player ${player.name}")
        showRegisterDialog(player)
    }

    @EventHandler
    fun onAuthMeUnregisterByAdmin(event: fr.xephi.authme.events.UnregisterByAdminEvent) {
        val player = event.player
        val playerName = event.playerName

        // 管理员通过 AuthMe 注销玩家，通知在线玩家显示注册界面
        plugin.logger.info("[DEBUG] UnregisterByAdminEvent fired for player $playerName")

        // 如果玩家在线，显示注册界面
        if (player != null && player.isOnline) {
            // 清理防抖记录，允许立即显示注册对话框
            lastDialogReshowTimes.remove(player.uniqueId)
            plugin.logger.info("[DEBUG] Cleared debounce record for player $playerName on admin unregister")

            // 显示注册对话框
            plugin.logger.info("[DEBUG] Showing register dialog for player $playerName")
            showRegisterDialog(player)
        }
    }

    /**
     * 显示登录对话框
     */
    private fun showLoginDialog(player: Player, errorMessage: String? = null) {
        plugin.logger.info("[DEBUG] showLoginDialog called for player ${player.name}, error: $errorMessage")

        // 防抖机制：2秒内不重复显示对话框
        val now = System.currentTimeMillis()
        val lastReshowTime = lastDialogReshowTimes[player.uniqueId] ?: 0
        if (now - lastReshowTime < 2000) {
            plugin.logger.info("[DEBUG] Dialog suppressed for player ${player.name} by debounce mechanism")
            return
        }
        lastDialogReshowTimes[player.uniqueId] = now
        plugin.logger.info("[DEBUG] Setting debounce record for player ${player.name}")

        val maxAttempts = plugin.config.getInt("login.max-login-attempts", 3)
        val attemptsLeft = maxAttempts - (loginAttempts[player.uniqueId] ?: 0)

        if (attemptsLeft <= 0) {
            plugin.logger.info("[DEBUG] Player ${player.name} exceeded max attempts, kicking")
            player.kick(plugin.messageManager.getComponent("login.too-many-attempts"))
            return
        }

        val authMeApi = AuthMeApi.getInstance() ?: return

        val loginAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val password = response.getText("login_password")
                val autoLoginCheckbox = response.getBoolean("auto_login_by_ip") ?: false

                plugin.logger.info("[DEBUG] Login dialog submitted for player ${player.name}")

                if (password.isNullOrBlank()) {
                    plugin.logger.info("[DEBUG] Password empty for player ${player.name}")
                    showLoginDialog(player, plugin.messageManager.getMessage("login.password-empty"))
                    return@DialogActionCallback
                }

                // 使用 AuthMe API 验证密码
                try {
                    if (authMeApi.checkPassword(player.name, password)) {
                        plugin.logger.info("[DEBUG] Password correct for player ${player.name}")

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
                        plugin.logger.info("[DEBUG] Password incorrect for player ${player.name}")
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
                    plugin.logger.warning("[DEBUG] AuthMe login check failed for player ${player.name}: ${e.message}")
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
        plugin.logger.info("[DEBUG] Login dialog shown for player ${player.name}")
    }

    /**
     * 显示注册对话框
     */
    private fun showRegisterDialog(player: Player, errorMessage: String? = null) {
        plugin.logger.info("[DEBUG] showRegisterDialog called for player ${player.name}, error: $errorMessage")

        // 防抖机制：2秒内不重复显示对话框
        val now = System.currentTimeMillis()
        val lastReshowTime = lastDialogReshowTimes[player.uniqueId] ?: 0
        if (now - lastReshowTime < 2000) {
            plugin.logger.info("[DEBUG] Register dialog suppressed for player ${player.name} by debounce mechanism")
            return
        }
        lastDialogReshowTimes[player.uniqueId] = now
        plugin.logger.info("[DEBUG] Setting debounce record for player ${player.name} (register)")

        val authMeApi = AuthMeApi.getInstance() ?: return

        val registerAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val password = response.getText("reg_password")
                val confirmPassword = response.getText("reg_confirm_password")

                plugin.logger.info("[DEBUG] Register dialog submitted for player ${player.name}")

                if (password.isNullOrBlank()) {
                    plugin.logger.info("[DEBUG] Password empty for player ${player.name} (register)")
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-empty"))
                    return@DialogActionCallback
                }

                if (password != confirmPassword) {
                    plugin.logger.info("[DEBUG] Password mismatch for player ${player.name}")
                    showRegisterDialog(player, plugin.messageManager.getMessage("register.password-mismatch"))
                    return@DialogActionCallback
                }

                // 使用 AuthMe API 注册
                try {
                    plugin.logger.info("[DEBUG] Attempting AuthMe registration for player ${player.name}")
                    val success = authMeApi.registerPlayer(player.name, password)
                    if (success) {
                        plugin.logger.info("[DEBUG] Registration successful for player ${player.name}")

                        // 注册成功，强制登录
                        authMeApi.forceLogin(player)
                        player.sendMessage(plugin.messageManager.getComponent("register.success"))

                        // 初始化数据库记录
                        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"
                        plugin.dbManager.initPlayerForAuthMe(player.uniqueId, player.name, currentIp)

                        // 清理防抖记录
                        lastDialogReshowTimes.remove(player.uniqueId)
                    } else {
                        plugin.logger.info("[DEBUG] Registration failed for player ${player.name}")
                        // 注册失败，重新显示注册界面
                        showRegisterDialog(player, plugin.messageManager.getMessage("register.failed"))
                    }
                } catch (e: Exception) {
                    // 捕获 AuthMe 注册异常，获取具体错误信息
                    plugin.logger.warning("[DEBUG] AuthMe registration failed for player ${player.name}: ${e.message}")
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
        plugin.logger.info("[DEBUG] Register dialog shown for player ${player.name}")
    }
}
