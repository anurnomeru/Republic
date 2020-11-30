package ink.anur.pojo.rpc.meta

import ink.anur.pojo.rpc.core.SerializableMeta

class RpcRegistrationMeta(
        val localNodeAddr: String,
        val RPC_BEAN: Map<String/* bean */, HashSet<String /* method */>>,
        val RPC_INTERFACE_BEAN: Map<String/* bean */, List<HashSet<String /* method */>>>
) : SerializableMeta