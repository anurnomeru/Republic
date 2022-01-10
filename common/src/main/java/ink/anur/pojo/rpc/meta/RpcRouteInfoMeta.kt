package ink.anur.pojo.rpc.meta

import ink.anur.pojo.metastruct.SerializableMeta

class RpcRouteInfoMeta(
    val providerMapping: MutableMap<String/* bean */,
            MutableMap<String /* methodSign */, MutableSet<String/* serverName */>>> = mutableMapOf(),
    val addressMapping: MutableMap<String, RpcInetSocketAddress> = mutableMapOf()
) : SerializableMeta