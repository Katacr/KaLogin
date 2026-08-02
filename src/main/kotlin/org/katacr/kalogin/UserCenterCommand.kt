package org.katacr.kalogin

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class UserCenterCommand(private val plugin: KaLogin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            plugin.messageManager.sendMessage(sender, "authme.player-only")
            return true
        }

        if (!isLoggedIn(sender)) {
            plugin.messageManager.sendMessage(sender, "anti-cheat.command-blocked")
            return true
        }

        showUserCenterDialog(sender)
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

    private fun showUserCenterDialog(player: Player) {
        plugin.dbManager.getPlayerInfoSnapshot(player.uniqueId).thenAccept { snapshot ->
            KaLoginScheduler.runPlayer(player, Runnable {
                if (!player.isOnline) return@Runnable

                val regDate = snapshot?.regDate ?: plugin.messageManager.getMessage("user-center.unknown")
                val lastIp = snapshot?.lastLoginIp?.let { maskIp(it) }
                    ?: plugin.messageManager.getMessage("user-center.unknown")
                val email = snapshot?.email?.let { maskEmail(it) }
                    ?: plugin.messageManager.getMessage("user-center.not-bound")
                val emailButtonLabel = if (snapshot?.email != null) {
                    plugin.messageManager.getComponent("user-center.button-rebind-email")
                } else {
                    plugin.messageManager.getComponent("user-center.button-bind-email")
                }

                plugin.dialogPlatform.showUserCenter(
                    player,
                    plugin.messageManager.getComponent("user-center.dialog-title"),
                    listOf(
                        plugin.messageManager.getComponent("user-center.info-username", "username" to player.name),
                        plugin.messageManager.getComponent("user-center.info-reg-date", "date" to regDate),
                        plugin.messageManager.getComponent("user-center.info-last-ip", "ip" to lastIp),
                        plugin.messageManager.getComponent("user-center.info-email", "email" to email),
                        plugin.messageManager.getComponent("user-center.info-password")
                    ),
                    emailButtonLabel,
                    plugin.messageManager.getComponent("user-center.button-change-password"),
                    Component.text("关闭"),
                    onEmail = { if (player.isOnline) player.performCommand("bindemail") },
                    onChangePassword = { if (player.isOnline) player.performCommand("kalogin:cp") },
                    onClose = { plugin.dialogPlatform.close(player) }
                )
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
