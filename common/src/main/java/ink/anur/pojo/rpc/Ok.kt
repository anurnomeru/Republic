package ink.anur.pojo.rpc

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.rpc.meta.EmptyMeta
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2022/1/14
 */
class Ok : MetaStruct<EmptyMeta> {
    constructor() : super(EmptyMeta())
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)
    constructor(exception: Exception) : super(exception)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.OK
    }

    override fun metaClazz(): Class<EmptyMeta> {
        return EmptyMeta::class.java
    }
}