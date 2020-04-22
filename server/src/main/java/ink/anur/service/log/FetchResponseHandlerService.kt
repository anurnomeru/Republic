package ink.anur.service.log

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.core.log.LogService
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.log.FetchResponse
import ink.anur.service.log.core.FetchService
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/3/18
 *
 * 发送 fetch 以后，会返回 fetch 到的 log
 */
@NigateBean
class FetchResponseHandlerService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var fetchService: FetchService

    @NigateInject
    private lateinit var recoveryReporterHandlerService: RecoveryReporterHandlerService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.FETCH_RESPONSE
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val fr = FetchResponse(msg)
        fetchService.fetchHandler(fr, fromServer)
    }
}