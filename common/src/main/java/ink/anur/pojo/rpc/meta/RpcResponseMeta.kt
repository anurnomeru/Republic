package ink.anur.pojo.rpc.meta

import ink.anur.pojo.rpc.core.SerializableMeta

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
class RpcResponseMeta(
        /**
         * 结果，如果错误的话，会附上错误码
         */
        result: Any?,
        /**
         * 是否产生了错误
         */
        error: Boolean = false) : SerializableMeta