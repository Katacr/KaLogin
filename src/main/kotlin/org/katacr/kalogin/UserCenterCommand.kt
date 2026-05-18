@file:Suppress("UnstableApiUsage")

package org.katacr.kalogin

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.Duration

class UserCenterCommand(private val plugin: KaLogin) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (sender !is Player) {
            plugin.messageManager.sendMessage(sender, "authme.player-only")
            return true
        }

        // 检查玩家是否已登录
        if (plugin.authMeManager.useAuthMe) {
            if (!plugin.authMeManager.isAuthenticated(sender)) {
                plugin.messageManager.sendMessage(sender, "anti-cheat.command-blocked")
                return true
            }
        } else {
            if (!plugin.loginListener.isLoggedIn(sender.uniqueId)) {
                plugin.messageManager.sendMessage(sender, "anti-cheat.command-blocked")
                return true
            }
        }

        showUserCenterDialog(sender)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): List<String> = emptyList()

    private fun showUserCenterDialog(player: Player) {
        plugin.dbManager.getPlayerInfoSnapshot(player.uniqueId).thenAccept { snapshot ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable

                val bodyList = mutableListOf<DialogBody>()

                // 用户名
                bodyList.add(DialogBody.plainMessage(
                    plugin.messageManager.getComponent("user-center.info-username", "username" to player.name)
                ))

                // 注册时间
                val regDate = snapshot?.regDate ?: plugin.messageManager.getMessage("user-center.unknown")
                bodyList.add(DialogBody.plainMessage(
                    plugin.messageManager.getComponent("user-center.info-reg-date", "date" to regDate)
                ))

                // 上次登录IP（脱敏）
                val lastIp = snapshot?.lastLoginIp?.let { maskIp(it) }
                    ?: plugin.messageManager.getMessage("user-center.unknown")
                bodyList.add(DialogBody.plainMessage(
                    plugin.messageManager.getComponent("user-center.info-last-ip", "ip" to lastIp)
                ))

                // 绑定邮箱（脱敏）
                val email = snapshot?.email?.let { maskEmail(it) }
                    ?: plugin.messageManager.getMessage("user-center.not-bound")
                bodyList.add(DialogBody.plainMessage(
                    plugin.messageManager.getComponent("user-center.info-email", "email" to email)
                ))

                // 密码（星号显示）
                bodyList.add(DialogBody.plainMessage(
                    plugin.messageManager.getComponent("user-center.info-password")
                ))

                // 绑定/换绑邮箱按钮
                val emailButtonLabel = if (snapshot?.email != null) {
                    plugin.messageManager.getComponent("user-center.button-rebind-email")
                } else {
                    plugin.messageManager.getComponent("user-center.button-bind-email")
                }
                val bindEmailAction = DialogAction.customClick(
                    { _, _ ->
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            if (player.isOnline) {
                                player.performCommand("bindemail")
                            }
                        })
                    },
                    ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
                )
                val emailButton = ActionButton.builder(emailButtonLabel)
                    .action(bindEmailAction)
                    .build()

                // 修改密码按钮
                val changePasswordAction = DialogAction.customClick(
                    { _, _ ->
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            if (player.isOnline) {
                                player.performCommand("kalogin:cp")
                            }
                        })
                    },
                    ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
                )
                val changePasswordButton = ActionButton.builder(
                    plugin.messageManager.getComponent("user-center.button-change-password")
                ).action(changePasswordAction).build()

                // 关闭按钮
                val closeAction = DialogAction.customClick(
                    { _, _ ->
                        player.closeDialog()
                    },
                    ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
                )
                val closeButton = ActionButton.builder(Component.text("关闭"))
                    .action(closeAction)
                    .build()

                val title = plugin.messageManager.getComponent("user-center.dialog-title")

                val dialog = Dialog.create { builder ->
                    builder.empty()
                        .base(
                            DialogBase.builder(title)
                                .pause(false)
                                .body(bodyList)
                                .canCloseWithEscape(true)
                                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                .build()
                        )
                        .type(
                            DialogType.multiAction(listOf(emailButton, changePasswordButton))
                                .columns(2)
                                .exitAction(closeButton)
                                .build()
                        )
                }
                player.showDialog(dialog)
            })
        }
    }

    /**
     * IP 脱敏：192.168.1.100 -> 192.168.*.*
     */
    private fun maskIp(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) {
            "${parts[0]}.${parts[1]}.*.*"
        } else {
            ip.replace(Regex("[0-9a-fA-F]+$"), "****")
        }
    }

    /**
     * 邮箱脱敏：example@gmail.com -> exa***@gmail.com
     */
    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 3) {
            return "*".repeat(atIndex) + email.substring(atIndex)
        }
        return email.take(3) + "***" + email.substring(atIndex)
    }
}
