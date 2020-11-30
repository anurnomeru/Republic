package ink.anur.pojo.rpc

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.rpc.core.MetaStruct
import ink.anur.pojo.rpc.core.SerializableMeta
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import java.nio.ByteBuffer

class RpcRegistration : MetaStruct {
    constructor(serializableMeta: SerializableMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REQUEST
    }

    override fun metaClazz(): Class<out SerializableMeta> {
        return RpcRegistrationMeta::class.java
    }
}