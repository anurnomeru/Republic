package ink.anur.pojo.rpc.meta

import ink.anur.pojo.rpc.core.SerializableMeta

class RpcRegistrationMeta(
        /**
         * 用于 response 通知
         */
        val SIGN: Int,
        /**
         * 本地rpc服务端口
         */
        val port: Int,
        /**
         * 专门为远程调用准备的映射
         */
        val RPC_BEAN: Map<String/* bean */, HashSet<String /* method */>>,
        /**
         * 远程调用下，接口下的实现
         */
        val RPC_INTERFACE_BEAN: Map<String/* bean */, List<HashSet<String /* method */>>>
) : SerializableMeta