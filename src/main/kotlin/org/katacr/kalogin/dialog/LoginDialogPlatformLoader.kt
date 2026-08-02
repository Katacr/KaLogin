package org.katacr.kalogin.dialog

import org.katacr.kalogin.KaLogin

/**
 * 根据运行核心选择 Dialog 平台实现。
 */
object LoginDialogPlatformLoader {
    fun load(plugin: KaLogin): LoginDialogPlatform {
        val platform = tryLoad("io.papermc.paper.dialog.Dialog", "org.katacr.kalogin.dialog.paper.PaperLoginDialogPlatform")
            ?: tryLoad("net.md_5.bungee.api.dialog.Dialog", "org.katacr.kalogin.dialog.spigot.SpigotLoginDialogPlatform")
            ?: error("No supported Dialog API found. KaLogin requires Paper/Folia 1.21.7+ or Spigot 1.21.6+.")

        platform.initialize(plugin)
        return platform
    }

    private fun tryLoad(apiClass: String, adapterClass: String): LoginDialogPlatform? {
        return try {
            Class.forName(apiClass)
            Class.forName(adapterClass).getDeclaredConstructor().newInstance() as LoginDialogPlatform
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}
