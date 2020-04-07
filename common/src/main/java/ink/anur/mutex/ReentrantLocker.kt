package ink.anur.mutex

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Anur IjuoKaruKas on 2019/7/10
 */
open class ReentrantLocker {
    private val reentrantLock: ReentrantLock = ReentrantLock()

    fun newCondition(): Condition {
        return reentrantLock.newCondition()
    }

    /**
     * 提供一个统一的锁入口
     */
    fun <T> lockSupplier(supplier: () -> T?): T? {
        reentrantLock.newCondition()

        val t: T?
        try {
            reentrantLock.lock()
            t = supplier.invoke()
        } finally {
            reentrantLock.unlock()
        }
        return t
    }

    /**
     * 提供一个统一的锁入口
     */
    fun <T> lockSupplierCompel(supplier: () -> T): T {
        val t: T
        try {
            reentrantLock.lock()
            t = supplier.invoke()
        } finally {
            reentrantLock.unlock()
        }
        return t
    }

    /**
     * 提供一个统一的锁入口
     */
    fun lockSupplier(doSomething: () -> Unit) {
        try {
            reentrantLock.lock()
            doSomething.invoke()
        } finally {
            reentrantLock.unlock()
        }
    }
}