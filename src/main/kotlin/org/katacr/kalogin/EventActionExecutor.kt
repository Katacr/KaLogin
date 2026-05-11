package org.katacr.kalogin

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player

/**
 * 执行登录/注册成功后的配置动作。
 *
 * 支持格式：
 * - wait: <等待 tick>
 * - command: <玩家执行的指令>
 * - console: <控制台执行的指令>
 */
class EventActionExecutor(private val plugin: KaLogin) {

    fun execute(player: Player, eventType: String) {
        val actions = plugin.config.getStringList("events.$eventType")
        if (actions.isEmpty()) {
            return
        }

        executeActionsSequentially(player, eventType, actions, 0)
    }

    private fun executeActionsSequentially(player: Player, eventType: String, actions: List<String>, index: Int) {
        if (index >= actions.size) {
            return
        }

        val rawAction = actions[index]
        val separatorIndex = rawAction.indexOf(':')
        if (separatorIndex <= 0) {
            plugin.logger.warning("Invalid event action format in events.$eventType: $rawAction")
            executeActionsSequentially(player, eventType, actions, index + 1)
            return
        }

        val actionType = rawAction.take(separatorIndex).trim().lowercase()
        val actionValue = rawAction.substring(separatorIndex + 1).trim()

        when (actionType) {
            "wait" -> {
                val delay = actionValue.toLongOrNull()
                if (delay == null || delay < 0) {
                    plugin.logger.warning("Invalid wait value in events.$eventType: $rawAction")
                    executeActionsSequentially(player, eventType, actions, index + 1)
                    return
                }

                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    executeActionsSequentially(player, eventType, actions, index + 1)
                }, delay)
            }

            "command", "console" -> {
                val command = resolvePlaceholders(player, actionValue).removePrefix("/")
                if (command.isBlank()) {
                    plugin.logger.warning("Empty event action command in events.$eventType: $rawAction")
                    executeActionsSequentially(player, eventType, actions, index + 1)
                    return
                }

                when (actionType) {
                    "command" -> {
                        if (!player.isOnline) {
                            plugin.logger.warning("Skipped player command because player ${player.name} is offline: $rawAction")
                        } else {
                            player.performCommand(command)
                        }
                    }
                    "console" -> plugin.server.dispatchCommand(plugin.server.consoleSender, command)
                }

                executeActionsSequentially(player, eventType, actions, index + 1)
            }

            else -> {
                plugin.logger.warning("Unsupported event action type '$actionType' in events.$eventType: $rawAction")
                executeActionsSequentially(player, eventType, actions, index + 1)
            }
        }
    }

    private fun resolvePlaceholders(player: Player, text: String): String {
        var result = text
            .replace("{player_name}", player.name)
            .replace("%player_name%", player.name)

        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            result = PlaceholderAPI.setPlaceholders(player, result)
        }

        return result
    }
}