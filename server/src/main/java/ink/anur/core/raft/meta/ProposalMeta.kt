package ink.anur.core.raft.meta

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.metastruct.SerializableMeta

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
class ProposalMeta(val rte: RequestTypeEnum,val meta: MetaStruct<*>) : SerializableMeta