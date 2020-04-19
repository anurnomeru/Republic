package ink.anur.core.request

import ink.anur.common.KanashiExecutors
import ink.anur.common.KanashiIOExecutors
import ink.anur.core.common.RequestMapping
import ink.anur.core.response.ResponseProcessCentreService
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.io.common.channel.ChannelService
import ink.anur.mutex.ReentrantReadWriteLocker
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.service.RegisterHandleService
import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Future

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
     * 注册所有的请求应该采用什么处理的映射
     */
    private val requestMappingRegister = mutableMapOf<RequestTypeEnum, RequestMapping>()

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
            // serverName 是不会为空的，但是有一种情况例外，便是服务还未注册时 这里做特殊处理
            when (val serverName = channelService.getChannelName(channel)) {
                null -> registerHandleService.handleRequest("", msg, channel)
                else -> try {
                    val requestMapping = requestMappingRegister[requestTypeEnum]

                    if (requestMapping != null) {
                        // 收到正常的请求 提交到正常的线程池去
                        KanashiExecutors.execute(Runnable { requestMapping.handleRequest(serverName, msg, channel) })
                    } else {
                        logger.error("类型 $requestTypeEnum 消息没有定制化 requestMapping ！！！")
                    }

                } catch (e: Exception) {
                    logger.error("在处理来自节点 $serverName 的 $requestTypeEnum 请求时出现异常", e)
                }
            }
        }
    }

    /**
     * 获取到集群信息之后，可以用这个来发送
     */
    fun sendAsyncByChannel(channel: Channel, msg: AbstractStruct): Future<Boolean> {
        return sendAsyncByName(null, msg, true, channel)
    }

    /**
     * 此发送器保证【一个类型的消息】只能在收到回复前发送一次，类似于仅有 1 容量的Queue
     */
    fun sendAsyncByName(serverName: String?, msg: AbstractStruct, keepError: Boolean = true, channel: Channel? = null): Future<Boolean> {
        val typeEnum = msg.getRequestType()

        return KanashiIOExecutors.submit(
            Callable {
                val error = msgSendService.doSend(serverName, msg, channel)
                if (error != null) {
                    if (keepError) {
                        logger.error("尝试发送到节点 $serverName 的 $typeEnum 任务失败", error)
                    }
                    return@Callable false
                } else {
                    return@Callable true
                }
            }
        )
    }
}