package ink.anur.service

import ink.anur.common.struct.KanashiNode
import ink.anur.core.client.ClientOperateHandler
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.exception.KanashiNoneMatchRpcProviderException
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.pojo.rpc.RpcInetSocketAddress
import ink.anur.pojo.rpc.RpcRequest
import ink.anur.pojo.rpc.RpcRequestMeta
import ink.anur.pojo.rpc.RpcResponse
import ink.anur.pojo.rpc.RpcResponseMeta
import ink.anur.rpc.RpcSender
import ink.anur.util.ClassMetaUtil
import io.netty.channel.Channel
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
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

    private val waitingMapping = ConcurrentHashMap<Long, CountDownLatch>()

    private val responseMapping = ConcurrentHashMap<Long, RpcResponseMeta>()

    private val random = Random(100)

    private var index = 0

    // todo 添加超时机制
    // todo 各种异常的捕获
    override fun sendRpcRequest(method: Method, interfaceName: String, alias: String?, args: Array<out Any>?): Any? {
        val msgSign = random.nextLong()
        val cdl = CountDownLatch(1)

        return if (waitingMapping.putIfAbsent(msgSign, cdl) != null) {
            sendRpcRequest(method, interfaceName, alias, args)
        } else {
            val rpcRequest = RpcRequest(RpcRequestMeta(alias, interfaceName, ClassMetaUtil.methodSignGen(method), args, msgSign))

            val searchValidProvider = rpcProviderMappingHolderService.searchValidProvider(rpcRequest)

            if (searchValidProvider == null || searchValidProvider.isEmpty()) {
                throw KanashiNoneMatchRpcProviderException("无法找到相应的 RPC 提供者！")
            }

            msgProcessCentreService.sendAsyncTo(getOrConnectToAChannel(searchValidProvider), rpcRequest)
            cdl.await()// 等待收到response
            val remove = responseMapping.remove(msgSign)!!
            remove.result
        }
    }

    /**
     * 收到response以后，进行通知
     */
    fun notifyRpcResponse(rpcResponseMeta: RpcResponseMeta) {
        val requestSign = rpcResponseMeta.requestSign
        responseMapping[requestSign] = rpcResponseMeta
        waitingMapping[requestSign]!!.countDown()
    }

    private val openConnections = ConcurrentHashMap<String, ClientOperateHandler>()

    /**
     * 尝试从已经有的管道或者重新发起一个长连接
     *
     * todo 有些地方的异常捕获做的其实不到位
     */
    private fun getOrConnectToAChannel(providers: Map<String, RpcInetSocketAddress>): Channel {
        val first = providers.keys.firstOrNull { openConnections.containsKey(it) }
        if (first != null) {
            return openConnections[first]!!.getChannel()
        } else {
            val entries = ArrayList(providers.entries)
            for (i in 0 until entries.size) {
                val entry = entries[index % entries.size]// 用余数是避免每次连接都连到第一个元素
                index++
                val connectWaitingLatch = CountDownLatch(1)
                val clientOperateHandler = ClientOperateHandler(KanashiNode(entry.key, entry.value.host, entry.value.port),
                    doAfterConnectToServer = {
                        connectWaitingLatch.countDown()
                    },
                    doAfterDisConnectToServer = {
                        openConnections.remove(entry.key)
                        false
                    })
                clientOperateHandler.start()

                if (connectWaitingLatch.await(5, TimeUnit.SECONDS)) {
                    openConnections[entry.key] = clientOperateHandler
                    return clientOperateHandler.getChannel()
                }
            }

            throw  KanashiNoneMatchRpcProviderException("无法连接上目前已经注册到注册中心的所有服务！")
        }
    }
}