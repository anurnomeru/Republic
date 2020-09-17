package ink.anur.service

import ink.anur.common.struct.KanashiNode
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.io.common.channel.ChannelService
import ink.anur.pojo.Register
import ink.anur.pojo.RegisterResponse
import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import kotlin.random.Random
import java.net.InetSocketAddress

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 *
 * 专门用于处理注册请求
 */
@NigateBean
class RegisterHandleService : AbstractRequestMapping() {

    val CLIENT_SIGN = "CLIENT";

    private val logger = LoggerFactory.getLogger(this::class.java)

    @NigateInject
    private lateinit var channelService: ChannelService

    @NigateInject
    private lateinit var msgCenterService: MsgProcessCentreService

    override fun typeSupport(): RequestTypeEnum = RequestTypeEnum.REGISTER

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        var sign = 0
        try {
            sign = msg.getInt(AbstractStruct.TypeOffset)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val typeEnum = RequestTypeEnum.parseByByteSign(sign)

        if (typeEnum != RequestTypeEnum.REGISTER) {
            logger.error("????????????怎么发生的???????????????")
        }

        val register = Register(msg)

        var serverName = register.getServerName()
        register.getRegistrySign()
        if (serverName == CLIENT_SIGN) {
            serverName = randomName()
            logger.info("客户端节点 {} 已注册到本节点", serverName)
        } else {
            logger.info("协调节点 {} 已注册到本节点", serverName)
        }

        val inetSocket = channel.remoteAddress() as InetSocketAddress

        channelService
            .register(KanashiNode(serverName, inetSocket.address.hostAddress, inetSocket.port), channel)
        msgCenterService.sendAsync(serverName, RegisterResponse(register.getRegistrySign()))
    }

    private val random = Random(100)

    private val str = "0123456789abcdefghijklmnopqrstuvwxyz"

    private fun randomName(): String {
        val sb = StringBuffer()
        val charLength = str.length

        for (i in 0..20)
            sb.append(str[random.nextInt(charLength)])
        return sb.toString()
    }
}