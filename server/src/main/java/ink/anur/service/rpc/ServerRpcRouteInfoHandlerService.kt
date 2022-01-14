package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.debug.Debugger
import ink.anur.exception.RPCNonAvailableProviderException
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.inject.bean.NigatePostConstructPriority
import ink.anur.mutex.SwitchableReentrantReadWriteLocker
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.RpcRouteInfo
import ink.anur.pojo.rpc.RpcRequest
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
@ObsoleteCoroutinesApi
@NigateBean
@NigatePostConstructPriority(-1)
class ServerRpcRouteInfoHandlerService : AbstractRequestMapping() {

    private val logger = Debugger(this::class.java)

    @NigateInject
    private lateinit var rpcRouteInfoSyncerService: RpcRouteInfoSyncerService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_ROUTE_INFO
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        logger.info("Receive RpcRouteInfo from leader $republicNode")
        rpcRouteInfoSyncerService.UpdateRouteInfo(RpcRouteInfo(msg))
    }
}