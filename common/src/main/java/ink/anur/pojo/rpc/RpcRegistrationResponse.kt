package ink.anur.pojo.rpc

import ink.anur.exception.codeabel_exception.CodeableException
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.rpc.meta.EmptyMeta
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import java.nio.ByteBuffer

class RpcRegistrationResponse : MetaStruct<EmptyMeta> {
    constructor() : super(EmptyMeta())
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)
    constructor(exception: Exception) : super(exception)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION_RESPONSE
    }

    override fun metaClazz(): Class<EmptyMeta> {
        return EmptyMeta::class.java
    }
}