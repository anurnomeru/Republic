package ink.anur.pojo.rpc.meta

import ink.anur.pojo.rpc.core.SerializableMeta

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
class RpcResponseMeta(
        /**
         * 结果，如果错误的话，会附上错误码
         */
        val result: Any?,
        /**
         * 是否产生了错误
         */
        val error: Boolean = false) : SerializableMeta