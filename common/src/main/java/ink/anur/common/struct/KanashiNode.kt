package ink.anur.common.struct

import ink.anur.config.InetConfig
import ink.anur.inject.Nigate
import ink.anur.inject.NigateInject


class KanashiNode(val serverName: String, val host: String, val port: Int) {

    companion object {
        val NOT_EXIST = KanashiNode("", "", 0)
    }

    @NigateInject(useLocalFirst = true)
    private var inetClass: InetConfig? = null

    /**
     * 是否是本地节点
     */
    @Synchronized
    fun isLocalNode(): Boolean {
        if (inetClass == null) {
            Nigate.injectOnly(this)
        }

        return this.serverName == inetClass!!.getLocalServerName()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KanashiNode

        if (serverName != other.serverName) return false
        if (host != other.host) return false
        if (port != other.port) return false
        return true
    }

    override fun hashCode(): Int {
        var result = serverName.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + port
        return result
    }

    override fun toString(): String {
        return "KanashiNode(serverName='$serverName', host='$host', port='$port')"
    }
}