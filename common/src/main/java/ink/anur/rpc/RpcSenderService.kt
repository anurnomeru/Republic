package ink.anur.rpc

import ink.anur.common.struct.KanashiNode
import ink.anur.common.struct.RepublicNode
import ink.anur.core.client.ClientOperateHandler
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.exception.KanashiException
import ink.anur.exception.RPCNoMatchProviderException
import ink.anur.exception.RpcErrorGenerator
import ink.anur.exception.RPCOverTimeException
import ink.anur.exception.RPCUnKnowException
import ink.anur.exception.RPCUnderRequestException
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.transport.Connection
import ink.anur.io.common.transport.Connection.Companion.getOrCreateConnection
import ink.anur.pojo.rpc.meta.RpcInetSocketAddress
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.meta.RpcRequestMeta
import ink.anur.pojo.rpc.meta.RpcResponseMeta
import ink.anur.util.ClassMetaUtil
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 负责发送 rcp 请求
 */
@NigateBean
class RpcSenderService : RpcSender {

    @NigateInject
    private lateinit var rpcProviderMappingHolderService: RpcProviderMappingHolderService

    /**
     * 正常的错误都是从 provider 返回的，但是由于一些特殊情况需要自己触发等待的 CDL ，此时用这个来标记等待过后的抛错重试
     */
    private val RETRY = "RETRY"

    private val openConnections = ConcurrentHashMap<String, Connection>()

    private var index = 0

    override fun sendRpcRequest(method: Method, interfaceName: String, alias: String?, args: Array<out Any>?, retryTimes: Int): Any? {
        val rpcRequest = RpcRequest(RpcRequestMeta(alias, interfaceName, ClassMetaUtil.methodSignGen(method), args))

        val searchValidProvider = rpcProviderMappingHolderService.searchValidProvider(rpcRequest)

        if (searchValidProvider == null || searchValidProvider.isEmpty()) {
            throw RPCNoMatchProviderException("无法找到相应的 RPC 提供者！")
        }
        try {
            val connection = getConnection(searchValidProvider)

            sendingMapping.compute(channelOperateHandler.getNodeName()) { _, set ->
                val s = set ?: ConcurrentSkipListSet()
                s.add(msgSign)
                return@compute s
            }
        } catch (e: Exception) {// 在发送阶段出现了报错，直接进行重试！
            if (retryTimes > 0) {
                return sendRpcRequest(method, interfaceName, alias, args, retryTimes - 1)
            } else {
                throw RPCUnKnowException("尝试多次向可用的 RPC Provider 发送请求，但是在发送过程就失败了！讲道理不会出现这个异常的！")
            }
        }

        if (cdl.await(5, TimeUnit.SECONDS)) {// 需要做成可以配置的
            val remove = responseMapping.remove(msgSign)!!

            if (remove.error) {
                return when (val errorSign = remove.result) {
                    RETRY -> return sendRpcRequest(method, interfaceName, alias, args, retryTimes - 1)
                    else -> throw (RpcErrorGenerator.RPC_ERROR_MAPPING[errorSign]?.invoke()
                            ?: RPCUnderRequestException(remove.result.toString()))
                }
            }
            return remove.result
        } else {
            responseMapping.remove(msgSign)
            throw RPCOverTimeException(" RPC 请求超时！")
        }
    }

    /**
     * 收到response以后，进行通知
     */
    fun notifyRpcResponse(fromServer: String, rpcResponseMeta: RpcResponseMeta) {
        val requestSign = rpcResponseMeta.requestSign
        responseMapping[requestSign] = rpcResponseMeta
        waitingMapping[requestSign]!!.countDown()
        waitingMapping.remove(requestSign)
        sendingMapping.compute(fromServer) { _, set ->
            val s = set ?: ConcurrentSkipListSet()
            s.remove(requestSign)
            return@compute s
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

                if (connection.established() && connection.waitForLicense(1L, TimeUnit.SECONDS)) {
                    openConnections[serverName] = connection
                    return connection
                }
            }

            throw  RPCNoMatchProviderException("无法连接上目前已经注册到注册中心的所有服务！")
        }
    }
}