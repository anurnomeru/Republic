package ink.anur.pojo.rpc

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.metastruct.SerializableMeta
import ink.anur.pojo.rpc.meta.RpcRouteInfoMeta
import java.nio.ByteBuffer

class RpcRouteInfo : MetaStruct<RpcRouteInfoMeta> {
    constructor(serializableMeta: RpcRouteInfoMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_ROUTE_INFO
    }

    override fun metaClazz(): Class<RpcRouteInfoMeta> {
        return RpcRouteInfoMeta::class.java
    }
}