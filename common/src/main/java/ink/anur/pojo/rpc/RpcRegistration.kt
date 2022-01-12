package ink.anur.pojo.rpc

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.metastruct.SerializableMeta
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import java.nio.ByteBuffer

class RpcRegistration : MetaStruct<RpcRegistrationMeta> {
    constructor(serializableMeta: RpcRegistrationMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION
    }

    override fun metaClazz(): Class<RpcRegistrationMeta> {
        return RpcRegistrationMeta::class.java
    }
}