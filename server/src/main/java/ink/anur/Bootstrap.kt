package ink.anur

import ink.anur.config.BootstrapConfiguration
import ink.anur.core.raft.RaftCenterController
import ink.anur.inject.Nigate
import ink.anur.core.log.LogService
import ink.anur.pojo.log.LogItem
import ink.anur.pojo.log.TestingProposal


/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 */
object Bootstrap {

    @Volatile
    private var RUNNING = true

    @JvmStatic
    fun main(args: Array<String>) {

        // 保存jvm参数
        BootstrapConfiguration.init(args)
        // 初始化 bean管理
        Nigate

        val logService = Nigate.getBeanByClass(LogService::class.java)
        val raftCenterController = Nigate.getBeanByClass(RaftCenterController::class.java)

        Thread.sleep(1000)

        for (i in 0 until 1000000) {
            logService.appendForLeader(LogItem(TestingProposal()))
            if (i % 100 == 0) {
                println(i)
            }
        }

        while (RUNNING) {
            Thread.sleep(1000)
        }
    }
}