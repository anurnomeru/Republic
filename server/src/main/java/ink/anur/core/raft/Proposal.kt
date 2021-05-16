package ink.anur.core.raft

import ink.anur.core.raft.meta.AppendEntriesMeta
import ink.anur.core.raft.meta.ProposalMeta
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
class Proposal: MetaStruct<ProposalMeta> {
    constructor(serializableMeta: ProposalMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.PROPOSAL
    }

    override fun metaClazz(): Class<ProposalMeta> {
        return ProposalMeta::class.java
    }
}