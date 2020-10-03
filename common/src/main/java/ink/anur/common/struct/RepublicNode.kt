package ink.anur.common.struct

import java.net.InetSocketAddress

/**
 * Created by Anur on 2020/9/30
 */
class RepublicNode {

    val host: String
    val port: Int

    constructor(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    constructor(inetSocketAddress: InetSocketAddress) {
        this.host = inetSocketAddress.hostName
        this.port = inetSocketAddress.port
    }

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

}