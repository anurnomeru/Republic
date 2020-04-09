package ink.anur.pojo.rpc

import java.io.Serializable

/**
 * Created by Anur IjuoKaruKas on 2020/4/9
 *
 * 所有客户端在连接上服务器后，必须发送自己的接口信息
 */
class RpcRegistrationMeta(
    /**
     * 用于 response 通知
     */
    val SIGN: Int,
    /**
     * 专门为远程调用准备的映射
     */
    val RPC_BEAN: Map<String/* bean */, MutableSet<String /* method */>>,

    /**
     * 远程调用下，接口下的实现
     */
    val RPC_INTERFACE_BEAN:  Map<String/* bean */, List<MutableSet<String /* method */>>>
) : Serializable