package ink.anur.service.log

import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.request.MsgProcessCentreService
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.core.log.LogService
import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.pojo.log.Fetch
import ink.anur.pojo.log.FetchResponse
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/3/12
 *
 * 向某个节点发送 fetch 消息，标明自己要 fetch 的 log 进度
 *
 * 节点会将这部分数据分批次返回回去（需要多次请求）
 *
 * 如果当前节点是主节点，还会根据情况返回当前可提交的 GAO (Commit)
 */
@NigateBean
class FetchHandlerService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var logService: LogService

    @NigateInject
    private lateinit var msgProcessCentreService: MsgProcessCentreService

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.FETCH
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val fetcher = Fetch(msg)

        // 为什么要。next，因为 fetch 过来的是客户端最新的 GAO 进度，而获取的要从 GAO + 1开始
        val fetchDataInfo = logService.getAfter(fetcher.fetchGAO.next())
        if (fetchDataInfo != null) {
            msgProcessCentreService.sendAsyncByName(fromServer, FetchResponse(fetchDataInfo))
        }
    }
}