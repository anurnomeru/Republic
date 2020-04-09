package ink.anur.rpc.handler

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.NigateBean
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRegistration
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 */
@NigateBean
class RpcRegistrationHandlerService: AbstractRequestMapping() {

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val rpcRegistration = RpcRegistration(msg)
        val rpcRegistrationMeta = rpcRegistration.rpcRegistrationMeta

    }
}