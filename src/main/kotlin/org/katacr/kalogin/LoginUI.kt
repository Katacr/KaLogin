package org.katacr.kalogin

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

/**
 * 平台无关的登录 UI 配置读取与文本解析工具。
 *
 * Paper/Folia 与 Spigot 的原生 Dialog 构建由 dialog 包中的平台适配器完成。
 */
object LoginUI {
    private lateinit var plugin: KaLogin
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun init(plugin: KaLogin) {
        this.plugin = plugin
    }

    private val legacyToMiniMessageMap = mapOf(
        "&0" to "<black>", "§0" to "<black>",
        "&1" to "<dark_blue>", "§1" to "<dark_blue>",
        "&2" to "<dark_green>", "§2" to "<dark_green>",
        "&3" to "<dark_aqua>", "§3" to "<dark_aqua>",
        "&4" to "<dark_red>", "§4" to "<dark_red>",
        "&5" to "<dark_purple>", "§5" to "<dark_purple>",
        "&6" to "<gold>", "§6" to "<gold>",
        "&7" to "<gray>", "§7" to "<gray>",
        "&8" to "<dark_gray>", "§8" to "<dark_gray>",
        "&9" to "<blue>", "§9" to "<blue>",
        "&a" to "<green>", "§a" to "<green>",
        "&b" to "<aqua>", "§b" to "<aqua>",
        "&c" to "<red>", "§c" to "<red>",
        "&d" to "<light_purple>", "§d" to "<light_purple>",
        "&e" to "<yellow>", "§e" to "<yellow>",
        "&f" to "<white>", "§f" to "<white>",
        "&k" to "<obfuscated>", "§k" to "<obfuscated>",
        "&l" to "<bold>", "§l" to "<bold>",
        "&m" to "<strikethrough>", "§m" to "<strikethrough>",
        "&n" to "<underline>", "§n" to "<underline>",
        "&o" to "<italic>", "§o" to "<italic>",
        "&r" to "<reset>", "§r" to "<reset>",
        "&A" to "<green>", "§A" to "<green>",
        "&B" to "<aqua>", "§B" to "<aqua>",
        "&C" to "<red>", "§C" to "<red>",
        "&D" to "<light_purple>", "§D" to "<light_purple>",
        "&E" to "<yellow>", "§E" to "<yellow>",
        "&F" to "<white>", "§F" to "<white>",
        "&K" to "<obfuscated>", "§K" to "<obfuscated>",
        "&L" to "<bold>", "§L" to "<bold>",
        "&M" to "<strikethrough>", "§M" to "<strikethrough>",
        "&N" to "<underline>", "§N" to "<underline>",
        "&O" to "<italic>", "§O" to "<italic>",
        "&R" to "<reset>", "§R" to "<reset>"
    )

    private fun convertLegacyToMiniMessage(text: String): String {
        var result = text
        legacyToMiniMessageMap.forEach { (legacy, mini) ->
            result = result.replace(legacy, mini)
        }
        return result
    }

    fun parseText(text: String): Component {
        if (text.isEmpty()) return Component.empty()
        val hasMiniMessageTags = text.contains(Regex("<[a-z_]+(?:[:][^>]*)?>", RegexOption.IGNORE_CASE))

        return if (hasMiniMessageTags) {
            val hasLegacyCodes = text.contains(Regex("[&§][0-9a-fA-FlmnoOrkLKMNO]"))
            miniMessage.deserialize(if (hasLegacyCodes) convertLegacyToMiniMessage(text) else text)
        } else {
            legacySerializer.deserialize(text.replace("&", "§"))
        }
    }

    fun parseText(text: String, player: Player): Component =
        parseText(resolveVariables(text, player))

