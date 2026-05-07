@file:Suppress("UnstableApiUsage")

package org.katacr.kalogin

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File


object LoginUI {
    private lateinit var plugin: KaLogin
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun init(plugin: KaLogin) {
        this.plugin = plugin
    }

    /**
     * Legacy 颜色代码到 MiniMessage 标签的映射
     */
    private val legacyToMiniMessageMap = mapOf(
        // 颜色代码
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
        // 格式化代码
        "&k" to "<obfuscated>", "§k" to "<obfuscated>",
        "&l" to "<bold>", "§l" to "<bold>",
        "&m" to "<strikethrough>", "§m" to "<strikethrough>",
        "&n" to "<underline>", "§n" to "<underline>",
        "&o" to "<italic>", "§o" to "<italic>",
        "&r" to "<reset>", "§r" to "<reset>",
        // 大写版本
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

    /**
     * 将 Legacy 颜色代码转换为 MiniMessage 标签
     * @param text 包含 Legacy 颜色代码的文本
     * @return 转换后的文本
     */
    private fun convertLegacyToMiniMessage(text: String): String {
        var result = text
        legacyToMiniMessageMap.forEach { (legacy, mini) ->
            result = result.replace(legacy, mini)
        }
        return result
    }

    /**
     * 智能解析文本格式（自动检测 MiniMessage 或 Legacy）
     * 如果文本包含 MiniMessage 标签，则使用 MiniMessage 解析以支持所有高级特性（点击、悬停等）
     * 否则使用 Legacy 颜色代码解析
     * 注意: hovertext 格式 (<text=...>) 应该先在 parseClickableText 中处理
     * @param text 文本内容
     * @return Adventure Component
     */
    private fun parseText(text: String): Component {
        if (text.isEmpty()) return Component.empty()

        // 检测是否包含 MiniMessage 标签（<...>，排除 <text=...> 自定义格式）
        // MiniMessage 标签特征：尖括号包裹的字母、冒号、渐变等
        val hasMiniMessageTags = text.contains(Regex("<[a-z_]+(?:[:][^>]*)?>", RegexOption.IGNORE_CASE))

        return if (hasMiniMessageTags) {
            // 检测是否包含 Legacy 颜色代码
            val hasLegacyCodes = text.contains(Regex("[&§][0-9a-fA-FlmnoOrkLKMNO]"))
            
            val textToParse = if (hasLegacyCodes) {
                // 将 Legacy 颜色代码转换为 MiniMessage 标签
                convertLegacyToMiniMessage(text)
            } else {
                text
            }
            
            // 使用 MiniMessage 解析，保留所有高级特性（点击、悬停、渐变等）
            miniMessage.deserialize(textToParse)
        } else {
            // 使用 Legacy 颜色代码解析
            legacySerializer.deserialize(text.replace("&", "§"))
        }
    }

    /**
     * 解析文本，支持MiniMessage格式和Legacy颜色代码
     */
    private fun parseText(text: String, player: Player): Component {
        val processedText = resolveVariables(text, player)
        return parseText(processedText)
    }

    /**
     * 解析PAPI变量
     */
    private fun resolveVariables(text: String, player: Player): String {
        var result = text

        // 替换 {player_name} 或 %player_name%
        result = result.replace(Regex("\\{player_name}|%player_name%"), player.name)

        // 支持PAPI变量（如果PAPI已加载）
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            result = PlaceholderAPI.setPlaceholders(player, result)
        }

        return result
    }

