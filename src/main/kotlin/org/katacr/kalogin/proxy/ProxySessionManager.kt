package org.katacr.kalogin.proxy

import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.katacr.kalogin.KaLogin
import org.katacr.kalogin.KaLoginScheduler
import org.katacr.kalogin.KaLoginTaskHandle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * KaProxy 跨服登录会话的后端管理器。
 *
 * 职责：进服时向代理查询该玩家是否已有有效会话（用于切服免登录），
 * 并在登录/注册成功、登出、注销、改密成功时把状态上报给代理。
 *
 * 代理是会话的唯一权威：后端只上报事实，不自行判定跨服会话有效性；
 * 玩家真正断开代理时由代理销毁会话（切服时后端 PlayerQuitEvent 不代表登出）。
 */
class ProxySessionManager(private val plugin: KaLogin) : PluginMessageListener {

    private var enabled = false
    private val pendingQueries = ConcurrentHashMap<UUID, PendingQuery>()
    private val timeoutTasks = ConcurrentHashMap<UUID, KaLoginTaskHandle>()

    /** 注册双通道（仅在 proxy.enabled 时）。 */
    fun init() {
        enabled = plugin.config.getBoolean("proxy.enabled", false)
        if (!enabled) {
            return
        }
        val messenger = plugin.server.messenger
        messenger.registerOutgoingPluginChannel(plugin, ProxyProtocol.CHANNEL)
        messenger.registerIncomingPluginChannel(plugin, ProxyProtocol.CHANNEL, this)
        plugin.logger.info("KaLogin 群组登录会话已启用（通道 ${ProxyProtocol.CHANNEL}）")
    }

    /** 注销通道并清理待处理查询。 */
    fun shutdown() {
        if (!enabled) {
            return
        }
        try {
            val messenger = plugin.server.messenger
            messenger.unregisterOutgoingPluginChannel(plugin, ProxyProtocol.CHANNEL)
            messenger.unregisterIncomingPluginChannel(plugin, ProxyProtocol.CHANNEL, this)
        } catch (error: Exception) {
            plugin.logger.warning("注销 KaLogin 群组会话通道失败: ${error.message}")
        }
        pendingQueries.clear()
        timeoutTasks.values.forEach { it.cancel() }
        timeoutTasks.clear()
        enabled = false
    }

    fun isEnabled(): Boolean = enabled

    /** 代理不可用时是否拒绝进入（否则回退本地登录/注册）。 */
    fun isRequireProxy(): Boolean = plugin.config.getBoolean("proxy.require-proxy", false)

    /** 是否应在跨服恢复时按条款状态弹欢迎界面。 */
    fun isRestoreWelcome(): Boolean = plugin.config.getBoolean("proxy.restore-welcome", true)

    /** 跨服恢复时是否重复弹邮箱绑定提示。 */
    fun isRestoreEmailPrompt(): Boolean = plugin.config.getBoolean("proxy.restore-email-prompt", false)

    /** 跨服恢复时是否重放 events.login 动作。 */
    fun isReplayLoginActions(): Boolean = plugin.config.getBoolean("proxy.replay-login-actions", false)

    /**
     * 查询玩家在代理侧是否已有有效登录会话。
     *
     * 结果通过回调在玩家线程返回；超时或代理未连通时返回 [QueryResult.UNAVAILABLE]。
     */
    fun query(player: Player, onResult: (QueryResult) -> Unit) {
        if (!enabled) {
            onResult(QueryResult.UNAVAILABLE)
            return
        }
        val uuid = player.uniqueId
        timeoutTasks.remove(uuid)?.cancel()
        pendingQueries[uuid] = PendingQuery(player, onResult)
        val delayTicks = plugin.config.getLong("proxy.query-delay-ticks", 3).coerceAtLeast(0L)
        KaLoginScheduler.runPlayerLater(player, delayTicks.coerceAtLeast(1L), Runnable {
            if (!player.isOnline) {
                complete(uuid, QueryResult.UNAVAILABLE)
                return@Runnable
            }
            if (!sendQuery(player)) {
                complete(uuid, QueryResult.UNAVAILABLE)
                return@Runnable
            }
            val timeoutMs = plugin.config.getLong("proxy.query-timeout-ms", 5000L)
            val timeoutTicks = (timeoutMs / 50L).coerceAtLeast(1L)
            timeoutTasks[uuid] = KaLoginScheduler.runPlayerLater(player, timeoutTicks, Runnable {
                complete(uuid, QueryResult.UNAVAILABLE)
            })
        })
    }

