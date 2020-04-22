package ink.anur.service.log

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.log.RecoveryComplete
import ink.anur.service.log.core.FetchService
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/4/19
 */
@NigateBean
class RecoveryCompleteHandlerService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var fetchService: FetchService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.RECOVERY_COMPLETE
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        fetchService.recoveryHandler(RecoveryComplete(msg).recoveryCompleteMeta)
    }
}