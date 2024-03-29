package ink.anur.pojo.rpc

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.rpc.meta.RpcRequestMeta
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
class RpcRequest : MetaStruct<RpcRequestMeta> {
    constructor(serializableMeta: RpcRequestMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REQUEST
    }

    override fun metaClazz(): Class<RpcRequestMeta> {
        return RpcRequestMeta::class.java
    }
}