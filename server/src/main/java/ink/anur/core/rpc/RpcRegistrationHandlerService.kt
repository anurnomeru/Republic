package ink.anur.core.rpc

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.rpc.RpcRegistrationCenterService
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.nio.ByteBuffer
import java.net.InetSocketAddress

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * provider 向 注册中心注册自己的服务
 */
@NigateBean
class RpcRegistrationHandlerService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var rpcRegistrationCenterService: RpcRegistrationCenterService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val rpcRegistration = RpcRegistration(msg)
        val rpcRegistrationMeta = rpcRegistration.rpcRegistrationMeta

        val isa = channel.remoteAddress() as InetSocketAddress
        val name = rpcRegistrationCenterService.register(isa, rpcRegistrationMeta)
        channel.pipeline().addLast(UnRegisterHandler(name, rpcRegistrationCenterService))

        // 进行多一次检查，避免在 addLast 后，服务就不可用了
        if (!channel.isActive) {
            rpcRegistrationCenterService.unRegister(name)
        }
    }

    class UnRegisterHandler(private val serverName: String, private val rpcRegistrationCenterService: RpcRegistrationCenterService) : ChannelInboundHandlerAdapter() {
        override fun channelInactive(ctx: ChannelHandlerContext?) {
            super.channelInactive(ctx)
            rpcRegistrationCenterService.unRegister(serverName)
        }
    }
}