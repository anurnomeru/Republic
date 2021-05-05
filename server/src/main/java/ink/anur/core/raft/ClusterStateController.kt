package ink.anur.core.raft

import ink.anur.inject.bean.NigateBean
import ink.anur.inject.event.Event
import ink.anur.inject.event.NigateListener
import ink.anur.mutex.ReentrantReadWriteLocker
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2021/5/5
 */
@NigateBean
class ClusterStateController {

    private val validLock = ReentrantReadWriteLocker()

    init {
        validLock.switchOff()
    }

    @NigateListener(onEvent = Event.CLUSTER_VALID)
    private fun onClusterValid() {
        validLock.switchOn()
    }

    @NigateListener(onEvent = Event.CLUSTER_INVALID)
    private fun onClusterInvalid() {
        validLock.switchOff()
    }

    private fun acquire(timeout: Long, unit: TimeUnit) {
        validLock.readLockSupplier({}, timeout, unit)
    }
}