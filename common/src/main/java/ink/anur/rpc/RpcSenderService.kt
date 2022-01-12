package ink.anur.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.exception.RPCErrorException
import ink.anur.exception.RPCNoMatchProviderException
import ink.anur.exception.RPCOverTimeException
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection
import ink.anur.io.common.transport.Connection.Companion.getOrCreateConnection
import ink.anur.io.common.transport.Connection.Companion.sendAndWaitingResponse
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.RpcResponse
import ink.anur.pojo.rpc.meta.RpcInetSocketAddress
import ink.anur.pojo.rpc.meta.RpcRequestMeta
import ink.anur.pojo.rpc.meta.RpcResponseMeta
import ink.anur.rpc.common.RPCError
import ink.anur.util.ClassMetaUtil
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 */
@ObsoleteCoroutinesApi
@NigateBean
class RpcSenderService : RpcSender {

    @NigateInject
    private lateinit var rpcRouteInfoHandlerService: RpcRouteInfoHandlerService

    override suspend fun sendRpcRequest(
        method: Method,
        interfaceName: String,
        alias: String?,
        args: Array<out Any>?
    ): Any? {
        val rpcRequest = RpcRequest(RpcRequestMeta(alias, interfaceName, ClassMetaUtil.methodSignGen(method), args))

        val searchValidProvider = rpcRouteInfoHandlerService.searchValidProvider(rpcRequest)

        val resp = searchValidProvider.sendAndWaitingResponse(rpcRequest, RpcResponse::class.java).await().Resp()
        val respMeta: RpcResponseMeta = resp.GetMeta()

        if (respMeta.error) {
            val rpcError = respMeta.result?.toString()?.let { RPCError.valueOfNullable(it) }
            if (rpcError == null) {
                throw RPCErrorException(
                    "RPC request ${rpcRequest.GetMeta()} to $searchValidProvider throw an empty" +
                            " service exception."
                )
            } else {
                throw RPCErrorException("RPC request ${rpcRequest.GetMeta()} to $searchValidProvider fail, cause by : ${rpcError.cause}")
            }
        } else {
            return respMeta.result
        }
    }
}