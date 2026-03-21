package org.katacr.kalogin

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
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


    /**
     * 获取本地化的对话框标题
     */
    private fun getLocalizedTitle(key: String): Component {
        return plugin.messageManager.getComponent(key)
    }

    /**
     * 获取本地化的对话框描述
     */
    private fun getLocalizedDescription(key: String, vararg args: Pair<String, Any>): Component {
        return plugin.messageManager.getComponent(key, *args)
    }

    /**
     * 获取本地化的按钮文本
     */
    private fun getLocalizedButton(key: String): Component {
        return plugin.messageManager.getComponent(key)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val currentIp = player.address?.address?.hostAddress ?: "127.0.0.1"

        // 重置登录错误次数
        loginAttempts.remove(player.uniqueId)

        // 检查玩家是否已注册
        plugin.dbManager.isPlayerRegistered(player.uniqueId).thenAccept { registered ->
            if (registered) {
                // 已注册，检查是否启用相同 IP 自动登录
                val autoLoginByIp = plugin.config.getBoolean("login.auto-login-by-ip", true)
                if (autoLoginByIp) {
                    plugin.dbManager.isSameLastIp(player.uniqueId, currentIp).thenAccept { sameIp ->
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            if (sameIp) {
                                // IP 相同，自动登录
                                player.sendMessage(plugin.messageManager.getComponent("login.auto-login-success"))
                                loggedInPlayers[player.uniqueId] = true
                                plugin.dbManager.updateLastLoginIp(player.uniqueId, currentIp)
                                // 结束防作弊状态
                                plugin.antiCheatManager.endAuthenticating(player)
                            } else {
                                // IP 不同，显示登录对话框
                                showLoginDialog(player)
                            }
                        })
                    }
                } else {
                    // 未启用自动登录，显示登录对话框
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        showLoginDialog(player)
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
    private fun showLoginDialog(player: Player, errorMessage: String? = null) {
        // 开始防作弊状态（仅在首次调用时）
        if (!plugin.antiCheatManager.isAuthenticating(player)) {
            plugin.antiCheatManager.startAuthenticating(player)
        }

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
                                // 结束防作弊状态
                                plugin.antiCheatManager.endAuthenticating(player)
                            } else {
                            val currentAttempts = (loginAttempts[player.uniqueId] ?: 0) + 1
                            loginAttempts[player.uniqueId] = currentAttempts

                            val remainingAttempts = maxAttempts - currentAttempts
                            if (remainingAttempts > 0) {
                                showLoginDialog(player, plugin.messageManager.getMessage("login.password-wrong", "attempts" to remainingAttempts))
                            } else {
                                player.kick(plugin.messageManager.getComponent("login.too-many-attempts"))
                                plugin.antiCheatManager.endAuthenticating(player)
                            }
                        }
                    })
                }
            },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
        )

        val dialog = buildInputDialog(
            titleKey = "login.dialog-title",
            welcomeMessageKey = "login.welcome-message",
            descriptionKey = "login.dialog-description",
            descriptionArgs = arrayOf("attempts" to attemptsLeft),
            inputId = "login_password",
            buttonKey = "login.dialog-button",
            action = loginAction,
            errorMessage = errorMessage
        )
        player.showDialog(dialog)
    }


    /**
     * 显示注册对话框（包含密码和确认密码两个输入框）
     */
    private fun showRegisterDialog(player: Player, description: String, errorMessage: String? = null) {
        // 开始防作弊状态（仅在第一次调用时）
        if (!plugin.antiCheatManager.isAuthenticating(player)) {
            plugin.antiCheatManager.startAuthenticating(player)
        }

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
                            // 结束防作弊状态
                            plugin.antiCheatManager.endAuthenticating(player)
                        } else {
                            player.sendMessage(plugin.messageManager.getComponent("register.failed"))
                        }
                    })
                }
            },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
        )

        val dialog = buildRegisterDialog(
            titleKey = "register.dialog-title",
            welcomeMessageKey = "register.welcome-message",
            descriptionKey = "register.dialog-description",
            descriptionArgs = arrayOf("description" to description),
            buttonKey = "register.dialog-button",
            action = registerAction,
            errorMessage = errorMessage
        )
        player.showDialog(dialog)
    }


    /**
     * 提取出的通用对话框构建方法（支持本地化，单个输入框）
     */
    private fun buildInputDialog(
        titleKey: String,
        welcomeMessageKey: String? = null,
        descriptionKey: String,
        descriptionArgs: Array<Pair<String, Any>> = emptyArray(),
        inputId: String,
        buttonKey: String,
        action: DialogAction,
        errorMessage: String? = null
    ): Dialog {
        val confirmButton = ActionButton.builder(getLocalizedButton(buttonKey))
            .action(action)
            .build()

        val bodyList = mutableListOf<DialogBody>()

        // 添加欢迎消息（如果有）- 放在最上方
        if (welcomeMessageKey != null) {
            bodyList.add(DialogBody.plainMessage(getLocalizedTitle(welcomeMessageKey)))
        }

        // 添加错误消息（如果有）- 放在 inputs 之后、按钮之前
        if (errorMessage != null) {
            bodyList.add(DialogBody.plainMessage(plugin.messageManager.getComponentFromMessage(errorMessage)))
        }

        val base = DialogBase.builder(getLocalizedTitle(titleKey))
            .canCloseWithEscape(false)
            .body(bodyList)
            .inputs(listOf(
                DialogInput.text(inputId, getLocalizedDescription(descriptionKey, *descriptionArgs)).build()
            ))
            .build()

        return Dialog.create { it.empty().base(base).type(DialogType.notice(confirmButton)) }
    }

    /**
     * 注册对话框构建方法（包含密码和确认密码两个输入框）
     */
    private fun buildRegisterDialog(
        titleKey: String,
        welcomeMessageKey: String? = null,
        descriptionKey: String,
        descriptionArgs: Array<Pair<String, Any>> = emptyArray(),
        buttonKey: String,
        action: DialogAction,
        errorMessage: String? = null
    ): Dialog {
        val confirmButton = ActionButton.builder(getLocalizedButton(buttonKey))
            .action(action)
            .build()

        val bodyList = mutableListOf<DialogBody>()

        // 添加欢迎消息（如果有）- 放在最上方
        if (welcomeMessageKey != null) {
            bodyList.add(DialogBody.plainMessage(getLocalizedTitle(welcomeMessageKey)))
        }

        // 添加错误消息（如果有）- 放在 inputs 之后、按钮之前
        if (errorMessage != null) {
            bodyList.add(DialogBody.plainMessage(plugin.messageManager.getComponentFromMessage(errorMessage)))
        }

        val base = DialogBase.builder(getLocalizedTitle(titleKey))
            .canCloseWithEscape(false)
            .body(bodyList)
            .inputs(listOf(
                DialogInput.text("reg_password", getLocalizedDescription("register.password-input", *descriptionArgs)).build(),
                DialogInput.text("reg_confirm_password", getLocalizedDescription("register.confirm-password-input")).build()
            ))
            .build()

        return Dialog.create { it.empty().base(base).type(DialogType.notice(confirmButton)) }
    }

    /**
     * 清理玩家的登录数据（供 AntiCheatManager 调用）
     */
    fun clearPlayerData(uuid: UUID) {
        loginAttempts.remove(uuid)
        loggedInPlayers.remove(uuid)
    }
}