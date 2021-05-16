package ink.anur.core.raft

import ink.anur.core.raft.meta.AppendEntriesMeta
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
class AppendEntries : MetaStruct<AppendEntriesMeta> {

    constructor(serializableMeta: AppendEntriesMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.APPEND_ENTRIES
    }

    override fun metaClazz(): Class<AppendEntriesMeta> {
        return AppendEntriesMeta::class.java
    }
}