package ink.anur.config

import ink.anur.common.struct.KanashiNode

/**
 * Created by Anur IjuoKaruKas on 2020/3/6
 *
 * client 和 server 都需要实现这个配置，供给获取网络配置
 */
interface InetConfig {

    /**
     * 只有 client 需要用
     */
    fun setClusters(clusters: List<KanashiNode>)

    /**
     * 获取本地服务名
     */
    fun getLocalServerName(): String

    /**
     * 获取本地端口
     */
    fun getLocalPort(): Int

    /**
     * 根据名字获取节点
     */
    fun getNode(serverName: String): List<KanashiNode>

    /**
     * 获取集群信息
     */
    fun getCluster(): List<KanashiNode>
}