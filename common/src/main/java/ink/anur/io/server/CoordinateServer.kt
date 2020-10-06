package ink.anur.io.server

import ink.anur.io.common.handler.AutoUnRegistryHandler
import ink.anur.io.common.handler.ErrorHandler
import ink.anur.io.common.handler.KanashiDecoder
import ink.anur.io.common.transport.ShutDownHooker
import io.netty.channel.ChannelPipeline

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 通用的 Server，提供了 ByteBuffer 的消费入口
 * 以及 pipeline 的定制入口
 */
class CoordinateServer(host: String, port: Int,
                       shutDownHooker: ShutDownHooker)
    : Server(host, port, shutDownHooker) {
    override fun channelPipelineConsumer(channelPipeline: ChannelPipeline): ChannelPipeline {
        channelPipeline
                .addFirst(AutoUnRegistryHandler())
                .addLast(KanashiDecoder())
                .addLast(ErrorHandler())
        return channelPipeline
    }
}