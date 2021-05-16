package ink.anur.service.coordinate

import ink.anur.common.struct.RepublicNode
import ink.anur.config.ElectConfiguration
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.raft.*
import ink.anur.core.raft.meta.AppendEntriesMeta
import ink.anur.core.raft.meta.ProposalMeta
import ink.anur.exception.codeabel_exception.ClusterInvalidException
import ink.anur.exception.codeabel_exception.NotLeaderException
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection.Companion.sendAndWaitingResponse
import ink.anur.io.common.transport.Connection.Companion.sendAsync
import ink.anur.pojo.common.RequestTypeEnum
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
@NigateBean
class ProposalService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var clusterStateController: ClusterStateController

    @NigateInject
    private lateinit var electionMetaService: ElectionMetaService

    @NigateInject
    private lateinit var raftCenterController: RaftCenterController

    @Throws
    fun DraftProposal(proposal: Proposal) {
        clusterStateController.AcquireCompel()

        if (electionMetaService.isLeader()) {
            // TODO
        } else {
            val resp =
                (electionMetaService.getLeader()?.sendAndWaitingResponse(proposal, ProposalResponse::class.java)
                    ?: throw ClusterInvalidException())

            runBlocking {
                // may exception
                resp.await().Resp().GetMeta()
            }
            return
        }
    }

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.PROPOSAL
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        val proposal = Proposal(msg)

        try {
            clusterStateController.AcquireCompel()

            if (!electionMetaService.isLeader()) {
                throw NotLeaderException()
            }

            val proposalMeta = proposal.GetMeta()
            val appendEntries =
                AppendEntries(
                    AppendEntriesMeta(
                        raftCenterController.GenGenerationAndOffset(),
                        proposalMeta.rte,
                        proposalMeta.meta
                    )
                )

            // TODO

        } catch (e: Exception) {
            republicNode.sendAsync(ProposalResponse(e).asResp(proposal))
        }
    }
}