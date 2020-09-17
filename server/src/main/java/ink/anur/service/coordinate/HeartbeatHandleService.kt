package ink.anur.service.coordinate

import ink.anur.pojo.common.RequestTypeEnum
import ink.anur.core.common.AbstractRequestMapping
import ink.anur.core.raft.RaftCenterController
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.bean.NigateInject
import ink.anur.pojo.HeartBeat
import io.netty.channel.Channel
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/28
 *
 * 专门处理心跳的处理器
 */
@NigateBean
class HeartbeatHandleService : AbstractRequestMapping() {

    @NigateInject
    private lateinit var raftCenterController: RaftCenterController

    override fun typeSupport(): RequestTypeEnum {
        return RequestTypeEnum.HEAT_BEAT
    }

    override fun handleRequest(fromServer: String, msg: ByteBuffer, channel: Channel) {
        val heartbeat = HeartBeat(msg)
        raftCenterController.receiveHeatBeat(fromServer, heartbeat.generation)
    }
}