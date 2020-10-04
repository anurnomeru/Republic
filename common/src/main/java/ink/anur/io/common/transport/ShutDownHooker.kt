package ink.anur.io.common.transport

import javax.annotation.concurrent.ThreadSafe

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 可注册结束事件的钩子
 */
@ThreadSafe
class ShutDownHooker {

    @Volatile
    private var shutDown: Boolean = false

    private var shutDownConsumer: () -> Unit = {}

    @Synchronized
    fun completeShutDown() {
        shutDown = true
        shutDownConsumer.invoke()
    }

    @Synchronized
    fun shutdown() = shutDownConsumer.invoke()

    @Synchronized
    fun shutDownRegister(shutDownSupplier: () -> Unit) {

        // 如果已经事先触发了关闭，则不需要再注册关闭事件了，直接调用关闭方法
        if (!shutDown) {
            this.shutDownConsumer = shutDownSupplier
        }
    }

    fun isShutDown(): Boolean = shutDown
}