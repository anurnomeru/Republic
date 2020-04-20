package ink.anur.service.log

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.pojo.common.RequestTypeEnum
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/19
 */
class RecoveryCompleteHandlerService : AbstractRequestMapping() {

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RECOVERY_REPORTER
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}