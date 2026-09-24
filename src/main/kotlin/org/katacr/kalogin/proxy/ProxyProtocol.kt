package org.katacr.kalogin.proxy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * KaProxy 统一二进制协议的后端实现。
 *
 * 信封结构：int MAGIC + short VERSION + UTF module + UTF action + 业务负载。
 * 与 KaProxy / KaBroadcast 的 KaProxyProtocol 保持字段顺序一致。
 */
object ProxyProtocol {
    const val CHANNEL = "kaproxy:main"
    private const val MAGIC = 0x4B415058
    private const val VERSION = 1
    private const val MAX_PACKET_BYTES = 1_048_576

    /** 编码一个带模块和动作的数据包。 */
    @Throws(IOException::class)
    fun encode(module: String, action: String, writer: PacketWriter): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeShort(VERSION)
            output.writeUTF(module)
            output.writeUTF(action)
            writer.write(output)
        }
        return bytes.toByteArray()
    }

    /** 校验信封并返回定位到业务负载的数据包。 */
    @Throws(IOException::class)
    fun decode(data: ByteArray): Packet {
        if (data.size > MAX_PACKET_BYTES) {
            throw IOException("KaProxy 数据包超过大小限制")
        }
        val input = DataInputStream(ByteArrayInputStream(data))
        if (input.readInt() != MAGIC) {
            throw IOException("无效的 KaProxy 数据包标识")
        }
        if (input.readShort().toInt() != VERSION) {
            throw IOException("不支持的 KaProxy 协议版本")
        }
        return Packet(input.readUTF(), input.readUTF(), input)
    }

    /** 写入 UUID 的两个 long 部分。 */
    fun writeUuid(output: DataOutputStream, value: UUID) {
        output.writeLong(value.mostSignificantBits)
        output.writeLong(value.leastSignificantBits)
    }

    /** 读取 UUID 的两个 long 部分。 */
    fun readUuid(input: DataInputStream): UUID = UUID(input.readLong(), input.readLong())

    /** 已解码的数据包路由信息与剩余负载。 */
    data class Packet(val module: String, val action: String, val input: DataInputStream)

    /** 业务负载写入器。 */
    fun interface PacketWriter {
        @Throws(IOException::class)
        fun write(output: DataOutputStream)
    }
}
