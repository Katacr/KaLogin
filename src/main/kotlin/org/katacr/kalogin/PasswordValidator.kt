package org.katacr.kalogin

import java.util.regex.Pattern

/**
 * 密码验证器
 */
class PasswordValidator(private val config: KaLogin) {

    private val settings = config.config.getConfigurationSection("settings")!!

    /**
     * 验证密码并返回错误信息，验证成功返回 null
     */
    fun validate(password: String): String? {
        // 检查密码长度
        val minLength = settings.getInt("min-password-length", 6)
        val maxLength = settings.getInt("max-password-length", 20)

        if (password.length < minLength) {
            return config.messageManager.getMessage("password-validation.too-short", "min" to minLength)
        }

        if (password.length > maxLength) {
            return config.messageManager.getMessage("password-validation.too-long", "max" to maxLength)
        }

        // 检查黑名单
        val blacklist = settings.getStringList("password-blacklist")
        if (blacklist.contains(password)) {
            return config.messageManager.getMessage("password-validation.in-blacklist", "word" to password)
        }

        // 检查正则表达式（如果配置了）
        val regex = settings.getString("password-regex", "")
        if (!regex.isNullOrEmpty()) {
            if (!Pattern.matches(regex, password)) {
                return config.messageManager.getMessage("password-validation.invalid-chars")
            }
        }

        // 检查大写字母
        if (settings.getBoolean("has-uppercase", true)) {
            if (!password.any { it.isUpperCase() }) {
                return config.messageManager.getMessage("password-validation.missing-uppercase")
            }
        }

        // 检查小写字母
        if (settings.getBoolean("has-lowercase", true)) {
            if (!password.any { it.isLowerCase() }) {
                return config.messageManager.getMessage("password-validation.missing-lowercase")
            }
        }

        // 检查数字
        if (settings.getBoolean("has-number", true)) {
            if (!password.any { it.isDigit() }) {
                return config.messageManager.getMessage("password-validation.missing-number")
            }
        }

        // 检查特殊符号
        if (settings.getBoolean("has-symbol", true)) {
            if (!password.any { !it.isLetterOrDigit() }) {
                return config.messageManager.getMessage("password-validation.missing-symbol")
            }
        }

        // 检查连续相同数字
        val maxConsecutiveSameDigits = settings.getInt("max-consecutive-same-digits", 0)
        if (maxConsecutiveSameDigits > 0) {
            val consecutiveCount = checkConsecutiveSameDigits(password)
            if (consecutiveCount >= maxConsecutiveSameDigits) {
                return config.messageManager.getMessage("password-validation.too-many-same-digits", "max" to maxConsecutiveSameDigits)
            }
        }

        return null // 验证通过
    }

    /**
     * 检查密码中连续相同数字的最大数量
     */
    private fun checkConsecutiveSameDigits(password: String): Int {
        var maxCount = 0
        var currentCount = 0
        var prevDigit: Char? = null

        for (char in password) {
            if (char.isDigit()) {
                if (prevDigit == char) {
                    currentCount++
                } else {
                    currentCount = 1
                    prevDigit = char
                }
            } else {
                currentCount = 0
                prevDigit = null
            }
            maxCount = maxOf(maxCount, currentCount)
        }

        return maxCount
    }
}
