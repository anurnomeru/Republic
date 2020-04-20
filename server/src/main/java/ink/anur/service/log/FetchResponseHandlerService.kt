package ink.anur.service.log

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.raft.ElectionMetaService
import ink.anur.debug.Debugger
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.core.log.LogService
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.log.FetchResponse
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
    private lateinit var logService: LogService

    @NigateInject
    private lateinit var recoveryReporterHandlerService: RecoveryReporterHandlerService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.FETCH_RESPONSE
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val fr = FetchResponse(msg)

        val read = fr.read()
        val iterator = read.iterator()
        val gen = fr.generation

        iterator.forEach {

            // 集群恢复
            logService.append(gen, it.offset, it.logItem)

            val offset = it.offset
            if (offset == recoveryReporterHandlerService.fetchTo!!.offset) {// 如果已经同步完毕，则通知集群同步完成
                recoveryReporterHandlerService.shuttingWhileRecoveryComplete()
                recoveryReporterHandlerService.fetchTo = null
            }
        }
    }
}