package ink.anur.pojo.rpc

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.rpc.meta.RpcResponseMeta
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
class RpcResponse : MetaStruct<RpcResponseMeta> {
    constructor(serializableMeta: RpcResponseMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_RESPONSE
    }

    override fun metaClazz(): Class<RpcResponseMeta> {
        return RpcResponseMeta::class.java
    }
}