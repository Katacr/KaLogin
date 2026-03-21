package org.katacr.kalogin

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.InputStreamReader

class MessageManager(private val plugin: KaLogin) {

    private val languages = mutableMapOf<String, YamlConfiguration>()
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private var defaultLanguage = "zh_CN"

    fun init() {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }

        loadLanguageFile("zh_CN", langFolder)
        loadLanguageFile("en_US", langFolder)

        defaultLanguage = plugin.config.getString("language", "zh_CN") ?: "zh_CN"
    }

    private fun loadLanguageFile(locale: String, langFolder: File) {
        try {
            val langFile = File(langFolder, "$locale.yml")

            if (!langFile.exists()) {
                plugin.saveResource("lang/$locale.yml", false)
            }

            if (langFile.exists()) {
                val config = YamlConfiguration.loadConfiguration(langFile)
                languages[locale] = config
            } else {
                val inputStream = plugin.getResource("lang/$locale.yml")
                if (inputStream != null) {
                    val config = YamlConfiguration.loadConfiguration(InputStreamReader(inputStream))
                    languages[locale] = config
                    plugin.logger.warning("语言文件 $locale.yml 无法释放到磁盘，已从插件资源加载")
                } else {
                    plugin.logger.warning("找不到语言文件资源: lang/$locale.yml")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("加载语言文件 $locale 失败: ${e.message}")
        }
    }

    /**
     * 获取玩家的语言设置
     */
    private fun getPlayerLanguage(player: Player): String {
        // 可以从玩家配置或客户端语言获取
        // 这里暂时使用全局配置的语言
        return defaultLanguage
    }

    /**
     * 获取消息
     */
    fun getMessage(key: String, locale: String? = null): String {
        val lang = locale ?: defaultLanguage
        return languages[lang]?.getString(key, key) ?: key
    }

    /**
     * 获取带参数的消息
     */
    fun getMessage(key: String, vararg args: Pair<String, Any>, locale: String? = null): String {
        var message = getMessage(key, locale)
        args.forEach { (param, value) ->
            message = message.replace("{$param}", value.toString())
        }
        return message
    }

    /**
     * 转换颜色代码 (& 和 § 转为 Adventure Component)
     */
    private fun convertColors(message: String): Component {
        // 先转换 & 为 §，再转换为 LegacyComponent
        val converted = ChatColor.translateAlternateColorCodes('&', message)
        return legacySerializer.deserialize(converted)
    }

    /**
     * 从消息字符串转换为 Component
     */
    fun getComponentFromMessage(message: String): Component {
        // 检查是否包含 MiniMessage 标签或 Legacy 颜色代码
        return if (message.contains("<") || message.contains("&") || message.contains("§")) {
            // 如果包含 Legacy 颜色代码，优先使用 Legacy 转换
            if (message.contains("&") || message.contains("§")) {
                convertColors(message)
            } else {
                miniMessage.deserialize(message)
            }
        } else {
            Component.text(message)
        }
    }

    /**
     * 获取带颜色的消息组件（支持 MiniMessage 和 &/§ 颜色代码）
     */
    fun getComponent(key: String, vararg args: Pair<String, Any>, locale: String? = null): Component {
        val lang = locale ?: defaultLanguage
        var message = languages[lang]?.getString(key, key) ?: key
        args.forEach { (param, value) ->
            message = message.replace("{$param}", value.toString())
        }

        // 检查是否包含 MiniMessage 标签或 Legacy 颜色代码
        return if (message.contains("<") || message.contains("&") || message.contains("§")) {
            // 如果包含 Legacy 颜色代码，优先使用 Legacy 转换
            if (message.contains("&") || message.contains("§")) {
                convertColors(message)
            } else {
                miniMessage.deserialize(message)
            }
        } else {
            Component.text(message)
        }
    }

    /**
     * 给玩家发送消息（支持 &/§ 颜色代码）
     */
    fun sendMessage(player: Player, key: String, vararg args: Pair<String, Any>) {
        val locale = getPlayerLanguage(player)
        val message = getMessage(key, *args, locale = locale)

        // 检查是否包含颜色代码
        val component = if (message.contains("&") || message.contains("§")) {
            convertColors(message)
        } else {
            miniMessage.deserialize(message)
        }
        player.sendMessage(component)
    }

    /**
     * 给命令发送者发送消息（支持 &/§ 颜色代码）
     */
    fun sendMessage(sender: org.bukkit.command.CommandSender, key: String, vararg args: Pair<String, Any>) {
        val locale = defaultLanguage // 控制台和命令默认使用配置语言
        val message = getMessage(key, *args, locale = locale)

        // 检查是否包含颜色代码
        val component = if (message.contains("&") || message.contains("§")) {
            convertColors(message)
        } else {
            miniMessage.deserialize(message)
        }
        sender.sendMessage(component)
    }

    /**
     * 重载语言文件
     */
    fun reload() {
        languages.clear()
        val langFolder = File(plugin.dataFolder, "lang")
        loadLanguageFile("zh_CN", langFolder)
        loadLanguageFile("en_US", langFolder)
        defaultLanguage = plugin.config.getString("language", "zh_CN") ?: "zh_CN"
    }
}
