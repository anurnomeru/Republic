package ink.anur.config

import ink.anur.common.Constant
import ink.anur.common.struct.KanashiNode
import ink.anur.config.common.ConfigHelper
import ink.anur.config.common.ConfigurationEnum
import ink.anur.inject.NigateBean

/**
 * Created by Anur IjuoKaruKas on 2019/7/5
 *
 * 网络相关配置，都可以从这里获取
 */
@NigateBean
class ClientInetSocketAddressConfiguration : ConfigHelper(), InetConfig {

    override fun setClusters(clusters: List<KanashiNode>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLocalServerName(): String {
        return Constant.CLIENT
    }

    override fun getLocalPort(): Int {
        return 0
    }

    override fun getNode(serverName: String?): KanashiNode {
        return getCluster().associateBy { kanashiLegal: KanashiNode -> kanashiLegal.serverName }[serverName] ?: KanashiNode.NOT_EXIST
    }


    override fun getCluster(): List<KanashiNode> {
        return getConfigSimilar(ConfigurationEnum.CLIENT_ADDR) { pair ->
            val serverName = pair.key
            val split = pair.value
                .split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            KanashiNode(serverName, split[0], Integer.valueOf(split[1]))
        } as List<KanashiNode>
    }
}
