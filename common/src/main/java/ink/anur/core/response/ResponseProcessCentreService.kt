package ink.anur.core.response

import ink.anur.common.struct.KanashiNode
import ink.anur.config.InetConfig
import ink.anur.pojo.common.AbstractStruct
import ink.anur.core.client.ClientService
import ink.anur.exception.NetWorkException
import ink.anur.exception.UnKnownNodeException
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.io.common.channel.ChannelService
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import java.util.HashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 */
@NigateBean
class ResponseProcessCentreService {

    @NigateInject(useLocalFirst = true)
    private lateinit var inetConfig: InetConfig

    @NigateInject
    private lateinit var channelService: ChannelService

    @NigateInject
    private lateinit var clientService: ClientService

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val LOCKER_MAPPING = HashMap<String, ReentrantLock>()

    /**
     * 在向同一个服务发送东西时需要加锁
     */
    @Synchronized
    private fun getLock(serverName: String): ReentrantLock {
        var lock: ReentrantLock? = LOCKER_MAPPING[serverName]
        if (lock == null) {
            lock = ReentrantLock()
            LOCKER_MAPPING[serverName] = lock
        }
        return lock
    }

    /**
     * 向某个服务发送东西~
     */
    fun doSend(serverName: String?, body: AbstractStruct, c: Channel? = null): Throwable? {
        if (inetConfig.getLocalServerName() == serverName) {
            return null
        }

        val lock = getLock((serverName ?: c?.toString()) ?: throw NetWorkException("发送时必须指定哪个管道或哪个服务"))
        try {
            lock.lock()
            val channel = (if (c != null) {
                c
            } else {
                var channelFromCS = channelService.getChannel(serverName!!)
                if (channelFromCS == null) {
                    val node = inetConfig.getNode(serverName)
                    if (node == KanashiNode.NOT_EXIST) {
                        return UnKnownNodeException("无法在配置文件中找到节点 $serverName，故无法主动连接该节点")
                    }
                    val connectLatch = CountDownLatch(1)
                    clientService.connect(node) { connectLatch.countDown() }
                    connectLatch.await(5, TimeUnit.SECONDS)
                    channelFromCS = channelService.getChannel(serverName)
                }
                channelFromCS
            }) ?: return NetWorkException("还未与节点 [$serverName] 建立连接，无法发送！")

            if (body.getRequestType() != RequestTypeEnum.HEAT_BEAT) {
                logger.trace("---> 发送了类型为 ${body.getRequestType()} 的消息")
            }
            channel.write(Unpooled.copyInt(body.totalSize()))
            body.writeIntoChannel(channel)
            channel.flush()
        } catch (t: Throwable) {
            logger.error("向节点发送 [$serverName] 关于 ${body.getRequestType()} 的请求失败： ${t.message}")
            t.printStackTrace()
            return t
        } finally {
            lock.unlock()
        }
        return null
    }
}