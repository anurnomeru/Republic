package ink.anur.rpc

import java.io.Serializable

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
class RpcResponseMeta(
    /**
     * 结果，如果错误的话，会附上错误码
     */
    result: Any?,
    /**
     * 请求时带的唯一标志，用于区分是哪个请求的回复
     */
    requestSign: Long,
    /**
     * 是否产生了错误
     */
    error: Boolean = false) : Serializable