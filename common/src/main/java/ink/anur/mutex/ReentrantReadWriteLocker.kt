package ink.anur.mutex

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.*

/**
 * Created by Anur IjuoKaruKas on 2019/7/10
 */
open class ReentrantReadWriteLocker {

    private var rwLock = ReentrantReadWriteLock()
    private val rl = rwLock.readLock()
    private val wl = rwLock.writeLock()
    private val condition: Condition = wl.newCondition()

    @Volatile
    private var switcher: Int = 0

    fun switchOff() {
        switcher = 1
    }

    fun switchOn() {
        runBlocking {
            launch {
                wl.lock()
                try {
                    switcher = 0
                    condition.signalAll()
                } finally {
                    wl.unlock()
                }
            }
        }
    }

    fun writeLocker(doSomething: () -> Unit) {
        writeLockSupplier { doSomething() }
    }

    fun <T> writeLockSupplierCompel(supplier: () -> T): T {
        return writeLockSupplier(supplier)!!
    }

    fun <T> writeLockSupplier(supplier: () -> T): T? {

        wl.lock()
        try {
            if (switcher > 0) {
                condition.await()
            }

            return supplier.invoke()
        } finally {
            wl.unlock()
        }
    }

    fun readLocker(doSomething: () -> Unit) {
        readLockSupplier(doSomething)
    }

    fun <T> readLockSupplierCompel(supplier: () -> T): T {
        return readLockSupplier(supplier)!!
    }

    fun <T> readLockSupplier(supplier: () -> T): T? {

        rl.tryLock()
        try {
            if (switcher > 0) {
                wl.lock()
                condition.await()
                wl.unlock()
            }

            return supplier.invoke()
        } finally {
            rl.unlock()
        }
    }

    fun readLockSupplier(supplier: () -> Boolean, timeout: Long, unit: TimeUnit): Boolean? {
        if (rl.tryLock(timeout, unit)) {
            try {
                if (switcher > 0) {
                    wl.lock()
                    condition.await()
                    wl.unlock()
                }

                return supplier.invoke()
            } finally {
                rl.unlock()
            }
        }
        return null
    }
}