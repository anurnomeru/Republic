package ink.anur.service.coordinate

import ink.anur.common.struct.RepublicNode
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.raft.RaftCenterController
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.pojo.coordinate.Voting
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/27
 *
 * 专门处理拉票的处理器
 */
@NigateBean
class VotingHandleService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var raftCenterController: RaftCenterController

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.VOTING
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        val voting = Voting(msg)
        raftCenterController.receiveVote(republicNode, voting)
    }
}