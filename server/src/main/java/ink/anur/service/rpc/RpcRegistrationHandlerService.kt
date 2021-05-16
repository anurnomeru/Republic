package ink.anur.service.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.raft.AppendEntries
import ink.anur.core.raft.Proposal
import ink.anur.core.raft.meta.AppendEntriesMeta
import ink.anur.core.raft.meta.ProposalMeta
import ink.anur.core.server.RpcRegistrationCenterService
import ink.anur.exception.codeabel_exception.CodeableException
import ink.anur.exception.codeabel_exception.UnkownCodeableException
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection.Companion.getConnection
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.meta.RpcInetSocketAddress
import ink.anur.pojo.rpc.RpcRegistration
import ink.anur.pojo.rpc.RpcRegistrationResponse
import ink.anur.pojo.rpc.meta.EmptyMeta
import ink.anur.service.coordinate.ProposalService
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * provider 向 注册中心注册自己的服务
 */
@NigateBean
class RpcRegistrationHandlerService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var proposalService: ProposalService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        val rpcRegistration = RpcRegistration(msg)

        // new proposal to persist
        val proposal = Proposal(ProposalMeta(RequestTypeEnum.RPC_REGISTRATION, rpcRegistration))
        try {
            proposalService.DraftProposal(proposal)
        } catch (e: Exception) {
            republicNode.sendAsync(RpcRegistrationResponse(e).asResp(rpcRegistration))
        }
        republicNode.sendAsync(RpcRegistrationResponse().asResp(rpcRegistration))
    }
}