package ink.anur.core

import ink.anur.debug.Debugger
import ink.anur.exception.codeabel_exception.ClusterInvalidException
import ink.anur.inject.bean.NigateBean
import ink.anur.inject.event.Event
import ink.anur.inject.event.NigateListener
import ink.anur.mutex.SwitchableReentrantReadWriteLocker
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

/**
 * Created by Anur IjuoKaruKas on 2021/5/5
 */
@NigateBean
class RpcStateController {

    private val validLock = SwitchableReentrantReadWriteLocker()
    private val logger = Debugger(this::class.java)

    init {
        validLock.switchOff()
    }

    fun onRpcValid() {
        validLock.switchOn()
    }

    fun onRpcInvalid() {
        validLock.switchOff()
    }

    @TestOnly
    fun letRpcValid() {
        validLock.switchOn()
    }

    // Acquire make sure the func is running while cluster valid
    fun Acquire(timeout: Long = 3, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        return validLock.readLockSupplier({ true }, timeout, unit) ?: false
    }

    // Acquire make sure the func is running while cluster valid
    fun Acquire(): Boolean {
        return validLock.readLockSupplier { true } ?: false
    }

    @Throws
    fun AcquireCompel(timeout: Long = 3, unit: TimeUnit = TimeUnit.SECONDS) {
        if (!Acquire(timeout, unit)) {
            throw ClusterInvalidException()
        }
    }
}