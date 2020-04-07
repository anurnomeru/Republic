package ink.anur.core.client

import ink.anur.common.KanashiRunnable
import ink.anur.common.Shutdownable
import ink.anur.common.struct.KanashiNode
import ink.anur.io.client.ReConnectableClient
import ink.anur.io.common.ShutDownHooker
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext

/**
 * Created by Anur IjuoKaruKas on 2020/2/23
 *
 * 连接某客户端的 client
 */
class ClientOperateHandler(kanashiNode: KanashiNode,

                           /**
                            * 当受到对方的注册回调后，触发此函数，注意 它可能会被多次调用
                            */
                           doAfterConnectToServer: (() -> Unit)? = null,

                           /**
                            * 当连接上对方后，如果断开了连接，做什么处理
                            *
                            * 返回 true 代表继续重连
                            * 返回 false 则不再重连
                            */
                           doAfterDisConnectToServer: (() -> Boolean)? = null)

    : KanashiRunnable(), Shutdownable {

    private val serverShutDownHooker = ShutDownHooker("终止与协调节点 $kanashiNode 的连接")

    private var ctx: Channel? = null

    private val coordinateClient = ReConnectableClient(kanashiNode, this.serverShutDownHooker, { synchronized(this) { ctx = it } }, doAfterConnectToServer, doAfterDisConnectToServer)

    private fun getChannel(): Channel {
        synchronized(this) {
            return ctx!!
        }
    }

    override fun run() {
        if (serverShutDownHooker.isShutDown()) {
            println("zzzzzzzz??????????????zzzzzzzzzzzzzzzzzzzzzzzz")
        } else {
            coordinateClient.start()
        }
    }

    override fun shutDown() {
        serverShutDownHooker.shutdown()
    }
}