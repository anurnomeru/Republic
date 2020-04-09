package ink.anur.io

import ink.anur.io.common.ShutDownHooker
import ink.anur.io.common.handler.AutoUnRegistryHandler
import ink.anur.io.common.handler.ErrorHandler
import ink.anur.io.common.handler.EventDriverPoolHandler
import ink.anur.io.common.handler.KanashiDecoder
import ink.anur.io.server.Server
import io.netty.channel.ChannelPipeline
import java.util.concurrent.CountDownLatch

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * 对于 RPC client 来说，可以随便搞一个端口监听
 */
class RpcServer(
    shutDownHooker: ShutDownHooker,
    startLatch: CountDownLatch?)
    : Server(null, shutDownHooker, startLatch) {
    override fun channelPipelineConsumer(channelPipeline: ChannelPipeline): ChannelPipeline {
        channelPipeline
            .addFirst(AutoUnRegistryHandler())
            .addLast(KanashiDecoder())
            .addLast(EventDriverPoolHandler())
            .addLast(ErrorHandler())
        return channelPipeline
    }
}