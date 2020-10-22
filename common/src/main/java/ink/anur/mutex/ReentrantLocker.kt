package ink.anur.mutex

import ink.anur.debug.Debugger
import ink.anur.debug.DebuggerLevel
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Anur IjuoKaruKas on 2019/7/10
 */
open class ReentrantLocker {

    companion object {
        val logger = Debugger(ReentrantLocker::class.java).switch(DebuggerLevel.INFO)
    }

    private val reentrantLock: ReentrantLock = ReentrantLock()

    /**
     * 提供一个统一的锁入口
     */
    fun <T> lockSupplier(supplier: () -> T?): T? {
        val t: T?
        try {
            logger.logTrace("lock ", 2)
            reentrantLock.lock()
            t = supplier.invoke()
        } finally {
            reentrantLock.unlock()
            logger.logTrace("un lock ", 2)
        }
        return t
    }

    /**
     * 提供一个统一的锁入口
     */
    fun <T> lockSupplierCompel(supplier: () -> T): T {
        val t: T
        try {
            logger.logTrace("lock ", 2)
            reentrantLock.lock()
            t = supplier.invoke()
        } finally {
            reentrantLock.unlock()
            logger.logTrace("un lock ", 2)
        }
        return t
    }

    /**
     * 提供一个统一的锁入口
     */
    fun lockSupplier(doSomething: () -> Unit) {
        try {
            logger.logTrace("lock ", 2)
            reentrantLock.lock()
            doSomething.invoke()
        } finally {
            reentrantLock.unlock()
            logger.logTrace("un lock ", 2)
        }
    }
}