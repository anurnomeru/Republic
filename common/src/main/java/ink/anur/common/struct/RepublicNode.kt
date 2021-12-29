package ink.anur.common.struct

import ink.anur.config.InetConfiguration
import ink.anur.inject.bean.Nigate
import java.net.InetSocketAddress
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Anur on 2020/9/30
 */
class RepublicNode private constructor(val host: String, val port: Int) : Comparator<RepublicNode?> {

    val addr = "$host:$port"

    companion object {

        private val unique = ConcurrentHashMap<String /* addr */, RepublicNode>()

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

    fun isLocal(): Boolean = Nigate.getBeanByClass(InetConfiguration::class.java).localNode == this

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

    override fun compare(o1: RepublicNode?, o2: RepublicNode?): Int {
        if (o1?.equals(o2) == true) {
            return 0
        }
        if (o2 == null) {
            return 1
        }
        return o1?.hashCode()?.compareTo(o2.hashCode()) ?: 0
    }
}