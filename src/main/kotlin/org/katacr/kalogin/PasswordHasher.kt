package org.katacr.kalogin

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {

    /**
     * 加密明文密码
     */
    fun hash(password: String): String {
        // gensalt(12) 这里的 12 是强度，数值越高越安全但越耗时（推荐 10-12）
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    /**
     * 验证明文密码与数据库密文是否匹配
     */
    fun check(password: String, hashed: String): Boolean {
        return try {
            BCrypt.checkpw(password, hashed)
        } catch (e: Exception) {
            false
        }
    }
}