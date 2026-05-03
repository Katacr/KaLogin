package org.katacr.kalogin

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class KaLoginCommand(private val plugin: KaLogin) : CommandExecutor, TabCompleter {

    private val passwordValidator = PasswordValidator(plugin)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (!sender.hasPermission("kalogin.admin")) {
            plugin.messageManager.sendMessage(sender, "command.no-permission")
            return true
        }

        if (args.isEmpty()) {
            plugin.messageManager.sendMessage(sender, "command.unknown-subcommand")
            return true
        }

        when (args[0].lowercase()) {
            "delete" -> handleDelete(sender, args)
            "register" -> handleRegister(sender, args)
            "reload" -> handleReload(sender)
            else -> {
                plugin.messageManager.sendMessage(sender, "command.unknown-subcommand")
            }
        }
        return true
    }

    /**
     * 删除玩家数据
     */
    private fun handleDelete(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            plugin.messageManager.sendMessage(sender, "command.delete.usage")
            return
        }

        val playerName = args[1]
        val player = Bukkit.getOfflinePlayer(playerName)

        if (!player.hasPlayedBefore() && player.uniqueId.version() == 4) {
            plugin.messageManager.sendMessage(sender, "command.delete.player-not-found", "player" to playerName)
            return
        }

        plugin.dbManager.deletePlayer(player.uniqueId).thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    plugin.messageManager.sendMessage(sender, "command.delete.success", "player" to playerName)
                    // 如果玩家在线，踢出玩家
                    val onlinePlayer = Bukkit.getPlayerExact(playerName)
                    if (onlinePlayer != null && onlinePlayer.isOnline) {
                        onlinePlayer.kick(plugin.messageManager.getComponent("command.delete.kicked"))
                    }
                } else {
                    plugin.messageManager.sendMessage(sender, "command.delete.failed")
                }
            })
        }
    }

    /**
     * 重新设置玩家密码
     */
    private fun handleRegister(sender: CommandSender, args: Array<String>) {
        if (args.size < 3) {
            plugin.messageManager.sendMessage(sender, "command.register.usage")
            return
        }

        val playerName = args[1]
        val password = args[2]
        val player = Bukkit.getOfflinePlayer(playerName)

        // 验证密码格式
        val validationError = passwordValidator.validate(password)
        if (validationError != null) {
            plugin.messageManager.sendMessage(sender, "command.register.validation-failed", "error" to validationError)
            return
        }

        plugin.dbManager.setPassword(player.uniqueId, password).thenAccept { success ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    plugin.messageManager.sendMessage(sender, "command.register.success", "player" to playerName)
                } else {
                    plugin.messageManager.sendMessage(sender, "command.register.failed")
                }
            })
        }
    }

    /**
     * 重载配置文件
     */
    private fun handleReload(sender: CommandSender) {
        plugin.reloadConfig()
        plugin.messageManager.reload()
        plugin.messageManager.sendMessage(sender, "command.reload.success")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): List<String>? {
        if (!sender.hasPermission("kalogin.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("delete", "register", "reload").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> {
                when (args[0].lowercase()) {
                    "delete", "register" -> Bukkit.getOnlinePlayers().map { it.name }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
