package ink.anur.core.common

import java.util.concurrent.locks.AbstractQueuedSynchronizer

/**
 * Created by Anur on 2020/11/27
 */
class License : AbstractQueuedSynchronizer() {

    init {
        state = 1
    }

    fun hasLicense() = state == 0

    fun license() = acquireShared(1)

    fun disable() {
        while (!compareAndSetState(state, 1)) {
        }
    }

    fun enable() {
        while (!compareAndSetState(state, 0)) {
        }
        releaseShared(0)
    }

    override fun tryAcquireShared(arg: Int): Int {
        return -state
    }

    override fun tryReleaseShared(ign: Int): Boolean {
        return true
    }
}