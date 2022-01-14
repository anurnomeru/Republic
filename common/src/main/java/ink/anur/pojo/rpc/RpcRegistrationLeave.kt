package ink.anur.pojo.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.rpc.meta.RpcRegistrationMeta
import java.nio.ByteBuffer

class RpcRegistrationLeave : MetaStruct<RepublicNode> {
    constructor(serializableMeta: RepublicNode) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REGISTRATION_LEAVE
    }

    override fun metaClazz(): Class<RepublicNode> {
        return RepublicNode::class.java
    }
}