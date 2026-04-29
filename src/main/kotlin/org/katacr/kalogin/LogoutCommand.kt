package org.katacr.kalogin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.katacr.kalogin.listener.KaLoginAPI

class LogoutCommand(private val plugin: KaLogin) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        // 检查是否为玩家
        if (sender !is Player) {
            sender.sendMessage("此命令只能由玩家执行！")
            return true
        }

        // 检查玩家是否已登录
        if (plugin.authMeManager.useAuthMe) {
            if (!plugin.authMeManager.isAuthenticated(sender)) {
                plugin.messageManager.sendMessage(sender, "logout.not-logged-in")
                return true
            }
        }

        // 1. 触发登出事件（如果使用 AuthMe，AuthMeLoginListener 会处理）
        KaLoginAPI.getInstance()?.callPlayerLogout(sender)

        // 2. 关闭同IP自动登录
        plugin.dbManager.updateAutoLoginByIp(sender.uniqueId, false)

        // 3. 如果使用 AuthMe，触发 AuthMe 登出
        if (plugin.authMeManager.useAuthMe) {
            plugin.authMeManager.forceLogout(sender)
        }

        // 4. 踢出玩家并显示消息
        val kickMessage = plugin.messageManager.getComponent("logout.kick-message")
        sender.kick(kickMessage)

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): List<String>? {
        // 无需Tab补全
        return emptyList()
    }
}
