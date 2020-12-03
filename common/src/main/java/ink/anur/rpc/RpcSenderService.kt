package ink.anur.rpc

import ink.anur.common.struct.RepublicNode
import ink.anur.exception.RPCErrorException
import ink.anur.exception.RPCNoMatchProviderException
import ink.anur.exception.RPCOverTimeException
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection
import ink.anur.io.common.transport.Connection.Companion.getOrCreateConnection
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.RpcResponse
import ink.anur.pojo.rpc.meta.RpcInetSocketAddress
import ink.anur.pojo.rpc.meta.RpcRequestMeta
import ink.anur.pojo.rpc.meta.RpcResponseMeta
import ink.anur.rpc.common.RPCError
import ink.anur.util.ClassMetaUtil
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 负责发送 rcp 请求
 */
@NigateBean
class RpcSenderService : RpcSender {

    @NigateInject
    private lateinit var rpcPouteInfoHandlerService: RpcPouteInfoHandlerService

    private val openConnections = ConcurrentHashMap<String, Connection>()

    private var index = 0

    override fun sendRpcRequest(method: Method, interfaceName: String, alias: String?, args: Array<out Any>?): Any? {
        val rpcRequest = RpcRequest(RpcRequestMeta(alias, interfaceName, ClassMetaUtil.methodSignGen(method), args))

        val searchValidProvider = rpcPouteInfoHandlerService.searchValidProvider(rpcRequest)

        if (searchValidProvider == null || searchValidProvider.isEmpty()) {
            throw RPCNoMatchProviderException("can not find provider for request ${rpcRequest.serializableMeta}")
        }

        val connection = getConnection(searchValidProvider)
        val resp = connection.sendAndWaitForResponse(rpcRequest, RpcResponse::class.java)

        if (resp == null) {
            throw RPCOverTimeException("RPC request ${rpcRequest.serializableMeta} to $connection is timeout")
        } else {
            val respMeta: RpcResponseMeta = resp.serializableMeta

            if (respMeta.error) {
                val rpcError = respMeta.result?.let { it.toString() }?.let { RPCError.valueOfNullable(it) }
                if (rpcError == null) {
                    throw RPCErrorException("RPC request ${rpcRequest.serializableMeta} to $connection throw an service exception: $rpcError")
                } else {
                    throw RPCErrorException("RPC request ${rpcRequest.serializableMeta} to $connection fail, cause by : ${rpcError.cause}")
                }
            } else {
                return respMeta.result
            }
        }
    }

    private fun getConnection(providers: Map<String, RpcInetSocketAddress>): Connection {
        val first = providers.keys.firstOrNull { openConnections.containsKey(it) }
        if (first != null) {
            val mayValidConnection = openConnections[first]
            return if (mayValidConnection?.established() == true) {
                mayValidConnection
            } else {
                openConnections.remove(first)
                getConnection(providers)
            }
        } else {
            val entries = ArrayList(providers.entries)
            for (i in 0 until entries.size) {
                val entry = entries[index % entries.size]// 用余数是避免每次连接都连到第一个元素
                val serverName = entry.key

                index++
                val connection = RepublicNode.construct(entry.value.host, entry.value.port)
                        .getOrCreateConnection(true)

                if (connection.established() && connection.waitForSendLicense(1L, TimeUnit.SECONDS)) {
                    openConnections[serverName] = connection
                    return connection
                }
            }

            throw  RPCNoMatchProviderException("无法连接上目前已经注册到注册中心的所有服务！")
        }
    }
}