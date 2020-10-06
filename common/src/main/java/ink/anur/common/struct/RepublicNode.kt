package ink.anur.common.struct

import ink.anur.config.InetConfiguration
import ink.anur.inject.bean.Nigate
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Anur on 2020/9/30
 */
class RepublicNode {

    val host: String
    val port: Int

    companion object {

        val unique = ConcurrentHashMap<String /* addr */, RepublicNode>()

        fun construct(addr: String): RepublicNode {
            val split: Array<String> = addr.split(":").toTypedArray()
            return construct(split[0], split[1].toInt())
        }

        fun construct(inetSocketAddress: InetSocketAddress): RepublicNode {
            return construct(inetSocketAddress.hostName, inetSocketAddress.port)
        }

        fun construct(host: String, port: Int): RepublicNode {
            val addr = "$host:$port"
            return unique.computeIfAbsent(addr) {
                RepublicNode(host, port)
            }
        }
    }

    private constructor(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    fun isLocal(): Boolean = Nigate.getBeanByClass(InetConfiguration::class.java).localServer == this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepublicNode

        if (host != other.host) return false
        if (port != other.port) return false

        return true
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + port
        return result
    }

    override fun toString(): String {
        return "RepublicNode(host='$host', port=$port)"
    }
}