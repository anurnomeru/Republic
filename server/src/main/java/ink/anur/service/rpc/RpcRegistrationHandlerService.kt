package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.bean.NigateBean
import ink.anur.aop.OnClusterValid
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateAfterBootStrap
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection.Companion.registerDestroyHandler
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRegistrationResponse
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 */
@ObsoleteCoroutinesApi
@NigateBean
open class RpcRegistrationHandlerService : AbstractRequestMapping() {

    @NigateInject
    lateinit var rpcRouteInfoSyncerService: RpcRouteInfoSyncerService

    @NigateAfterBootStrap
    fun doinit() {
        Nigate.injectOnly(this)
    }

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION
    }

    @OnClusterValid
    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        val rpcRegistration = RpcRegistration(msg)
        val rpcRegistrationMeta = rpcRegistration.GetMeta()
        republicNode.sendAsync(RpcRegistrationResponse().asResp(rpcRegistration))

        // first registry to rpc route info
        // then update the route info and notify all node

        rpcRouteInfoSyncerService.NotifyMe(republicNode)
        republicNode.registerDestroyHandler {
            rpcRouteInfoSyncerService.OutOfDeck(republicNode)
            rpcRouteInfoSyncerService.RemoveFromRouteInfo(republicNode)
        }

        rpcRouteInfoSyncerService.UpdateRouteInfo(republicNode, rpcRegistrationMeta)

    }
}