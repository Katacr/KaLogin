package org.katacr.kalogin.proxy

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.katacr.kalogin.KaLogin
import org.katacr.kalogin.KaLoginScheduler
import org.katacr.kalogin.KaLoginTaskHandle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * “上次下线位置”的后端管理器。
 *
 * 职责：
 * - 玩家退服（含切服）时把当前坐标写入共享数据库（server 列由代理在真正离开时写入）；
 * - 玩家认证完成后，接收代理为本玩家预留的 `teleport`，校验世界与高度后传送；
 * - 位置所在世界无效时向代理发送 `abort`，由代理回退到默认子服；
 * - 认证完成后发送 `ready`，由代理决定是否把玩家切回上次所在子服。
 *
 * 坐标直接写库，不依赖插件消息载体，因此单人子服退服也能可靠记录。
 * 传送在认证完成后应用，避免与登录期间的防作弊位置锁定冲突。
 */
class LastSeenManager(private val plugin: KaLogin) : PluginMessageListener {

    private var enabled = false
    private var serverName = ""
    private var blacklist: List<String> = emptyList()
    private val pendingTeleports = ConcurrentHashMap<UUID, PendingTeleport>()
    private val suppressUpdate = ConcurrentHashMap.newKeySet<UUID>()
    // 手动模式下已下发、等待延迟弹窗的任务（退服/登出时取消）
    private val pendingOffers = ConcurrentHashMap<UUID, KaLoginTaskHandle>()

    /** 注册通道（仅在同时启用 proxy 与 last-seen 时）。 */
    fun init() {
        enabled = plugin.config.getBoolean("proxy.enabled", false) &&
            plugin.config.getBoolean("last-seen.enabled", true)
        if (!enabled) {
            return
        }
        val messenger = plugin.server.messenger
        messenger.registerOutgoingPluginChannel(plugin, ProxyProtocol.CHANNEL)
        messenger.registerIncomingPluginChannel(plugin, ProxyProtocol.CHANNEL, this)
        serverName = plugin.config.getString("proxy.server-name", "")?.trim().orEmpty()
        if (serverName.isEmpty()) {
            plugin.logger.warning("未配置 proxy.server-name，退服时不会记录上次位置")
        }
        blacklist = plugin.config.getStringList("last-seen.blacklist")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        plugin.logger.info("KaLogin 上次下线位置已启用")
    }

    fun shutdown() {
        if (!enabled) {
            return
        }
        try {
            val messenger = plugin.server.messenger
            messenger.unregisterOutgoingPluginChannel(plugin, ProxyProtocol.CHANNEL)
            messenger.unregisterIncomingPluginChannel(plugin, ProxyProtocol.CHANNEL, this)
        } catch (error: Exception) {
            plugin.logger.warning("注销 KaLogin 上次下线位置通道失败: ${error.message}")
        }
        pendingTeleports.clear()
        suppressUpdate.clear()
        pendingOffers.values.forEach { it.cancel() }
        pendingOffers.clear()
        enabled = false
    }

