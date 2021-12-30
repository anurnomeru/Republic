package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.bean.NigateBean
import ink.anur.aop.OnClusterValid
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRegistrationResponse
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 */
@NigateBean
open class RpcRegistrationHandlerService : AbstractRequestMapping() {

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION
    }

    @OnClusterValid
    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        val rpcRegistration = RpcRegistration(msg)

        republicNode.sendAsync(RpcRegistrationResponse().asResp(rpcRegistration))
    }
}