package ink.anur.core.raft

import ink.anur.exception.codeabel_exception.ClusterInvalidException
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

    fun Acquire(timeout: Long = 3, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        return validLock.readLockSupplier({ true }, timeout, unit) ?: false
    }

    @Throws
    fun AcquireCompel(timeout: Long = 3, unit: TimeUnit = TimeUnit.SECONDS) {
        if (!Acquire(timeout, unit)) {
            throw ClusterInvalidException()
        }
    }
}