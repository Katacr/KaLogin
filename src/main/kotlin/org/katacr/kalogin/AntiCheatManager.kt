package org.katacr.kalogin

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 防作弊管理器
 * 在玩家未登录/未注册期间限制其行为
 */
class AntiCheatManager(private val plugin: KaLogin) : Listener {

    // 跟踪需要认证的玩家（未登录或未注册）
    private val authenticatingPlayers = ConcurrentHashMap<UUID, Player>()

    // 保存玩家的登录前游戏模式
    private val playerGameModes = ConcurrentHashMap<UUID, org.bukkit.GameMode>()

    // 跟踪登录超时任务
    val loginTimeoutTasks = ConcurrentHashMap<UUID, Int>()

    // 跟踪注册超时任务
    val registerTimeoutTasks = ConcurrentHashMap<UUID, Int>()

    // 跟踪玩家当前的对话框类型（login 或 register）
    private val playerDialogTypes = ConcurrentHashMap<UUID, String>()

    // 跟踪上次重新显示对话框的时间（防抖机制）
    private val lastDialogReshowTimes = ConcurrentHashMap<UUID, Long>()

    /**
     * 开始认证状态（注册或登录）
     */
    fun startAuthenticating(player: Player) {
        val uuid = player.uniqueId

        // 保存游戏模式
        playerGameModes[uuid] = player.gameMode

        // 设置为旁观者模式（从根源阻止破坏性行为）
        player.gameMode = org.bukkit.GameMode.SPECTATOR

        // 给予无限夜视效果（让玩家看不清周围）
        player.addPotionEffect(
            org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.DARKNESS,
                Int.MAX_VALUE,
                255,
                false,
                false
            )
        )

        // 给予缓慢效果（防止快速移动）
        player.addPotionEffect(
            org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS,
                Int.MAX_VALUE,
                5,
                false,
                false
            )
        )

        authenticatingPlayers[uuid] = player
    }

    /**
     * 结束认证状态（登录或注册成功）
     */
    fun endAuthenticating(player: Player) {
        val uuid = player.uniqueId

        // 移除所有负面效果
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS)
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)

        // 强制恢复为生存模式
        player.gameMode = org.bukkit.GameMode.SURVIVAL

        // 清理游戏模式缓存
        playerGameModes.remove(uuid)

        // 清理数据
        authenticatingPlayers.remove(uuid)
        playerDialogTypes.remove(uuid)
        lastDialogReshowTimes.remove(uuid)
    }

    /**
     * 检查玩家是否在认证中
     */
    fun isAuthenticating(uuid: UUID): Boolean {
        return authenticatingPlayers.containsKey(uuid)
    }

    /**
     * 检查玩家是否在认证中
     */
    fun isAuthenticating(player: Player): Boolean {
        return isAuthenticating(player.uniqueId)
    }

    /**
     * 设置玩家当前的对话框类型
     */
    fun setPlayerDialogType(player: Player, type: String) {
        playerDialogTypes[player.uniqueId] = type
    }

    /**
     * 获取玩家当前的对话框类型
     */
    fun getPlayerDialogType(player: Player): String? {
        return playerDialogTypes[player.uniqueId]
    }

    /**
     * 清除玩家的对话框类型
     */
    fun clearPlayerDialogType(player: Player) {
        playerDialogTypes.remove(player.uniqueId)
    }

    /**
     * 玩家移动事件 - 冻结位置并重新显示对话框
     */
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return

        // 只检测位置移动，不检测视角移动
        val hasMoved = event.from.x != event.to.x ||
                      event.from.y != event.to.y ||
                      event.from.z != event.to.z

        if (!hasMoved) return

        // 如果检测到移动，重新显示对话框
        val dialogType = getPlayerDialogType(player)
        if (dialogType != null) {
            val now = System.currentTimeMillis()
            val lastReshowTime = lastDialogReshowTimes[player.uniqueId] ?: 0

            // 防抖机制：2秒内不重复显示对话框
            if (now - lastReshowTime < 2000) return

            lastDialogReshowTimes[player.uniqueId] = now

            // 延迟一 tick 重新显示对话框，避免频繁触发
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!isAuthenticating(player)) return@Runnable

                // 根据 AuthMe 是否真正启用来决定调用哪个监听器的方法
                // 使用 authMeManager.useAuthMe 而非直接读配置，因为配置可能启用但 AuthMe 未安装
                when (dialogType) {
                    "login" -> {
                        if (plugin.authMeManager.useAuthMe) {
                            // AuthMe 模式下，这里不应该被调用，因为 AuthMeLoginListener 有自己的防抖机制
                            plugin.logger.warning("AntiCheatManager trying to show login dialog in AuthMe mode")
                        } else {
                            plugin.loginListener.showLoginDialog(player)
                        }
                    }
                    "register" -> {
                        if (plugin.authMeManager.useAuthMe) {
                            // AuthMe 模式下，这里不应该被调用，因为 AuthMeLoginListener 有自己的防抖机制
                            plugin.logger.warning("AntiCheatManager trying to show register dialog in AuthMe mode")
                        } else {
                            plugin.loginListener.showRegisterDialog(player, plugin.messageManager.getMessage("register.welcome", "seconds" to 90))
                        }
                    }
                }
            })
        }

        // 冻结位置移动（但不冻结视角）
        event.to = event.from
    }

    /**
     * 玩家聊天事件 - 禁止聊天
     */
    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return

        event.isCancelled = true
        plugin.messageManager.sendMessage(player, "anti-cheat.chat-blocked")
    }

    /**
     * 玩家命令事件 - 只允许允许的命令
     */
    @EventHandler
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return

        val command = event.message.lowercase()

        // 允许 /login 和 /register 命令（如果存在）
        val allowedCommands = listOf("/kalogin", "/kl")
        val isAllowed = allowedCommands.any { command.startsWith(it) }

        if (!isAllowed) {
            event.isCancelled = true
            plugin.messageManager.sendMessage(player, "anti-cheat.command-blocked")
        }
    }

    /**
     * 玩家退出事件 - 清理数据
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 取消登录超时任务
        loginTimeoutTasks[uuid]?.let { taskId ->
            plugin.server.scheduler.cancelTask(taskId)
            loginTimeoutTasks.remove(uuid)
        }

        // 取消注册超时任务
        registerTimeoutTasks[uuid]?.let { taskId ->
            plugin.server.scheduler.cancelTask(taskId)
            registerTimeoutTasks.remove(uuid)
        }

        // 清理 LoginListener 的数据
        plugin.loginListener.clearPlayerData(uuid)

        // 结束认证状态
        if (isAuthenticating(player)) {
            endAuthenticating(player)
        }
    }

    /**
     * 清理所有数据
     */
    fun clearAll() {
        authenticatingPlayers.clear()
        playerGameModes.clear()
        loginTimeoutTasks.clear()
        registerTimeoutTasks.clear()
    }
}