    fun resolveVariables(text: String, player: Player): String {
        var result = text.replace(Regex("\\{player_name}|%player_name%"), player.name)
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            result = PlaceholderAPI.setPlaceholders(player, result)
        }
        return result
    }

    fun parseClickableText(text: String, player: Player): Component {
        val resolvedText = resolveVariables(text, player)
        val regex = Regex("<text=['\"]?([^'\";\n]+)['\"]?(?:;hover=['\"]?([^'\";\n]+)['\"]?)?(?:;command=['\"]?([^'\";\n]+)['\"]?)?(?:;url=['\"]?([^'\";\n]+)['\"]?)?>")
        var lastIndex = 0
        val builder = Component.text()

        while (true) {
            val match = regex.find(resolvedText, lastIndex) ?: break
            val prefixText = resolvedText.substring(lastIndex, match.range.first)
            if (prefixText.isNotEmpty()) {
                builder.append(parseText(prefixText))
            }

            val displayComponent = parseText(match.groupValues[1])
            val clickableComponent = when {
                match.groupValues[3].isNotEmpty() -> displayComponent.clickEvent(ClickEvent.runCommand(match.groupValues[3]))
                match.groupValues[4].isNotEmpty() -> displayComponent.clickEvent(ClickEvent.openUrl(match.groupValues[4]))
                else -> displayComponent
            }

            val hoverText = match.groupValues[2]
            builder.append(
                if (hoverText.isNotEmpty()) clickableComponent.hoverEvent(HoverEvent.showText(parseText(hoverText)))
                else clickableComponent
            )
            lastIndex = match.range.last + 1
        }

        if (lastIndex < resolvedText.length) {
            builder.append(parseText(resolvedText.substring(lastIndex)))
        }

        return if (lastIndex == 0) parseText(resolvedText) else builder.build()
    }

    fun bodyElements(player: Player, fileName: String): List<DialogBodyElement> {
        val uiFile = File(plugin.dataFolder, "ui/$fileName.yml")
        if (!uiFile.exists()) return emptyList()

        val config = YamlConfiguration.loadConfiguration(uiFile).getConfigurationSection("Body") ?: return emptyList()
        val bodyList = mutableListOf<DialogBodyElement>()

        config.getKeys(false).forEach { key ->
            val section = config.getConfigurationSection(key) ?: return@forEach
            when (section.getString("type", "message")?.trim()?.lowercase()) {
                "none" -> Unit
                "message" -> {
                    val text = readMessageText(section, "text", fileName, key)
                    if (!text.isNullOrEmpty()) {
                        bodyList.add(DialogBodyElement.Message(parseClickableText(text, player), section.getInt("width", -1)))
                    }
                }
                "item" -> {
                    val material = section.getString("material", "apple") ?: "apple"
                    val bukkitMaterial = runCatching { Material.valueOf(material.uppercase()) }.getOrNull()
                    if (bukkitMaterial == null) {
                        plugin.logger.warning("Invalid material: $material")
                        return@forEach
                    }

                    val descriptionList = when {
                        section.isList("description") -> section.getStringList("description")
                        section.getString("description")?.isNotEmpty() == true -> listOf(section.getString("description")!!)
                        else -> emptyList()
                    }

                    bodyList.add(
                        DialogBodyElement.Item(
                            material = bukkitMaterial,
                            amount = section.getInt("amount", 1).coerceAtLeast(1),
                            name = section.getString("name", ""),
                            lore = section.getStringList("lore"),
                            description = descriptionList,
                            descriptionWidth = section.getInt("description_width", -1),
                            itemModel = section.getString("item_model", ""),
                            customModelData = if (section.contains("custom_model_data")) section.getInt("custom_model_data") else null,
                            showOverlays = section.getBoolean("show_overlays", false),
                            showTooltip = section.getBoolean("show_tooltip", true),
                            width = section.getInt("width", 16),
                            height = section.getInt("height", 16)
                        )
                    )
                }
            }
        }

        return bodyList
    }

    /** 读取 message.text，支持字符串、多行字符串和字符串列表，列表按换行合并。 */
    private fun readMessageText(
        section: ConfigurationSection,
        path: String,
        fileName: String,
        componentId: String
    ): String? {
        val value = section.get(path) ?: return null
        if (value is String) return value
        if (value is List<*>) {
            val lines = mutableListOf<String>()
            value.forEachIndexed { index, item ->
                if (item is String) {
                    lines.add(item)
                } else {
                    plugin.logger.warning(
                        "Invalid message text entry in ui/$fileName.yml Body.$componentId.$path[$index]: expected string"
                    )
                }
            }
            return lines.joinToString("\n")
        }
        plugin.logger.warning("Invalid message text in ui/$fileName.yml Body.$componentId.$path: expected string or string list")
        return null
    }

    fun textInput(key: String, label: Component, sectionPath: String, maxLength: Int): TextInputSpec {
        val section = plugin.config.getConfigurationSection(sectionPath)
        return TextInputSpec(
            key = key,
            label = label,
            width = section?.getInt("width", 200) ?: 200,
            height = section?.getInt("height", 20) ?: 20,
            labelVisible = section?.getBoolean("labelVisible", true) ?: true,
            initial = section?.getString("initial", "") ?: "",
            maxLength = maxLength
        )
    }

    fun boolInitial(sectionPath: String): Boolean =
        plugin.config.getConfigurationSection(sectionPath)?.getBoolean("initial", false) ?: false
}

sealed class DialogBodyElement {
    data class Message(val text: Component, val width: Int) : DialogBodyElement()
    data class Item(
        val material: Material,
        val amount: Int,
        val name: String?,
        val lore: List<String>,
        val description: List<String>,
        val descriptionWidth: Int,
        val itemModel: String?,
        val customModelData: Int?,
        val showOverlays: Boolean,
        val showTooltip: Boolean,
        val width: Int,
        val height: Int
    ) : DialogBodyElement()
}

data class TextInputSpec(
    val key: String,
    val label: Component,
    val width: Int,
    val height: Int,
    val labelVisible: Boolean,
    val initial: String,
    val maxLength: Int
)