    fun isEnabled(): Boolean = enabled

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (!enabled || channel != ProxyProtocol.CHANNEL) {
            return
        }
        try {
            val packet = ProxyProtocol.decode(message)
            if (packet.module != "lastseen") {
                return
            }
            when (packet.action) {
                "teleport" -> handleTeleport(packet.input)
                "offer" -> handleOffer(packet.input)
            }
        } catch (error: Exception) {
            plugin.logger.warning("解析 KaLogin 位置数据包失败: ${error.message}")
        }
    }

    private fun handleTeleport(input: java.io.DataInputStream) {
        val uuid = ProxyProtocol.readUuid(input)
        val world = input.readUTF()
        val x = input.readDouble()
        val y = input.readDouble()
        val z = input.readDouble()
        val yaw = input.readFloat()
        val pitch = input.readFloat()
        pendingTeleports[uuid] = PendingTeleport(
            world, x, y, z, yaw, pitch, System.currentTimeMillis() + PENDING_TTL_MILLIS
        )
        // 玩家已在线且已完成认证时立即应用；否则等认证完成后的 onAuthenticated
        val online = plugin.server.getPlayer(uuid)
        if (online != null && !plugin.antiCheatManager.isAuthenticating(online)) {
            KaLoginScheduler.runPlayer(online, Runnable { applyPending(online) })
        }
    }

    /**
     * 手动模式：代理询问是否返回上次位置。延迟 delaySeconds 后弹出确认框，
     * 玩家点击“确定”回传 `confirm`（由代理切服并投递坐标），点击“取消”回传 `decline`。
     */
    private fun handleOffer(input: java.io.DataInputStream) {
        val uuid = ProxyProtocol.readUuid(input)
        val delaySeconds = input.readInt()
        input.readUTF() // 目标子服名，仅作提示，代理侧仍会重新读取记录
        val player = plugin.server.getPlayer(uuid) ?: return
        pendingOffers.remove(uuid)?.cancel()
        val task = KaLoginScheduler.runPlayerLater(player, delaySeconds * 20L) {
            pendingOffers.remove(uuid)
            if (!player.isOnline) {
                return@runPlayerLater
            }
            showLastSeenConfirm(player)
        }
        pendingOffers[uuid] = task
    }

    /** 弹出“是否返回上次位置”确认框（认证期间旋转视角被反作弊重弹时也会调用）。 */
    fun showLastSeenConfirm(player: Player) {
        plugin.antiCheatManager.setPlayerDialogType(player, "last-seen")
        plugin.antiCheatManager.markDialogOpened(player)
        plugin.dialogPlatform.showLastSeen(
            player,
            plugin.messageManager.getComponent("last-seen.dialog-title"),
            listOf(plugin.messageManager.getComponent("last-seen.dialog-body")),
            plugin.messageManager.getComponent("last-seen.confirm-button"),
            plugin.messageManager.getComponent("last-seen.cancel-button"),
            onConfirm = {
                plugin.antiCheatManager.markDialogClosed(player)
                plugin.antiCheatManager.markProgrammaticClose(player)
                plugin.dialogPlatform.close(player)
                send(player, "confirm") { output -> ProxyProtocol.writeUuid(output, player.uniqueId) }
            },
            onCancel = {
                plugin.antiCheatManager.markDialogClosed(player)
                plugin.antiCheatManager.markProgrammaticClose(player)
                plugin.dialogPlatform.close(player)
                send(player, "decline") { output -> ProxyProtocol.writeUuid(output, player.uniqueId) }
            }
        )
    }

    /** 认证完成后的收尾：应用预留传送并通知代理可以前往。 */
    fun onAuthenticated(player: Player) {
        if (!enabled) {
            return
        }
        applyPending(player)
        send(player, "ready") { output -> ProxyProtocol.writeUuid(output, player.uniqueId) }
    }

    /** 玩家退服（含切服）：写入坐标到共享数据库并清理未应用的传送。 */
    fun onPlayerQuit(player: Player) {
        if (!enabled) {
            return
        }
        pendingTeleports.remove(player.uniqueId)
        pendingOffers.remove(player.uniqueId)?.cancel()
        // 登出/注销已使会话失效，位置记录由代理清除，本次退服不再写库以免复活记录
        if (suppressUpdate.remove(player.uniqueId)) {
            return
        }
        reportUpdate(player)
    }

    /**
     * 服务器关闭前为所有在线玩家写一次位置。
     *
     * <p>正常退服依赖 `PlayerQuitEvent`，但停服时该事件可能晚于 `onDisable` 触发，
     * 导致异步写被已关闭的执行器丢弃。此处在 `dbManager.close()` 之前主动写库，
     * 由 `close()` 的 `awaitTermination` 等待落盘。
     */
    fun reportAllOnline() {
        if (!enabled) {
            return
        }
        for (player in plugin.server.onlinePlayers) {
            pendingTeleports.remove(player.uniqueId)
            pendingOffers.remove(player.uniqueId)?.cancel()
            if (suppressUpdate.remove(player.uniqueId)) {
                continue
            }
            reportUpdate(player)
        }
    }

    /** 标记该玩家本次退服不写库（登出/注销时由 ProxySessionManager 调用）。 */
    fun suppressUpdate(uuid: UUID) {
        if (plugin.server.getPlayer(uuid) != null) {
            suppressUpdate.add(uuid)
        }
    }

    /** 判断子服是否在“不记录位置”黑名单中（大小写不敏感）。 */
    private fun isBlacklisted(server: String): Boolean {
        if (server.isBlank()) {
            return false
        }
        return blacklist.any { it.equals(server, ignoreCase = true) }
    }

    private fun reportUpdate(player: Player) {
        // 黑名单子服不写坐标：下次登录无记录，直连默认服出生点，避免残留错误坐标
        if (isBlacklisted(serverName)) {
            return
        }
        val location = player.location
        val uuid = player.uniqueId
        val world = player.world.name
        // 事件时间戳保证切服乱序时最后一次写入胜出
        val updatedAt = System.currentTimeMillis()
        plugin.dbManager
            .updateLastSeen(uuid, serverName, world, location.x, location.y, location.z,
                location.yaw, location.pitch, updatedAt)
            .exceptionally { error ->
                plugin.logger.warning("写入上次位置失败: ${error.message}")
                false
            }
    }

    private fun applyPending(player: Player) {
        val target = pendingTeleports.remove(player.uniqueId) ?: return
        if (target.expireAt < System.currentTimeMillis()) {
            return
        }
        val world = plugin.server.getWorld(target.world)
        if (world == null || target.y < world.minHeight || target.y > world.maxHeight) {
            send(player, "abort") { output -> ProxyProtocol.writeUuid(output, player.uniqueId) }
            return
        }
        KaLoginScheduler.teleport(player, Location(world, target.x, target.y, target.z, target.yaw, target.pitch))
    }

    private fun send(player: Player, action: String, writer: ProxyProtocol.PacketWriter) {
        if (!enabled) {
            return
        }
        KaLoginScheduler.runPlayer(player, Runnable {
            if (player.isOnline) {
                sendNow(player, action, writer)
            }
        })
    }

    private fun sendNow(player: Player, action: String, writer: ProxyProtocol.PacketWriter): Boolean {
        return try {
            player.sendPluginMessage(plugin, ProxyProtocol.CHANNEL, ProxyProtocol.encode("lastseen", action, writer))
            true
        } catch (error: Exception) {
            plugin.logger.warning("发送 KaLogin 位置数据包($action)失败: ${error.message}")
            false
        }
    }

    private companion object {
        const val PENDING_TTL_MILLIS = 60_000L
    }

    private data class PendingTeleport(
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val expireAt: Long
    )
}
