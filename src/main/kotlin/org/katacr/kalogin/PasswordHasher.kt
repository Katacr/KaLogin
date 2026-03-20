package org.katacr.kalogin

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {

    /**
     * 加密明文密码
     */
    fun hash(password: String): String {
        // BCrypt 加密强度固定为 5，修改后会导致旧密码无法验证
        return BCrypt.hashpw(password, BCrypt.gensalt(5))
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