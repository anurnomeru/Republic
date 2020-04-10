package ink.anur.pojo.rpc

import java.io.Serializable
import java.net.InetSocketAddress

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * rcp 包含所有的集群内可以使用的 provider
 */
class RpcProviderMappingMeta(
    val providerMapping: MutableMap<String/* bean */,
        MutableMap<String /* methodSign */, MutableSet<String/* serverName */>>>,
    val addressMapping: MutableMap<String, InetSocketAddress>
) : Serializable