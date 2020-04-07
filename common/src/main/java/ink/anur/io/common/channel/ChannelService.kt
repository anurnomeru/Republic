package ink.anur.io.common.channel

import ink.anur.common.struct.KanashiNode
import ink.anur.inject.NigateBean
import ink.anur.mutex.ReentrantReadWriteLocker
import io.netty.channel.Channel

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 管理所有连接
 */
@NigateBean
class ChannelService : ReentrantReadWriteLocker() {

    companion object {
        val COORDINATE_LEADE_SIGN = "LEADER"
    }

    private val kanashiNodeMap: MutableMap<String/* serverName */, KanashiNode> = mutableMapOf()

    /**
     * 记录了服务名和 channel 的映射
     */
    private val serverChannelMap: MutableMap<String/* serverName */, Channel> = mutableMapOf()

    /**
     * 记录了 channel 和服务名的映射
     */
    private val channelServerMap: MutableMap<Channel, String/* serverName */> = mutableMapOf()

    /**
     * 如果还没连接上服务，是会返回空的
     */
    fun getChannel(serverName: String): Channel? = this.readLockSupplier {
        serverChannelMap[serverName]
    }

    /**
     * 如果还没连接上服务，是会返回空的
     */
    fun getChannelName(channel: Channel): String? = this.readLockSupplier {
        channelServerMap[channel]
    }

    /**
     * 向 channelManager 注册服务
     */
    fun register(kanashiNode: KanashiNode, channel: Channel) {
        this.writeLockSupplier {
            kanashiNodeMap[kanashiNode.serverName] = kanashiNode
            serverChannelMap[kanashiNode.serverName] = channel
            channelServerMap[channel] = kanashiNode.serverName
        }
    }

    /**
     * 根据服务名来注销服务
     */
    fun unRegister(serverName: String): KanashiNode? {
        return this.writeLockSupplier {
            val channel = serverChannelMap.remove(serverName)
            channelServerMap.remove(channel)
            return@writeLockSupplier kanashiNodeMap.remove(serverName)
        }
    }

    /**
     * 根据管道来注销服务
     */
    fun unRegister(channel: Channel): KanashiNode? {
        return this.writeLockSupplier {
            val serverName = channelServerMap.remove(channel)
            serverChannelMap.remove(serverName)
            return@writeLockSupplier kanashiNodeMap.remove(serverName)
        }
    }
}