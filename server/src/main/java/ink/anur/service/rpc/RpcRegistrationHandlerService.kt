package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.bean.NigateBean
import ink.anur.aop.OnClusterValid
import ink.anur.common.KanashinUlimitedExecutors
import ink.anur.core.raft.ElectionMetaService
import ink.anur.debug.Debugger
import ink.anur.inject.bean.Nigate
import ink.anur.inject.bean.NigateAfterBootStrap
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection.Companion.registerDestroyHandler
import ink.anur.io.common.transport.Connection.Companion.send
import ink.anur.io.common.transport.Connection.Companion.sendAndWaitingResponse
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.Ok
import ink.anur.pojo.rpc.RpcRegistrationLeave
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRegistrationReport
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 */
@ObsoleteCoroutinesApi
@NigateBean
open class RpcRegistrationHandlerService : AbstractRequestMapping() {

    @NigateInject
    lateinit var rpcRouteInfoSyncerService: RpcRouteInfoSyncerService

    @NigateInject
    lateinit var electionMetaService: ElectionMetaService

    private val logger = Debugger(this::class.java)

    @NigateAfterBootStrap
    fun doinit() {
        Nigate.injectOnly(this)
    }

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION
    }

    @OnClusterValid
    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {

        logger.info("Receive registration from $republicNode")

        val rpcRegistration = RpcRegistration(msg)
        val rpcRegistrationMeta = rpcRegistration.GetMeta()

        val leader = electionMetaService.getLeader() ?: return

        // first registry to client to notify deck
        // then if leader update route info directly
        // or else send to the leader and waiting for Ok
        // after send rpc route info to leader, leader's changes will notify current node
        // and current node should notify all client

        rpcRouteInfoSyncerService.NotifyMeWhenChanged(republicNode)

        if (leader.isLocal()) {
            rpcRouteInfoSyncerService.UpdateRouteInfo(rpcRegistrationMeta, republicNode)
        } else {

            logger.info("Report registration to leader $republicNode")
            runBlocking {
                launch(KanashinUlimitedExecutors.Dispatcher) {
                    leader.sendAndWaitingResponse(RpcRegistrationReport(rpcRegistrationMeta), Ok::class.java).await()
                }
            }
            republicNode.registerDestroyHandler {
                leader.send(RpcRegistrationLeave(republicNode))
            }
        }

        republicNode.send(Ok().asResp(rpcRegistration))
    }
}