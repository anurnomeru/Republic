package ink.anur.pojo.rpc

import java.io.Serializable

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 进行一个请求
 */
class RpcRequestMeta(
    /**
     * 请求时直接指定 bean 名字来 RPC_REQUEST，可以不指定
     */
    val requestBean: String?,
    /**
     * 此请求的接口
     */
    val requestInterface: String,
    /**
     * 此请求的方法唯一标志
     */
    val requestMethodSign: String,
    /**
     * 此请求的参数
     */
    val requestParams: Array<out Any>?,
    /**
     * 此请求的请求标志，用于区分回复
     */
    val msgSign: Long) : Serializable