    private fun sendQuery(player: Player): Boolean {
        return try {
            val packet = ProxyProtocol.encode("kalogin", "query") { output ->
                ProxyProtocol.writeUuid(output, player.uniqueId)
                output.writeUTF(player.name)
                output.writeUTF(currentIp(player))
            }
            player.sendPluginMessage(plugin, ProxyProtocol.CHANNEL, packet)
            true
        } catch (error: Exception) {
            plugin.logger.warning("发送 KaLogin 群组会话查询失败: ${error.message}")
            false
        }
    }

    private fun complete(uuid: UUID, result: QueryResult) {
        val pending = pendingQueries.remove(uuid) ?: return
        timeoutTasks.remove(uuid)?.cancel()
        KaLoginScheduler.runPlayer(pending.player, Runnable {
            if (pending.player.isOnline) {
                pending.onResult(result)
            }
        })
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (!enabled || channel != ProxyProtocol.CHANNEL) {
            return
        }
        try {
            val packet = ProxyProtocol.decode(message)
            if (packet.module != "kalogin" || packet.action != "session") {
                return
            }
            val uuid = ProxyProtocol.readUuid(packet.input)
            val authenticated = packet.input.readBoolean()
            complete(uuid, if (authenticated) QueryResult.AUTHENTICATED else QueryResult.NOT_AUTHENTICATED)
        } catch (error: Exception) {
            plugin.logger.warning("解析 KaLogin 群组会话数据包失败: ${error.message}")
        }
    }

    /** 登录/注册成功：建立或刷新代理会话。 */
    fun reportAuth(player: Player) {
        send(player, "auth") { output ->
            ProxyProtocol.writeUuid(output, player.uniqueId)
            output.writeUTF(player.name)
            output.writeUTF(currentIp(player))
        }
    }

    /** 登出：使代理会话失效并由代理断开整个群组；返回是否已交由代理处理。 */
    fun reportLogout(player: Player): Boolean {
        if (!enabled) {
            return false
        }
        // 登出会使会话失效，位置记录由代理清除，本次退服不再写库
        plugin.lastSeenManager.suppressUpdate(player.uniqueId)
        plugin.dbManager.deleteLastSeen(player.uniqueId)
        send(player, "logout") { output ->
            ProxyProtocol.writeUuid(output, player.uniqueId)
        }
        return true
    }

    /** 注销账户：使代理会话失效。 */
    fun reportUnregister(uuid: UUID, name: String) {
        if (!enabled) {
            return
        }
        plugin.lastSeenManager.suppressUpdate(uuid)
        plugin.dbManager.deleteLastSeen(uuid)
        val carrier = plugin.server.onlinePlayers.firstOrNull() ?: return
        KaLoginScheduler.runPlayer(carrier, Runnable {
            if (carrier.isOnline) {
                send(carrier, "unregister") { output ->
                    ProxyProtocol.writeUuid(output, uuid)
                    output.writeUTF(name)
                }
            }
        })
    }

    /** 改密成功：刷新代理会话时间，保持当前登录态。 */
    fun reportPasswordChanged(player: Player) {
        send(player, "password_changed") { output ->
            ProxyProtocol.writeUuid(output, player.uniqueId)
        }
    }

    /** 玩家退服（含切服）时取消其未完成的查询，但不影响代理会话。 */
    fun onQuit(player: Player) {
        timeoutTasks.remove(player.uniqueId)?.cancel()
        pendingQueries.remove(player.uniqueId)
    }

    private fun send(player: Player, action: String, writer: ProxyProtocol.PacketWriter) {
        if (!enabled) {
            return
        }
        KaLoginScheduler.runPlayer(player, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }
            try {
                player.sendPluginMessage(plugin, ProxyProtocol.CHANNEL, ProxyProtocol.encode("kalogin", action, writer))
            } catch (error: Exception) {
                plugin.logger.warning("发送 KaLogin 群组会话数据包($action)失败: ${error.message}")
            }
        })
    }

    private fun currentIp(player: Player): String =
        player.address?.address?.hostAddress ?: ""

    /** 代理会话查询结果。 */
    enum class QueryResult {
        /** 代理确认存在有效会话，可恢复登录态。 */
        AUTHENTICATED,

        /** 代理确认无有效会话，走本地登录/注册。 */
        NOT_AUTHENTICATED,

        /** 代理未连通或应答超时。 */
        UNAVAILABLE
    }

    private data class PendingQuery(val player: Player, val onResult: (QueryResult) -> Unit)
}
