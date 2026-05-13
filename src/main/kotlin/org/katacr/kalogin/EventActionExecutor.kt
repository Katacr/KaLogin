@file:Suppress("UnstableApiUsage")

package org.katacr.kalogin

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player

/**
 * 执行登录/注册成功后的配置动作。
 *
 * 支持格式：
 * - wait: <等待 tick>
 * - command: <玩家执行的指令>
 * - console: <控制台执行的指令>
 * - toast: <Toast 通知参数>
 */
class EventActionExecutor(private val plugin: KaLogin) {

    fun sendToast(player: Player, args: String) {
        if (!player.isOnline) {
            return
        }
        parseAndSendToast(player, args)
    }

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

            "toast" -> {
                if (!player.isOnline) {
                    plugin.logger.warning("Skipped toast action because player ${player.name} is offline: $rawAction")
                } else {
                    sendToast(player, actionValue)
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

    private fun parseAndSendToast(player: Player, args: String) {
        var frameType = "task"
        var iconItem = "minecraft:stone"
        var title = "提示"
        var description = ""

        args.split(";").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                val value = resolvePlaceholders(player, parts[1].trim())
                when (key) {
                    "type" -> frameType = value.lowercase()
                    "icon" -> iconItem = if (value.contains(":")) value else "minecraft:$value"
                    "msg", "title" -> title = value
                    "description", "desc" -> description = value
                }
            }
        }

        val randomKey = NamespacedKey(plugin, "toast_${player.uniqueId}_${System.currentTimeMillis()}")
        val titleJson = GsonComponentSerializer.gson().serialize(plugin.messageManager.getComponentFromMessage(title))
        val descJson = GsonComponentSerializer.gson().serialize(plugin.messageManager.getComponentFromMessage(description))

        val advancementJson = """
            {
              "display": {
                "icon": {
                  "id": "${iconItem.lowercase()}"
                },
                "title": $titleJson,
                "description": $descJson,
                "frame": "${if (frameType in listOf("task", "goal", "challenge")) frameType else "task"}",
                "show_toast": true,
                "announce_to_chat": false,
                "hidden": true
              },
              "criteria": {
                "impossible": {
                  "trigger": "minecraft:impossible"
                }
              }
            }
        """.trimIndent()

        try {
            val advancement = Bukkit.getUnsafe().loadAdvancement(randomKey, advancementJson)
            val progress = player.getAdvancementProgress(advancement)
            progress.awardCriteria("impossible")

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (player.isOnline) {
                    progress.revokeCriteria("impossible")
                }
                Bukkit.getUnsafe().removeAdvancement(randomKey)
            }, 10L)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send toast action to ${player.name}: ${e.message}")
        }
    }
}