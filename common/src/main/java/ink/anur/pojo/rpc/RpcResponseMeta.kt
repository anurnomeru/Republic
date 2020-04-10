package ink.anur.pojo.rpc

import java.io.Serializable

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
class RpcResponseMeta(
    /**
     * 结果，如果错误的话，会附上错误码
     */
    val result: Any?,
    /**
     * 请求时带的唯一标志，用于区分是哪个请求的回复
     */
    val requestSign: Long,
    /**
     * 是否产生了错误
     */
    val error: Boolean = false) : Serializable