package org.katacr.kalogin

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Geyser 兼容层（纯反射实现，避免类加载器冲突）
 *
 * 对基岩版玩家使用 canCloseWithEscape(true) + 服务器端控制重开，
 * 避免 Geyser 对 canCloseWithEscape=false 的对话框自动重开导致无法关闭。
 */
object GeyserCompat {

    private var geyserAvailable = false

    // 反射缓存
    private var apiMethod: java.lang.reflect.Method? = null
    private var connectionByUuidMethod: java.lang.reflect.Method? = null

    fun init(plugin: KaLogin) {
        try {
            val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            apiMethod = geyserApiClass.getMethod("api")
            connectionByUuidMethod = geyserApiClass.getMethod("connectionByUuid", UUID::class.java)

            geyserAvailable = true
            plugin.logger.info("Geyser API detected, bedrock dialog compatibility enabled.")
        } catch (e: ClassNotFoundException) {
            geyserAvailable = false
        } catch (e: Exception) {
            geyserAvailable = false
            plugin.logger.warning("Failed to initialize Geyser compatibility: ${e.message}")
        }
    }

    /**
     * 判断玩家是否为基岩版玩家
     */
    fun isBedrockPlayer(player: Player): Boolean {
        if (!geyserAvailable) return false
        return try {
            val api = apiMethod!!.invoke(null)
            connectionByUuidMethod!!.invoke(api, player.uniqueId) != null
        } catch (e: Exception) {
            false
        }
    }
}
