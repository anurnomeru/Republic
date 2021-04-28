package ink.anur.pojo.rpc.meta

import ink.anur.pojo.metastruct.SerializableMeta

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 进行一个请求
 */
class RpcRequestMeta(
        /**
         * 请求时直接指定 bean 名字来 RPC，可以不指定
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
        val requestParams: Array<out Any>?) : SerializableMeta {
        override fun toString(): String {
                return "RpcRequestMeta(requestBean=$requestBean, requestInterface='$requestInterface', requestMethodSign='$requestMethodSign', requestParams=${requestParams?.contentToString()})"
        }
}