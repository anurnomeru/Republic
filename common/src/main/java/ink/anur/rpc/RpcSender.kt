package ink.anur.rpc

import java.lang.reflect.Method

/**
 * Created by Anur IjuoKaruKas on 2020/4/10
 */
interface RpcSender {

    suspend fun sendRpcRequest(method: Method, interfaceName: String, alias: String?, args: Array<out Any>?): Any?
}