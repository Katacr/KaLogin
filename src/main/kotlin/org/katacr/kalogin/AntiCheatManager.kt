package org.katacr.kalogin

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
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

    // 保存认证期间的锁定位置（含朝向）
    private val lockedLocations = ConcurrentHashMap<UUID, Location>()

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

        // 记录认证期间的锁定位置（包括视角）
        lockedLocations[uuid] = player.location.clone()

        // 设置为旁观者模式（从根源阻止破坏性行为）
        player.gameMode = org.bukkit.GameMode.SPECTATOR
        player.isFlying = false
        player.flySpeed = 0f
        player.walkSpeed = 0f

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

        // 恢复认证前的游戏模式
        player.gameMode = playerGameModes[uuid] ?: org.bukkit.GameMode.SURVIVAL
        player.flySpeed = 0.1f
        player.walkSpeed = 0.2f

        // 清理游戏模式缓存
        playerGameModes.remove(uuid)
        lockedLocations.remove(uuid)

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

    private fun restoreLockedLocation(player: Player) {
        val locked = lockedLocations[player.uniqueId] ?: return
        val current = player.location
        if (current.world != locked.world || current.distanceSquared(locked) > 0.0 || current.yaw != locked.yaw || current.pitch != locked.pitch) {
            player.teleport(locked)
        }
    }

    private fun resendDialog(player: Player) {
        val dialogType = getPlayerDialogType(player) ?: return
        val now = System.currentTimeMillis()
        val lastReshowTime = lastDialogReshowTimes[player.uniqueId] ?: 0
        if (now - lastReshowTime < 1500) return

        lastDialogReshowTimes[player.uniqueId] = now
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!isAuthenticating(player) || !player.isOnline) return@Runnable
            when (dialogType) {
                "login" -> plugin.showLoginDialogForPlayer(player)
                "register" -> plugin.showRegisterDialogForPlayer(player)
            }
        })
    }

    private fun blockAndRestore(player: Player, messageKey: String? = null, resendDialog: Boolean = false) {
        restoreLockedLocation(player)
        if (messageKey != null) {
            plugin.messageManager.sendMessage(player, messageKey)
        }
        if (resendDialog) {
            resendDialog(player)
        }
    }

    /**
     * 玩家移动事件 - 冻结位置并重新显示对话框
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return

        val to = event.to ?: return

        // 同时检测位置移动和视角变化，防止关闭 Dialog 后转头观察环境
        val hasMoved = event.from.x != event.to.x ||
                      event.from.y != event.to.y ||
                      event.from.z != event.to.z
        val hasRotated = event.from.yaw != to.yaw || event.from.pitch != to.pitch

        if (!hasMoved && !hasRotated) return

        event.to = event.from
        if (hasMoved || hasRotated) {
            resendDialog(player)
        }
    }

    /**
     * 玩家聊天事件 - 禁止聊天
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return

        event.isCancelled = true
        plugin.messageManager.sendMessage(player, "anti-cheat.chat-blocked")
    }

    /**
     * 玩家命令事件 - 只允许允许的命令
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return

        val command = event.message.lowercase()

        // 允许 /login 和 /register 命令（如果存在）
        val allowedCommands = listOf("/kalogin", "/kl", "/recoverpassword", "/rp", "/bindemail")
        val isAllowed = allowedCommands.any { command.startsWith(it) }

        if (!isAllowed) {
            event.isCancelled = true
            plugin.messageManager.sendMessage(player, "anti-cheat.command-blocked")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player, resendDialog = true)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player, resendDialog = true)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player, resendDialog = true)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player, resendDialog = true)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player, resendDialog = true)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (!isAuthenticating(player)) return
        resendDialog(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPickupItem(event: PlayerAttemptPickupItemEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onToggleFlight(event: PlayerToggleFlightEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onToggleSprint(event: PlayerToggleSprintEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPortal(event: PlayerPortalEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        if (event.cause == PlayerTeleportEvent.TeleportCause.UNKNOWN || event.cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return
        }
        event.isCancelled = true
        blockAndRestore(player, resendDialog = true)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBedEnter(event: PlayerBedEnterEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamageOthers(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        if (!isAuthenticating(player)) return
        event.isCancelled = true
        blockAndRestore(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (!isAuthenticating(player)) return
        event.isCancelled = true
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
        lockedLocations.clear()
        loginTimeoutTasks.clear()
        registerTimeoutTasks.clear()
    }
}
