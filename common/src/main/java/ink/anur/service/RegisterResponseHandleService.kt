package ink.anur.service

import ink.anur.config.InetConfig
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.cuncurrent.AsyncEventRegister
import ink.anur.debug.Debugger
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.pojo.Register
import ink.anur.pojo.RegisterResponse
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 *
 * 专门用于处理注册请求
 */
@NigateBean
class RegisterResponseHandleService : AbstractRequestMapping() {

    @NigateInject(useLocalFirst = true)
    private lateinit var inetConfig: InetConfig

    private lateinit var asyncEventRegister: AsyncEventRegister

    private val logger = Debugger(this.javaClass)

    override fun typeSupport(): RequestTypeEnum = RequestTypeEnum.REGISTER_RESPONSE

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        logger.info("与节点 $fromServer 的连接已建立")
        asyncEventRegister.triggerCallback(AsyncEventRegister.AsyncEvent.REGISTER, RegisterResponse(msg).getRegistrySign())
    }

    /**
     * 生成一个 Register 对象，并注册一个回调，生成一个当前的时间戳作为 Register 请求的回调标识
     * 附带在 Register 中，当对方回复 RegisterResponse 后，会触发这个回调函数
     */
    fun genRegister(doAfterReceiveRegisterResponse: (() -> Unit)?): Register {
        return Register(
                inetConfig.getLocalServerName(),
                asyncEventRegister.registerCallbackTs(AsyncEventRegister.AsyncEvent.REGISTER, doAfterReceiveRegisterResponse)
        )
    }
}