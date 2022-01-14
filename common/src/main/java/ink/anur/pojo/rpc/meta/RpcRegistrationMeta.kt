package ink.anur.pojo.rpc.meta

import ink.anur.common.struct.RepublicNode
import ink.anur.pojo.metastruct.SerializableMeta

class RpcRegistrationMeta(
    val provider: RepublicNode,
    val RPC_BEAN: Map<String/* bean */, HashSet<String /* method */>> = mapOf(),
    // ?
    val RPC_INTERFACE_BEAN: Map<String/* bean */, List<HashSet<String /* method */>>> = mapOf()
) : SerializableMeta