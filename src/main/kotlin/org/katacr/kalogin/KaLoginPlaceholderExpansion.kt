package org.katacr.kalogin

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class KaLoginPlaceholderExpansion(private val plugin: KaLogin) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "kalogin"

    override fun getAuthor(): String = "Katacr"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String {
        if (player == null) return ""

        val snapshot = plugin.dbManager.getPlayerInfoSnapshot(player.uniqueId).join() ?: return ""

        return when (params.lowercase()) {
            "email_masked" -> snapshot.email?.let { maskEmail(it) } ?: ""
            "email_plain" -> snapshot.email ?: ""
            "accepted_terms" -> snapshot.acceptedTerms.toString()
            "last_login_ip" -> snapshot.lastLoginIp ?: ""
            "auto_login_by_ip" -> snapshot.autoLoginByIp.toString()
            "register_time" -> snapshot.regDate ?: ""
            else -> ""
        }
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@", limit = 2)
        if (parts.size != 2) return email
        val name = parts[0]
        val masked = when {
            name.isEmpty() -> "****"
            name.length == 1 -> name + "****"
            name.length == 2 -> name.first() + "****"
            else -> name.take(2) + "****"
        }
        return "$masked@${parts[1]}"
    }
}