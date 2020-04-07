package ink.anur.mutex

import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Created by Anur IjuoKaruKas on 2019/7/10
 *
 * 提供一个基于读写锁的二次封装类
 */
open class ReentrantReadWriteLocker : ReentrantReadWriteLock() {

    private val readLock: ReadLock = readLock()

    private val writeLock: WriteLock = writeLock()

    private val switch = writeLock.newCondition()

    @Volatile
    private var switcher = 0

    fun switchOff() {
        switcher = 1
    }

    fun switchOn() {
        try {
            writeLock.lock()
            switcher = 0
            switch.signalAll()
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * 提供一个统一的锁入口
     */
    fun writeLocker(doSomething: () -> Unit) {
        try {
            writeLock.lock()

            if (switcher > 0) {
                switch.await()
            }

            doSomething.invoke()
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * 提供一个统一的锁入口
     */
    fun <T> writeLockSupplier(supplier: () -> T): T? {
        val t: T
        try {
            writeLock.lock()

            if (switcher > 0) {
                switch.await()
            }

            t = supplier.invoke()
        } finally {
            writeLock.unlock()
        }
        return t
    }

    /**
     * 提供一个统一的锁入口
     */
    fun <T> writeLockSupplierCompel(supplier: () -> T): T {
        val t: T
        try {
            writeLock.lock()

            if (switcher > 0) {
                switch.await()
            }

            t = supplier.invoke()
        } finally {
            writeLock.unlock()
        }
        return t
    }


    /**
     * 提供一个统一的锁入口
     */
    fun <T> readLockSupplier(supplier: () -> T): T? {
        val t: T
        try {
            readLock.lock()

            if (switcher > 0) {
                switch.await()
            }

            t = supplier.invoke()
        } finally {
            readLock.unlock()
        }
        return t
    }

    /**
     * 提供一个统一的锁入口
     */
    fun <T> readLockSupplierCompel(supplier: () -> T): T {
        val t: T
        try {
            readLock.lock()

            if (switcher > 0) {
                switch.await()
            }

            t = supplier.invoke()
        } finally {
            readLock.unlock()
        }
        return t
    }


    /**
     * 提供一个统一的锁入口
     */
    fun readLocker(doSomething: () -> Unit) {
        try {
            readLock.lock()

            if (switcher > 0) {
                switch.await()
            }

            doSomething.invoke()
        } finally {
            readLock.unlock()
        }
    }

}