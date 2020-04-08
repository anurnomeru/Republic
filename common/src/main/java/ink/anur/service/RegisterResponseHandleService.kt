package ink.anur.service

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.debug.Debugger
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigateListenerService
import ink.anur.pojo.RegisterResponse
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.channel.Channel
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 *
 * 专门用于处理注册请求
 */
@NigateBean
class RegisterResponseHandleService : AbstractRequestMapping() {

    private val logger = Debugger(this.javaClass)

    private val ramdon = Random(100)

    // todo 可能存在内存泄露问题
    private val callbackMapping = ConcurrentHashMap<Long, (() -> Unit)?>()

    @NigateInject
    private lateinit var nigateListenerService: NigateListenerService

    override fun typeSupport(): RequestTypeEnum = RequestTypeEnum.REGISTER_RESPONSE

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        logger.info("与节点 $fromServer 的连接已建立")
        val function = callbackMapping[RegisterResponse(msg).getRegistrySign()]
        function?.invoke()?:logger.debug("没有！！！！")
    }

    fun registerCallBack(doAfterConnectToServer: (() -> Unit)?): Long {
        val nextLong = ramdon.nextLong()
        return if (callbackMapping.contains(nextLong)) {
            registerCallBack(doAfterConnectToServer)
        } else {
            doAfterConnectToServer?.also { callbackMapping[nextLong] = it }
            nextLong
        }
    }
}