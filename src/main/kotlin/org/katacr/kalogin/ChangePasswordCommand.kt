package org.katacr.kalogin

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kalogin.listener.KaLoginAPI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class ChangePasswordCommand(private val plugin: KaLogin) : CommandExecutor {

    private val passwordValidator = PasswordValidator(plugin)
    private val changePasswordAttempts = ConcurrentHashMap<String, Int>()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        // 只有玩家可以使用此命令
        if (sender !is Player) {
            plugin.messageManager.sendMessage(sender, "authme.player-only")
            return true
        }

        val player = sender

        // 检查玩家是否已登录
        if (plugin.authMeManager.useAuthMe) {
            // AuthMe 模式：使用 AuthMe 的认证状态
            if (!plugin.authMeManager.isAuthenticated(player)) {
                plugin.messageManager.sendMessage(player, "anti-cheat.command-blocked")
                return true
            }
        } else {
            // KaLogin 模式：使用 KaLogin 的认证状态
            if (!plugin.loginListener.isLoggedIn(player.uniqueId)) {
                plugin.messageManager.sendMessage(player, "anti-cheat.command-blocked")
                return true
            }
        }

        // 显示修改密码对话框
        showChangePasswordDialog(player)
        return true
    }

    /**
     * 显示修改密码对话框
     */
    private fun showChangePasswordDialog(player: Player, errorMessage: String? = null) {
        // 确认按钮的动作
        val changePasswordAction = DialogAction.customClick(
            DialogActionCallback { response, _ ->
                val oldPassword = response.getText("old_password")
                val newPassword = response.getText("new_password")
                val confirmNewPassword = response.getText("confirm_new_password")

                if (oldPassword.isNullOrBlank()) {
                    showChangePasswordDialog(player, plugin.messageManager.getMessage("change-password.old-password-empty"))
                    return@DialogActionCallback
                }

                if (newPassword.isNullOrBlank()) {
                    showChangePasswordDialog(player, plugin.messageManager.getMessage("change-password.new-password-empty"))
                    return@DialogActionCallback
                }

                // 验证旧密码是否正确
                val verifyPasswordTask = if (plugin.authMeManager.useAuthMe) {
                    // AuthMe 模式：使用 AuthMe API
                    val isValid = plugin.authMeManager.checkPassword(player.name, oldPassword)
                    CompletableFuture.completedFuture(isValid)
                } else {
                    // KaLogin 模式：使用数据库
                    plugin.dbManager.verifyPassword(player.uniqueId, oldPassword)
                }

                verifyPasswordTask.thenAccept { isOldPasswordValid: Boolean ->
                    if (!isOldPasswordValid) {
                        val currentAttempts = (changePasswordAttempts[player.name] ?: 0) + 1
                        changePasswordAttempts[player.name] = currentAttempts

                        val maxAttempts = plugin.config.getInt("change-password.max-attempts", 3)
                        if (currentAttempts >= maxAttempts) {
                            player.sendMessage(plugin.messageManager.getComponent("change-password.too-many-attempts"))
                            changePasswordAttempts.remove(player.name)
                            // 触发修改密码失败事件（尝试次数过多）
                            KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "Too many attempts")
                            return@thenAccept
                        }

                        plugin.server.scheduler.runTask(plugin, Runnable {
                            // 触发修改密码失败事件（旧密码错误）
                            KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "Old password incorrect")
                            showChangePasswordDialog(
                                player,
                                plugin.messageManager.getMessage(
                                    "change-password.old-password-wrong",
                                    "attempts" to (maxAttempts - currentAttempts)
                                )
                            )
                        })
                        return@thenAccept
                    }

                    // 验证新密码格式
                    val validationError = passwordValidator.validate(newPassword)
                    if (validationError != null) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            // 触发修改密码失败事件（新密码不符合要求）
                            KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "Invalid password format: $validationError")
                            showChangePasswordDialog(
                                player,
                                plugin.messageManager.getMessage(
                                    "change-password.new-password-invalid",
                                    "error" to validationError
                                )
                            )
                        })
                        return@thenAccept
                    }

                    // 验证新旧密码不能相同
                    if (oldPassword == newPassword) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            // 触发修改密码失败事件（新旧密码相同）
                            KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "New password same as old password")
                            showChangePasswordDialog(
                                player,
                                plugin.messageManager.getMessage("change-password.same-password")
                            )
                        })
                        return@thenAccept
                    }

                    // 验证两次新密码是否一致
                    if (newPassword != confirmNewPassword) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            // 触发修改密码失败事件（两次密码不一致）
                            KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "Passwords do not match")
                            showChangePasswordDialog(
                                player,
                                plugin.messageManager.getMessage("change-password.password-mismatch")
                            )
                        })
                        return@thenAccept
                    }

                    // 重置尝试次数
                    changePasswordAttempts.remove(player.name)

                    // 更新密码
                    player.sendMessage(plugin.messageManager.getComponent("change-password.saving"))

                    if (plugin.authMeManager.useAuthMe) {
                        // AuthMe 模式：使用 AuthMe API
                        plugin.authMeManager.changePassword(player.name, newPassword)
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            player.sendMessage(plugin.messageManager.getComponent("change-password.success"))
                            // 触发修改密码成功事件
                            KaLoginAPI.getInstance()?.callPlayerChangePasswordSuccess(player)
                        })
                    } else {
                        // KaLogin 模式：使用数据库
                        plugin.dbManager.setPassword(player.uniqueId, newPassword).thenAccept { success: Boolean ->
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                if (success) {
                                    player.sendMessage(plugin.messageManager.getComponent("change-password.success"))
                                    // 触发修改密码成功事件
                                    KaLoginAPI.getInstance()?.callPlayerChangePasswordSuccess(player)
                                } else {
                                    player.sendMessage(plugin.messageManager.getComponent("change-password.failed"))
                                    // 触发修改密码失败事件
                                    KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "Database error")
                                }
                            })
                        }
                    }
                }
            },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
        )

        // 取消按钮的动作
        val cancelAction = DialogAction.customClick(
            DialogActionCallback { _, _ ->
                // 取消操作，不执行任何操作
            },
            ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
        )

        val errorComponent = errorMessage?.let { plugin.messageManager.getComponentFromMessage(it) }
        val confirmButton = ActionButton.builder(plugin.messageManager.getComponent("change-password.dialog-button"))
            .action(changePasswordAction)
            .build()
        val cancelButton = ActionButton.builder(plugin.messageManager.getComponent("change-password.cancel-button"))
            .action(cancelAction)
            .build()

        val dialog = LoginUI.buildChangePasswordDialog(
            player,
            plugin.messageManager.getComponent("change-password.dialog-title"),
            null,
            errorComponent,
            confirmButton,
            cancelButton
        )
        player.showDialog(dialog)
    }
}
