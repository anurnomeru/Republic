package ink.anur.pojo.rpc

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.rpc.meta.RpcProviderMappingMeta
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 */
class RpcProviderMapping : MetaStruct<RpcProviderMappingMeta> {

    constructor(serializableMeta: RpcProviderMappingMeta) : super(serializableMeta)
    constructor(byteBuffer: ByteBuffer) : super(byteBuffer)

    override fun requestTypeEnum(): RequestTypeEnum {
        return RequestTypeEnum.RPC_REQUEST
    }

    override fun metaClazz(): Class<RpcProviderMappingMeta> {
        return RpcProviderMappingMeta::class.java
    }
}