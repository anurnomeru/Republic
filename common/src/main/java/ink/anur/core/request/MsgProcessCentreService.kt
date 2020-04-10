package ink.anur.core.request

import ink.anur.common.Constant
import ink.anur.common.pool.EventDriverPool
import ink.anur.common.struct.Request
import ink.anur.core.common.RequestMapping
import ink.anur.core.response.ResponseProcessCentreService
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigatePostConstruct
import ink.anur.io.common.channel.ChannelService
import ink.anur.mutex.ReentrantReadWriteLocker
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.service.RegisterHandleService
import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2020/2/24
 *
 * 消息控制中心，负责收发信息
 */
@NigateBean
class MsgProcessCentreService : ReentrantReadWriteLocker() {

    @NigateInject
    private lateinit var channelService: ChannelService

    @NigateInject
    private lateinit var msgSendService: ResponseProcessCentreService

    @NigateInject
    private lateinit var registerHandleService: RegisterHandleService

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 此 map 用于保存接收到的信息的时间戳，如果收到旧的请求，则不作处理
     */
    private val receiveLog = mutableMapOf<String, MutableMap<RequestTypeEnum, Long?>>()

    /**
     * 注册所有的请求应该采用什么处理的映射
     */
    private val requestMappingRegister = mutableMapOf<RequestTypeEnum, RequestMapping>()

    @NigatePostConstruct
    fun postConstruct() {
        EventDriverPool.register(Request::class.java,
            8,
            200,
            TimeUnit.MILLISECONDS
        ) {
            this.receive(it.msg, it.typeEnum, it.channel)
        }
    }

    /**
     * 注册 RequestMapping
     */
    fun registerRequestMapping(typeEnum: RequestTypeEnum, requestMapping: RequestMapping) {
        requestMappingRegister[typeEnum] = requestMapping
    }

    /**
     * 接收到消息如何处理
     */
    fun receive(msg: ByteBuffer, requestTypeEnum: RequestTypeEnum, channel: Channel?) {

        if (channel == null) {
            logger.error("????????????????????????????????????")
        } else {
            val requestTimestampCurrent = msg.getLong(AbstractStruct.TimestampOffset)
            val serverName = channelService.getChannelName(channel)

            // serverName 是不会为空的，但是有一种情况例外，便是服务还未注册时 这里做特殊处理
            when {
                serverName == null -> registerHandleService.handleRequest("", msg, channel)
                writeLockSupplierCompel {
                    when (requestTypeEnum) {
                        // command 类型不需要防止重发的问题，可以无限重发，其他类型避免同一个消息发送多次被重复消费
                        else -> {
                            var changed = false
                            receiveLog.compute(serverName) { _, timestampMap ->
                                (timestampMap ?: mutableMapOf()).also {
                                    it.compute(requestTypeEnum) { _, timestampBefore ->
                                        changed = (timestampBefore == null || requestTimestampCurrent > timestampBefore)
                                        if (changed) requestTimestampCurrent else timestampBefore
                                    }
                                }
                            }
                            changed
                        }
                    }

                } -> try {
                    val requestMapping = requestMappingRegister[requestTypeEnum]

                    if (requestMapping != null) {
                        requestMapping.handleRequest(serverName, msg, channel)// 收到正常的请求
                    } else {
                        logger.error("类型 $requestTypeEnum 消息没有定制化 requestMapping ！！！")
                    }

                } catch (e: Exception) {
                    logger.error("在处理来自节点 $serverName 的 $requestTypeEnum 请求时出现异常", e)
                    writeLocker {
                        receiveLog.compute(serverName) { _, timestampMap ->
                            (timestampMap ?: mutableMapOf()).also { it.remove(requestTypeEnum) }
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取到集群信息之后，可以用这个来发送
     */
    fun sendAsyncTo(channel: Channel, msg: AbstractStruct): Boolean {
        return sendAsync(null, msg, true, channel)
    }

    /**
     * 此发送器保证【一个类型的消息】只能在收到回复前发送一次，类似于仅有 1 容量的Queue
     */
    fun sendAsync(serverName: String?, msg: AbstractStruct, keepError: Boolean = false, channel: Channel? = null): Boolean {
        val typeEnum = msg.getRequestType()

        val error = msgSendService.doSend(serverName, msg, channel)
        if (error != null) {
            if (keepError) {
                logger.error("尝试发送到节点 $serverName 的 $typeEnum 任务失败", error)
            }
            return false
        }
        return true
    }
}