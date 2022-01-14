package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.debug.Debugger
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection.Companion.send
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.Ok
import ink.anur.pojo.rpc.RpcRegistrationReport
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2022/1/14
 */
@ObsoleteCoroutinesApi
@NigateBean
class RpcRegistrationReportHandlerService : AbstractRequestMapping() {

    private val logger = Debugger(this::class.java)

    @NigateInject
    private lateinit var rpcRouteInfoSyncerService: RpcRouteInfoSyncerService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION_REPORT
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {

        val rpcRegistrationReport = RpcRegistrationReport(msg)
        val rpcRegistrationMeta = rpcRegistrationReport.GetMeta()

        logger.info("Receive registration report by $republicNode from ${rpcRegistrationMeta.provider}")

        rpcRouteInfoSyncerService.NotifyMeWhenChanged(republicNode)
        rpcRouteInfoSyncerService.UpdateRouteInfo(rpcRegistrationMeta, republicNode)

        republicNode.send(Ok().asResp(rpcRegistrationReport))
    }

}