    /**
     * 解析可点击文本（hovertext）
     * 格式: <text=显示文字;hover=悬停文字;command=指令;url=链接>
     * 也支持带单引号格式: <text='显示文字';hover='悬停文字';url='链接'>
     */
    fun parseClickableText(text: String, player: Player): Component {
        // 先解析变量
        val resolvedText = resolveVariables(text, player)

        // 查找所有的 <text=...> 标签（支持可选的单引号包裹值）
        val regex = Regex("<text=['\"]?([^'\";\n]+)['\"]?(?:;hover=['\"]?([^'\";\n]+)['\"]?)?(?:;command=['\"]?([^'\";\n]+)['\"]?)?(?:;url=['\"]?([^'\";\n]+)['\"]?)?>")
        var lastIndex = 0
        val builder = Component.text()

        while (true) {
            val match = regex.find(resolvedText, lastIndex) ?: break

            // 添加匹配前的文本
            val prefixText = resolvedText.substring(lastIndex, match.range.first)
            if (prefixText.isNotEmpty()) {
                builder.append(parseText(prefixText))
            }

            val displayText = match.groupValues[1]
            val hoverText = match.groupValues[2]
            val command = match.groupValues[3]
            val url = match.groupValues[4]

            // 构建可点击文本
            val displayComponent = parseText(displayText)

            val clickableComponent = when {
                command.isNotEmpty() -> {
                    displayComponent.clickEvent(ClickEvent.runCommand(command))
                }
                url.isNotEmpty() -> {
                    displayComponent.clickEvent(ClickEvent.openUrl(url))
                }
                else -> {
                    displayComponent
                }
            }

            val finalComponent = if (hoverText.isNotEmpty()) {
                clickableComponent.hoverEvent(
                    HoverEvent.showText(
                        parseText(hoverText)
                    )
                )
            } else {
                clickableComponent
            }

            builder.append(finalComponent)
            lastIndex = match.range.last + 1
        }

        // 添加剩余文本
        if (lastIndex < resolvedText.length) {
            builder.append(parseText(resolvedText.substring(lastIndex)))
        }

        return if (lastIndex == 0) {
            // 没有匹配到hovertext标签，直接返回普通文本
            parseText(resolvedText)
        } else {
            builder.build()
        }
    }

    /**
     * 从UI配置文件构建DialogBody列表
     * @param configPath 格式: "ui.xxx.Body" -> 从 plugins/KaLogin/ui/xxx.yml 读取 Body 节点
     */
    private fun buildBodyListFromConfig(player: Player, configPath: String): List<DialogBody> {
        val bodyList = mutableListOf<DialogBody>()
        
        // 解析路径: "ui.login.Body" -> fileName="login", sectionPath="Body"
        val pathParts = configPath.removePrefix("ui.").split(".", limit = 2)
        if (pathParts.isEmpty()) return bodyList
        
        val fileName = pathParts[0]
        val sectionPath = if (pathParts.size > 1) pathParts[1] else null
        
        // 从 ui 文件夹读取配置
        val uiFile = File(plugin.dataFolder, "ui/$fileName.yml")
        if (!uiFile.exists()) return bodyList
        
        val uiConfig = YamlConfiguration.loadConfiguration(uiFile)
        val config = if (sectionPath != null) {
            uiConfig.getConfigurationSection(sectionPath)
        } else {
            uiConfig
        }
        
        if (config == null) return bodyList

        config.getKeys(false).forEach { key ->
            val section = config.getConfigurationSection(key) ?: return@forEach
            val type = section.getString("type", "message")

            when (type) {
                "message" -> {
                    val text = section.getString("text", "")
                    text?.let {
                        if (it.isNotEmpty()) {
                            text.let { bodyList.add(DialogBody.plainMessage(parseClickableText(it, player))) }
                        }
                    }
                }
                "item" -> {
                    val material = section.getString("material", "apple")
                    val name = section.getString("name", "")
                    val lore = section.getStringList("lore")
                    // 支持 description 为字符串或列表
                    val descriptionList: List<String> = when {
                        section.isList("description") -> section.getStringList("description")
                        section.getString("description")?.isNotEmpty() == true -> listOf(section.getString("description")!!)
                        else -> emptyList()
                    }
                    val itemModel = section.getString("item_model", "")

                    try {
                        val bukkitMaterial = material?.let { Material.valueOf(it.uppercase()) }
                        val itemStack = bukkitMaterial?.let { ItemStack(it) }

                        // 设置 name、lore 和 item_model 到 ItemStack 上
                        itemStack?.editMeta { meta ->
                            // 设置物品名称
                            if (name?.isNotEmpty() == true) {
                                meta.displayName(parseText(name, player))
                            }
                            // 设置物品 Lore
                            if (lore.isNotEmpty()) {
                                meta.lore(lore.map { parseText(it, player) })
                            }
                            // 设置 item_model
                            if (itemModel?.isNotEmpty() == true) {
                                try {
                                    val namespacedKey = NamespacedKey.fromString(itemModel)
                                    if (namespacedKey != null) {
                                        meta.itemModel = namespacedKey
                                    }
                                } catch (e: IllegalArgumentException) {
                                    plugin.logger.warning("Invalid item_model: $itemModel")
                                }
                            }
                        }

                        // description 作为 DialogBody.item 的额外描述文本（可选）
                        val descriptionBody = if (descriptionList.isNotEmpty()) {
                            val descriptionText = descriptionList.joinToString("\n")
                            DialogBody.plainMessage(parseText(descriptionText, player))
                        } else null

                        val itemBody = itemStack?.let { DialogBody.item(it) }
                            ?.description(descriptionBody)
                            ?.showDecorations(false)
                            ?.showTooltip(true)
                            ?.width(16)
                            ?.height(16)
                            ?.build()

                        itemBody?.let { bodyList.add(it) }
                    } catch (e: IllegalArgumentException) {
                        plugin.logger.warning("Invalid material: $material")
                    }
                }
            }
        }

        return bodyList
    }

