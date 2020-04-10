package ink.anur.service

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcResponse
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
@NigateBean
class RpcResponseHandlerService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var rpcSenderService: RpcSenderService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_RESPONSE
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        rpcSenderService.notifyRpcResponse(RpcResponse(msg).responseMeta)
    }
}