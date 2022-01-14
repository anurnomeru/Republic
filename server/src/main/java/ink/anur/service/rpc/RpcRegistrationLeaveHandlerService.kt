package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.debug.Debugger
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRegistrationLeave
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2022/1/14
 */
@ObsoleteCoroutinesApi
@NigateBean
class RpcRegistrationLeaveHandlerService : AbstractRequestMapping() {

    private val logger = Debugger(this::class.java)

    @NigateInject
    private lateinit var rpcRouteInfoSyncerService: RpcRouteInfoSyncerService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION_LEAVE
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        val leaving = RpcRegistrationLeave(msg).GetMeta()
        logger.info("Receive leave report by $leaving from $republicNode")

        rpcRouteInfoSyncerService.RemoveFromRouteInfo(leaving)
    }
}