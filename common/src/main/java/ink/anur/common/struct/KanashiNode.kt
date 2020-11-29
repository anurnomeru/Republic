//package ink.anur.common.struct
//
//import ink.anur.config.InetConfiguration
//import ink.anur.inject.bean.Nigate
//
//class KanashiNode(val serverName: String, val host: String, val port: Int) {
//
//    companion object {
//        val NOT_EXIST = KanashiNode("", "", 0)
//    }
//
//    private var inetConfiguration: InetConfiguration? = null
//
//    /**
//     * 是否是本地节点
//     */
//    @Synchronized
//    fun isLocalNode(): Boolean {
//        if (inetConfiguration == null) {
//            Nigate.injectOnly(this)
//        }
//
//        return true
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as KanashiNode
//
//        if (serverName != other.serverName) return false
//        if (host != other.host) return false
//        if (port != other.port) return false
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = serverName.hashCode()
//        result = 31 * result + host.hashCode()
//        result = 31 * result + port
//        return result
//    }
//
//    override fun toString(): String {
//        return "KanashiNode(serverName='$serverName', host='$host', port='$port')"
//    }
//}