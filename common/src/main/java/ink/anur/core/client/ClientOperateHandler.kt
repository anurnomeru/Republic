package ink.anur.core.client

import ink.anur.common.KanashiRunnable
import ink.anur.common.Shutdownable
import ink.anur.common.struct.KanashiNode
import ink.anur.io.client.ReConnectableClient
import ink.anur.io.common.ShutDownHooker
import ink.anur.pojo.Register
import io.netty.channel.Channel

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 连接某客户端的 client
 */
class ClientOperateHandler(private val kanashiNode: KanashiNode,

                           /**
                            * 管道可用后触发此函数，注意可能被调用多次
                            */
                           doAfterChannelActive: ((Channel) -> Unit)? = null,

                           /**
                            * 当收到对方的注册回调后，触发此函数，注意 它可能会被多次调用
                            */
                           doAfterConnectToServer: (() -> Unit)? = null,

                           /**
                            * 当连接上对方后，如果断开了连接，做什么处理
                            *
                            * 返回 true 代表继续重连
                            * 返回 false 则不再重连
                            */
                           doAfterDisConnectToServer: (() -> Boolean)? = null)

    : KanashiRunnable() {

    private val serverShutDownHooker = ShutDownHooker("终止与协调节点 $kanashiNode 的连接")

    private val coordinateClient = ReConnectableClient(kanashiNode, this.serverShutDownHooker, doAfterChannelActive, doAfterConnectToServer, doAfterDisConnectToServer)

    fun getNodeName() = kanashiNode.serverName

    override fun run() {
        if (serverShutDownHooker.isShutDown()) {
            println("zzzzzzzz??????????????zzzzzzzzzzzzzzzzzzzzzzzz")
        } else {
            coordinateClient.start()
        }
    }

    fun shutDown(executeInactiveCallback: Boolean = true) {
        serverShutDownHooker.shutdown(executeInactiveCallback)
    }
}