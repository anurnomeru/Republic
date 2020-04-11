package ink.anur.service

import ink.anur.common.struct.KanashiNode
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
import ink.anur.pojo.rpc.RpcInetSocketAddress
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.RpcRequestMeta
import ink.anur.pojo.rpc.RpcResponseMeta
import ink.anur.rpc.RpcSender
import ink.anur.util.ClassMetaUtil
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Created by Anur IjuoKaruKas on 2020/4/7
 *
 * 负责发送 rcp 请求
 */
@NigateBean
class RpcSenderService : RpcSender {

    @NigateInject
    private lateinit var msgProcessCentreService: MsgProcessCentreService

    @NigateInject
    private lateinit var rpcProviderMappingHolderService: RpcProviderMappingHolderService

    /**
     * 正常的错误都是从 provider 返回的，但是由于一些特殊情况需要自己触发等待的 CDL ，此时用这个来标记等待过后的抛错重试
     */
    private val RETRY = "RETRY"

    /**
     * 内含一个cdl，用于等待应答
     */
    private val waitingMapping = ConcurrentHashMap<Long, CountDownLatch>()

    /**
     * 应答装填处
     */
    private val responseMapping = ConcurrentHashMap<Long, RpcResponseMeta>()

    /**
     * 防止RPC断开导致的长时间等待，触发调用方尽快切换其他节点
     */
    private val sendingMapping = ConcurrentHashMap<String, ConcurrentSkipListSet<Long>>()

    private val random = Random(100)

    private var index = 0

    override fun sendRpcRequest(method: Method, interfaceName: String, alias: String?, args: Array<out Any>?, retryTimes: Int): Any? {
        val msgSign = random.nextLong()
        val cdl = CountDownLatch(1)

        return if (waitingMapping.putIfAbsent(msgSign, cdl) != null) {
            sendRpcRequest(method, interfaceName, alias, args, retryTimes)
        } else {
            val rpcRequest = RpcRequest(RpcRequestMeta(alias, interfaceName, ClassMetaUtil.methodSignGen(method), args, msgSign))

            val searchValidProvider = rpcProviderMappingHolderService.searchValidProvider(rpcRequest)

            if (searchValidProvider == null || searchValidProvider.isEmpty()) {
                throw RPCNoMatchProviderException("无法找到相应的 RPC 提供者！")
            }
            try {
                val channelOperateHandler: ClientOperateHandler = getOrConnectToAChannel(searchValidProvider)
                val sendSuccess = msgProcessCentreService.sendAsyncTo(channelOperateHandler.getChannel(), rpcRequest)

                if (!sendSuccess.get()) {
                    throw KanashiException("RPC 请求发送失败")
                }

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
                        else -> throw (RpcErrorGenerator.RPC_ERROR_MAPPING[errorSign]?.invoke() ?: RPCUnderRequestException(remove.result.toString()))
                    }
                }
                return remove.result
            } else {
                responseMapping.remove(msgSign)
                throw RPCOverTimeException(" RPC 请求超时！")
            }
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

    private val openConnections = ConcurrentHashMap<String, ClientOperateHandler>()

    /**
     * 尝试从已经有的管道或者重新发起一个长连接
     *
     * todo 有些地方的异常捕获做的其实不到位
     */
    private fun getOrConnectToAChannel(providers: Map<String, RpcInetSocketAddress>): ClientOperateHandler {
        val first = providers.keys.firstOrNull { openConnections.containsKey(it) }
        if (first != null) {
            return openConnections[first]!!
        } else {
            val entries = ArrayList(providers.entries)
            for (i in 0 until entries.size) {
                val entry = entries[index % entries.size]// 用余数是避免每次连接都连到第一个元素
                val serverName = entry.key

                index++
                val connectWaitingLatch = CountDownLatch(1)
                val clientOperateHandler = ClientOperateHandler(KanashiNode(serverName, entry.value.host, entry.value.port),
                    doAfterConnectToServer = {
                        connectWaitingLatch.countDown()
                    },
                    doAfterDisConnectToServer = {

                        // 断开连接以后，将正在等待中的线程唤醒
                        // 然后要求他们重新请求
                        // 所以这算是一个“快速恢复”的机制，不需要去等那些压根不会给回应的节点的response
                        sendingMapping[serverName]?.also {
                            for (waitingSign in it) {
                                responseMapping[waitingSign] = RpcResponseMeta(RETRY, 0L, true)
                                waitingMapping[waitingSign]?.countDown()
                                waitingMapping.remove(waitingSign)
                            }
                        }
                        sendingMapping.remove(serverName)
                        openConnections.remove(serverName)
                        false
                    })
                clientOperateHandler.start()

                if (connectWaitingLatch.await(5, TimeUnit.SECONDS)) {
                    openConnections[serverName] = clientOperateHandler
                    return clientOperateHandler
                }
            }

            throw  RPCNoMatchProviderException("无法连接上目前已经注册到注册中心的所有服务！")
        }
    }
}