    /**
     * 构建登录对话框
     */
    fun buildLoginDialog(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        confirmButton: ActionButton
    ): Dialog {
        val bodyList = mutableListOf<DialogBody>()
        val inputList = mutableListOf<DialogInput>()

        // 添加UI配置文件中的body
        bodyList.addAll(buildBodyListFromConfig(player, "ui.login.Body"))

        // 添加描述文本
        description?.let { bodyList.add(DialogBody.plainMessage(it)) }

        // 添加错误消息
        error?.let { bodyList.add(DialogBody.plainMessage(it)) }

        // 添加密码输入框
        inputList.add(
            DialogInput.text("login_password", plugin.messageManager.getComponent("login.password-input"))
                .labelVisible(true)
                .maxLength(64)
                .build()
        )

        // 添加自动登录复选框（如果配置启用）
        if (plugin.config.getBoolean("login.show-auto-login-checkbox", true)) {
            inputList.add(
                DialogInput.bool("auto_login_by_ip", plugin.messageManager.getComponent("login.auto-login-checkbox"))
                    .initial(false)
                    .build()
            )
        }

        // 构建对话框
        return Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(title)
                        .body(bodyList)
                        .inputs(inputList)
                        .canCloseWithEscape(false)
                        .build()
                )
                .type(DialogType.notice(confirmButton))
        }
    }

    /**
     * 构建注册对话框
     */
    fun buildRegisterDialog(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        confirmButton: ActionButton
    ): Dialog {
        val bodyList = mutableListOf<DialogBody>()
        val inputList = mutableListOf<DialogInput>()

        // 添加UI配置文件中的body
        bodyList.addAll(buildBodyListFromConfig(player, "ui.register.Body"))

        // 添加描述文本
        description?.let { bodyList.add(DialogBody.plainMessage(it)) }

        // 添加错误消息
        error?.let { bodyList.add(DialogBody.plainMessage(it)) }

        // 添加密码输入框
        inputList.add(
            DialogInput.text("reg_password", plugin.messageManager.getComponent("register.password-input"))
                .labelVisible(true)
                .maxLength(64)
                .build()
        )

        // 添加确认密码输入框
        inputList.add(
            DialogInput.text("reg_confirm_password", plugin.messageManager.getComponent("register.confirm-password-input"))
                .labelVisible(true)
                .maxLength(64)
                .build()
        )

        // 构建对话框
        return Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(title)
                        .body(bodyList)
                        .inputs(inputList)
                        .canCloseWithEscape(false)
                        .build()
                )
                .type(DialogType.notice(confirmButton))
        }
    }

    /**
     * 构建修改密码对话框
     */
    fun buildChangePasswordDialog(
        player: Player,
        title: Component,
        description: Component?,
        error: Component?,
        confirmButton: ActionButton,
        cancelButton: ActionButton
    ): Dialog {
        val bodyList = mutableListOf<DialogBody>()
        val inputList = mutableListOf<DialogInput>()

        // 添加UI配置文件中的body
        bodyList.addAll(buildBodyListFromConfig(player, "ui.change-password.Body"))

        // 添加描述文本
        description?.let { bodyList.add(DialogBody.plainMessage(it)) }

        // 添加错误消息
        error?.let { bodyList.add(DialogBody.plainMessage(it)) }

        // 添加旧密码输入框
        inputList.add(
            DialogInput.text("old_password", plugin.messageManager.getComponent("change-password.old-password-input"))
                .labelVisible(true)
                .maxLength(64)
                .build()
        )

        // 添加新密码输入框
        inputList.add(
            DialogInput.text("new_password", plugin.messageManager.getComponent("change-password.new-password-input"))
                .labelVisible(true)
                .maxLength(64)
                .build()
        )

        // 添加确认新密码输入框
        inputList.add(
            DialogInput.text("confirm_new_password", plugin.messageManager.getComponent("change-password.confirm-new-password-input"))
                .labelVisible(true)
                .maxLength(64)
                .build()
        )

        // 构建对话框
        return Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(title)
                        .body(bodyList)
                        .inputs(inputList)
                        .canCloseWithEscape(true)
                        .build()
                )
                .type(DialogType.confirmation(confirmButton, cancelButton))
        }
    }
}
