import ink.anur.mutex.SwitchableReentrantReadWriteLocker
import org.junit.Test

/**
 * Created by Anur IjuoKaruKas on 2022/1/12
 */
class TestSwitcher {

    @Test
    fun test() {
        val lock = SwitchableReentrantReadWriteLocker()
        lock.switchOff()

        Thread {
            Thread.sleep(2000)
            lock.switchOn()
        }.start()

        println("ac")
        lock.readLockSupplier {
            true
        }

        println("out")
    }
}