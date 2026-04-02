package org.katacr.kalogin

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CompletableFuture

class DatabaseManager(private val plugin: KaLogin) {

    private var connection: Connection? = null

    fun init() {
        val config = plugin.config
        val type = config.getString("database.type", "sqlite")?.lowercase()

        try {
            if (type == "mysql") {
                val host = config.getString("database.mysql.host")
                val port = config.getInt("database.mysql.port")
                val db = config.getString("database.mysql.database")
                val user = config.getString("database.mysql.username")
                val pass = config.getString("database.mysql.password")
                val params = config.getString("database.mysql.params", "")

                val url = "jdbc:mysql://$host:$port/$db$params"
                connection = DriverManager.getConnection(url, user, pass)
                plugin.logger.info("已成功连接到 MySQL 数据库")
            } else {
                // 默认 SQLite
                val fileName = config.getString("database.sqlite.file_name", "data.db")
                val dbFile = File(plugin.dataFolder, fileName!!)
                if (!dbFile.exists()) {
                    plugin.dataFolder.mkdirs()
                    dbFile.createNewFile()
                }
                Class.forName("org.sqlite.JDBC")
                connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
                plugin.logger.info("已成功连接到 SQLite 数据库")
            }

            // 创建用户表
            createTable()
        } catch (e: Exception) {
            plugin.logger.severe("数据库初始化失败: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS kalogin_users (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16),
                password TEXT,
                ip VARCHAR(45),
                last_login_ip VARCHAR(45),
                auto_login_by_ip BOOLEAN DEFAULT FALSE,
                reg_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        connection?.createStatement()?.use { it.execute(sql) }

        // 检查并添加 last_login_ip 字段（用于数据库升级）
        addColumnIfNotExists("last_login_ip", "VARCHAR(45)")
        // 检查并添加 auto_login_by_ip 字段（用于数据库升级）
        addColumnIfNotExists("auto_login_by_ip", "BOOLEAN DEFAULT FALSE")
    }

    /**
     * 如果字段不存在则添加（用于数据库升级）
     */
    private fun addColumnIfNotExists(columnName: String, columnType: String) {
        try {
            val tableName = "kalogin_users"
            val sql = "SELECT COUNT(*) FROM pragma_table_info('$tableName') WHERE name = '$columnName'"

            // SQLite 查询
            val columnExists = connection?.createStatement()?.executeQuery(sql)?.use { rs ->
                rs.next() && rs.getInt(1) > 0
            } ?: false

            if (!columnExists) {
                val alterSql = "ALTER TABLE kalogin_users ADD COLUMN $columnName $columnType"
                connection?.createStatement()?.execute(alterSql)
                plugin.logger.info("已添加数据库字段: $columnName")
            }
        } catch (e: SQLException) {
            // MySQL 或其他数据库，直接尝试添加字段
            try {
                val alterSql = "ALTER TABLE kalogin_users ADD COLUMN last_login_ip VARCHAR(45)"
                connection?.createStatement()?.execute(alterSql)
            } catch (e2: SQLException) {
                // 字段已存在，忽略错误
            }
        }
    }

    fun getConnection(): Connection? {
        try {
            if (connection == null || connection!!.isClosed) {
                init() // 断线重连
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return connection
    }
    // 在 DatabaseManager.kt 中添加
    fun registerPlayer(uuid: UUID, username: String, password: String, ip: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val hashedPassword = PasswordHasher.hash(password)
            val sql = "INSERT INTO kalogin_users (uuid, username, password, ip, last_login_ip, auto_login_by_ip) VALUES (?, ?, ?, ?, ?, FALSE)"

            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.setString(2, username)
                    ps.setString(3, hashedPassword)
                    ps.setString(4, ip)
                    ps.setString(5, ip) // 初始时 last_login_ip 与注册 IP 相同
                    ps.executeUpdate()
                    true
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 检查玩家是否已注册
     */
    fun isPlayerRegistered(uuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "SELECT COUNT(*) FROM kalogin_users WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.executeQuery()?.use { rs ->
                        rs.next()
                        rs.getInt(1) > 0
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 为 AuthMe 模式初始化玩家记录
     * 只存储 IP 相关信息，不存储密码
     */
    fun initPlayerForAuthMe(uuid: UUID, username: String, ip: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "INSERT INTO kalogin_users (uuid, username, last_login_ip, auto_login_by_ip) VALUES (?, ?, ?, FALSE)"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.setString(2, username)
                    ps.setString(3, ip)
                    ps.executeUpdate()
                    true
                }
            } catch (e: SQLException) {
                // 可能是唯一键冲突，玩家已存在，尝试更新
                try {
                    val updateSql = "UPDATE kalogin_users SET username = ?, last_login_ip = ? WHERE uuid = ?"
                    getConnection()?.prepareStatement(updateSql)?.use { ps ->
                        ps.setString(1, username)
                        ps.setString(2, ip)
                        ps.setString(3, uuid.toString())
                        ps.executeUpdate() > 0
                    }
                } catch (e2: SQLException) {
                    e.printStackTrace()
                    false
                }
            }
        })
    }

    /**
     * 检查玩家是否启用同IP自动登录且IP与上次相同
     */
    fun canAutoLogin(uuid: UUID, ip: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "SELECT last_login_ip, auto_login_by_ip FROM kalogin_users WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.executeQuery()?.use { rs ->
                        if (rs.next()) {
                            val lastIp = rs.getString("last_login_ip")
                            val autoLoginByIp = rs.getBoolean("auto_login_by_ip")
                            autoLoginByIp && (lastIp != null && lastIp == ip)
                        } else {
                            false
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 检查玩家 IP 是否与上次登录相同
     */
    fun isSameLastIp(uuid: UUID, ip: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "SELECT last_login_ip FROM kalogin_users WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.executeQuery()?.use { rs ->
                        if (rs.next()) {
                            val lastIp = rs.getString("last_login_ip")
                            lastIp != null && lastIp == ip
                        } else {
                            false
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 更新玩家的自动登录设置
     */
    fun updateAutoLoginByIp(uuid: UUID, enabled: Boolean): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "UPDATE kalogin_users SET auto_login_by_ip = ? WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setBoolean(1, enabled)
                    ps.setString(2, uuid.toString())
                    ps.executeUpdate()
                    true
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 更新玩家最后登录 IP
     */
    fun updateLastLoginIp(uuid: UUID, ip: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "UPDATE kalogin_users SET last_login_ip = ? WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, ip)
                    ps.setString(2, uuid.toString())
                    ps.executeUpdate()
                    true
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 验证玩家密码
     */
    fun verifyPassword(uuid: UUID, password: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "SELECT password FROM kalogin_users WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.executeQuery()?.use { rs ->
                        if (rs.next()) {
                            val hashedPassword = rs.getString("password")
                            PasswordHasher.check(password, hashedPassword)
                        } else {
                            false
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 获取指定 IP 的注册账号数量
     */
    fun countAccountsByIp(ip: String): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync({
            val sql = "SELECT COUNT(*) FROM kalogin_users WHERE ip = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, ip)
                    ps.executeQuery()?.use { rs ->
                        if (rs.next()) {
                            rs.getInt(1)
                        } else {
                            0
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                0
            }
        })
    }

    /**
     * 删除玩家数据
     */
    fun deletePlayer(uuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val sql = "DELETE FROM kalogin_users WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.executeUpdate() > 0
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    /**
     * 重新设置玩家密码
     */
    fun setPassword(uuid: UUID, password: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val hashedPassword = PasswordHasher.hash(password)
            val sql = "UPDATE kalogin_users SET password = ? WHERE uuid = ?"
            try {
                getConnection()?.prepareStatement(sql)?.use { ps ->
                    ps.setString(1, hashedPassword)
                    ps.setString(2, uuid.toString())
                    ps.executeUpdate() > 0
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            }
        })
    }

    fun close() {
        connection?.close()
    }
}