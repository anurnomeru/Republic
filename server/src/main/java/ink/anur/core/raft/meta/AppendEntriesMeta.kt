package ink.anur.core.raft.meta

import ink.anur.core.raft.GenerationAndOffset
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.metastruct.SerializableMeta

/**
 * Created by Anur IjuoKaruKas on 2021/5/11
 */
class AppendEntriesMeta(gao : GenerationAndOffset, rte: RequestTypeEnum, meta: MetaStruct<*>) : SerializableMeta {
}