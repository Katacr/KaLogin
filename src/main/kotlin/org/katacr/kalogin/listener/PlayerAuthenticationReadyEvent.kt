package org.katacr.kalogin.listener

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** 认证收尾与上次位置处理完成后，供其他插件安全执行待办落点。 */
class PlayerAuthenticationReadyEvent(val player: Player) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
