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
     * 玩家移动事件 - 冻结位置
     */
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // 只在位置实际变化时检查（转头不算）
        if (event.from.x == event.to.x && event.from.y == event.to.y && event.from.z == event.to.z) {
            return
        }

        val player = event.player
        if (!isAuthenticating(player)) return

        // 简单直接：将目标位置设置为源位置（比TP更高效）
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
