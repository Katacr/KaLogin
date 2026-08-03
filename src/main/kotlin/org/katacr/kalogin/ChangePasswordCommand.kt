package org.katacr.kalogin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.katacr.kalogin.dialog.ChangePasswordResponse
import org.katacr.kalogin.listener.KaLoginAPI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class ChangePasswordCommand(private val plugin: KaLogin) : CommandExecutor, TabCompleter {

    private val passwordValidator = PasswordValidator(plugin)
    private val changePasswordAttempts = ConcurrentHashMap<String, Int>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            plugin.messageManager.sendMessage(sender, "authme.player-only")
            return true
        }

        if (!isLoggedIn(sender)) {
            plugin.messageManager.sendMessage(sender, "anti-cheat.command-blocked")
            return true
        }

        showChangePasswordDialog(sender)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> =
        emptyList()

    private fun isLoggedIn(player: Player): Boolean =
        if (plugin.authMeManager.useAuthMe) {
            plugin.authMeManager.isAuthenticated(player)
        } else {
            plugin.loginListener.isLoggedIn(player.uniqueId)
        }

    /**
     * 显示修改密码对话框。
     */
    private fun showChangePasswordDialog(player: Player, errorMessage: String? = null) {
        val changePasswordAction: (ChangePasswordResponse) -> Unit = action@{ response ->
            val oldPassword = response.oldPassword
            val newPassword = response.newPassword
            val confirmNewPassword = response.confirmNewPassword

            if (oldPassword.isNullOrBlank()) {
                showChangePasswordDialog(player, plugin.messageManager.getMessage("change-password.old-password-empty"))
                return@action
            }

            if (newPassword.isNullOrBlank()) {
                showChangePasswordDialog(player, plugin.messageManager.getMessage("change-password.new-password-empty"))
                return@action
            }

            val verifyPasswordTask = if (plugin.authMeManager.useAuthMe) {
                CompletableFuture.completedFuture(plugin.authMeManager.checkPassword(player.name, oldPassword))
            } else {
                plugin.dbManager.verifyPassword(player.uniqueId, oldPassword)
            }

            verifyPasswordTask.thenAccept { isOldPasswordValid ->
                if (!isOldPasswordValid) {
                    handleInvalidOldPassword(player)
                    return@thenAccept
                }

                val validationError = passwordValidator.validate(newPassword)
                if (validationError != null) {
                    reopenWithFailure(player, "Invalid password format: $validationError") {
                        plugin.messageManager.getMessage("change-password.new-password-invalid", "error" to validationError)
                    }
                    return@thenAccept
                }

                if (oldPassword == newPassword) {
                    reopenWithFailure(player, "New password same as old password") {
                        plugin.messageManager.getMessage("change-password.same-password")
                    }
                    return@thenAccept
                }

                if (newPassword != confirmNewPassword) {
                    reopenWithFailure(player, "Passwords do not match") {
                        plugin.messageManager.getMessage("change-password.password-mismatch")
                    }
                    return@thenAccept
                }

                changePasswordAttempts.remove(player.name)
                plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("change-password.saving"))

                if (plugin.authMeManager.useAuthMe) {
                    plugin.authMeManager.changePassword(player.name, newPassword)
                    KaLoginScheduler.runPlayer(player, Runnable {
                        plugin.dialogPlatform.close(player)
                        plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("change-password.success"))
                        KaLoginAPI.getInstance()?.callPlayerChangePasswordSuccess(player)
                    })
                } else {
                    plugin.dbManager.setPassword(player.uniqueId, newPassword).thenAccept { success ->
                        KaLoginScheduler.runPlayer(player, Runnable {
                            if (success) {
                                plugin.dialogPlatform.close(player)
                                plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("change-password.success"))
                                KaLoginAPI.getInstance()?.callPlayerChangePasswordSuccess(player)
                            } else {
                                plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("change-password.failed"))
                                KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "Database error")
                            }
                        })
                    }
                }
            }
        }

        plugin.dialogPlatform.showChangePassword(
            player,
            plugin.messageManager.getComponent("change-password.dialog-title"),
            null,
            plugin.resolveDialogErrorComponent(player, errorMessage),
            changePasswordAction,
            onCancel = { plugin.dialogPlatform.close(player) }
        )
    }

    private fun handleInvalidOldPassword(player: Player) {
        val currentAttempts = (changePasswordAttempts[player.name] ?: 0) + 1
        changePasswordAttempts[player.name] = currentAttempts
        val maxAttempts = plugin.config.getInt("change-password.max-attempts", 3)

        if (currentAttempts >= maxAttempts) {
            plugin.messageManager.sendComponent(player, plugin.messageManager.getComponent("change-password.too-many-attempts"))
            changePasswordAttempts.remove(player.name)
            KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, "Too many attempts")
            return
        }

        reopenWithFailure(player, "Old password incorrect") {
            plugin.messageManager.getMessage(
                "change-password.old-password-wrong",
                "attempts" to (maxAttempts - currentAttempts)
            )
        }
    }

    private fun reopenWithFailure(player: Player, reason: String, message: () -> String) {
        KaLoginScheduler.runPlayer(player, Runnable {
            KaLoginAPI.getInstance()?.callPlayerChangePasswordFailed(player, reason)
            showChangePasswordDialog(player, message())
        })
    }
}
