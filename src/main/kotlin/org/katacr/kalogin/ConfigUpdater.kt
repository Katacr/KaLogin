package org.katacr.kalogin

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.io.extension
import kotlin.io.nameWithoutExtension
import kotlin.text.toIntOrNull

/**
 * 配置文件更新管理器
 * 用于处理配置文件的版本升级、合并和备份
 */
object ConfigUpdater {

    private var languageManager: MessageManager? = null

    /**
     * 设置语言管理器
     * @param lm 语言管理器实例
     */
    fun setLanguageManager(lm: MessageManager) {
        languageManager = lm
    }

    /**
     * 获取本地化消息
     * 如果语言管理器未初始化，返回键本身
     */
    private fun getMessage(key: String, vararg args: Pair<String, Any>): String {
        val lm = languageManager
        return lm?.getMessage(key, *args)
            ?: // 如果语言管理器未初始化，使用硬编码的英文消息
            when (key) {
                "config_update.detected_old_version" -> "Detected old version config file (v${args.toMap()["old"]}), updating to v${args.toMap()["new"]}..."
                "config_update.update_success" -> "Config file updated successfully! Old version: v${args.toMap()["old"]} -> New version: v${args.toMap()["new"]}"
                "config_update.backup_success" -> "Old config backed up to: ${args.toMap()["path"]}"
                else -> key
            }
    }

    /**
     * 当前配置文件版本
     * 每次配置文件结构变更时需要增加此版本号
     */
    private const val CURRENT_CONFIG_VERSION = 3

    /**
     * 配置版本键名
     */
    private const val CONFIG_VERSION_KEY = "config-version"

    /**
     * 检查并更新配置文件
     * 流程：记录用户配置 → 备份 → 覆盖新配置 → 写入用户值
     * @param plugin 插件实例
     * @param configFile 配置文件
     * @return 是否进行了更新
     */
    fun checkAndUpdateConfig(plugin: JavaPlugin, configFile: File): Boolean {
        // 1. 记录用户当前的所有配置
        val userConfigValues = extractUserConfigValues(configFile)

        // 2. 检查配置版本
        val configVersion = userConfigValues[CONFIG_VERSION_KEY]?.toString()?.toIntOrNull() ?: 0

        // 3. 检测是否存在已废弃的节点
        val deprecatedKeys = userConfigValues.keys.filter { it.startsWith("ui.") }.toList()

        // 如果配置已是最新版本且没有废弃节点，无需更新
        if (configVersion >= CURRENT_CONFIG_VERSION && deprecatedKeys.isEmpty()) {
            return false
        }

        // 如果版本已是最新但存在废弃节点，记录警告
        if (configVersion >= CURRENT_CONFIG_VERSION && deprecatedKeys.isNotEmpty()) {
            plugin.logger.info("Detected deprecated config nodes: $deprecatedKeys, cleaning up...")
        } else {
            plugin.logger.info(getMessage("config_update.detected_old_version", "old" to configVersion, "new" to CURRENT_CONFIG_VERSION))
        }

        // 4. 备份旧配置文件
        backupConfig(plugin, configFile, configVersion)

        // 4. 从插件内部复制新配置文件覆盖用户的配置
        if (!copyDefaultConfig(plugin, configFile)) {
            return false
        }

        // 5. 将用户自定义的值写入到新配置文件
        writeUserValues(plugin, configFile, userConfigValues)

        plugin.logger.info(getMessage("config_update.update_success", "old" to configVersion, "new" to CURRENT_CONFIG_VERSION))
        return true
    }

    /**
     * 提取用户配置中的所有键值对
     * @param configFile 配置文件
     * @return 用户配置的键值对映射
     */
    private fun extractUserConfigValues(configFile: File): Map<String, Any?> {
        val config = YamlConfiguration.loadConfiguration(configFile)
        val userValues = mutableMapOf<String, Any?>()

        config.getKeys(true).forEach { key ->
            if (config.isString(key) || config.isBoolean(key) || config.isInt(key) || config.isDouble(key) || config.isList(key)) {
                userValues[key] = config.get(key)
            }
        }

        return userValues
    }

    /**
     * 从插件内部复制默认配置文件
     * @param plugin 插件实例
     * @param configFile 目标配置文件
     * @return 是否成功
     */
    private fun copyDefaultConfig(plugin: JavaPlugin, configFile: File): Boolean {
        try {
            // 1. 从 jar 中读取默认配置文件
            val inputStream = plugin.getResource("config.yml")
                ?: throw IOException("config.yml not found in plugin jar")
            
            // 2. 直接复制到目标位置（先删除旧的）
            if (configFile.exists()) {
                Files.delete(configFile.toPath())
            }
            Files.copy(inputStream, configFile.toPath())
            inputStream.close()
            return true
        } catch (e: IOException) {
            plugin.logger.severe(getMessage("config_update.save_failed", "error" to (e.message ?: "unknown error")))
            return false
        }
    }

    /**
     * 将用户自定义的值写入到新配置文件
     * @param plugin 插件实例
     * @param configFile 配置文件
     * @param userValues 用户配置值
     */
    private fun writeUserValues(plugin: JavaPlugin, configFile: File, userValues: Map<String, Any?>) {
        try {
            val config = YamlConfiguration.loadConfiguration(configFile)
            // 获取新配置的所有键（精确匹配）
            val newConfigKeys = config.getKeys(true)

            userValues.forEach { (key, value) ->
                // 跳过版本号
                if (key == CONFIG_VERSION_KEY) {
                    return@forEach
                }
                // 只写入新配置文件中明确存在的键
                if (newConfigKeys.contains(key)) {
                    config.set(key, value)
                }
            }

            config.save(configFile)
        } catch (e: IOException) {
            plugin.logger.warning(getMessage("config_update.save_failed", "error" to (e.message ?: "unknown error")))
        }
    }

    /**
     * 备份配置文件
     * @param plugin 插件实例
     * @param configFile 配置文件
     * @param configVersion 旧配置版本号
     */
    private fun backupConfig(plugin: JavaPlugin, configFile: File, configVersion: Int) {
        val backupFile = File(configFile.parent, getBackupFileName(configFile, configVersion))

        try {
            Files.copy(
                configFile.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            plugin.logger.info(getMessage("config_update.backup_success", "path" to backupFile.name))
        } catch (e: IOException) {
            plugin.logger.warning(getMessage("config_update.backup_failed", "error" to (e.message ?: "unknown error")))
        }
    }

    /**
     * 获取备份文件名
     * @param configFile 配置文件
     * @param configVersion 配置版本号
     * @return 备份文件名
     */
    private fun getBackupFileName(configFile: File, configVersion: Int): String {
        val timestamp = System.currentTimeMillis()
        val baseName = configFile.nameWithoutExtension
        return "${baseName}_v${configVersion}_backup_${timestamp}.${configFile.extension}"
    }

    /**
     * 获取当前配置版本
     * @param config 配置文件
     * @return 配置版本号
     */
    fun getConfigVersion(config: FileConfiguration): Int {
        return config.getInt(CONFIG_VERSION_KEY, 0)
    }

    /**
     * 设置配置版本
     * @param config 配置文件
     * @param version 版本号
     */
    fun setConfigVersion(config: FileConfiguration, version: Int) {
        config.set(CONFIG_VERSION_KEY, version)
    }
}
