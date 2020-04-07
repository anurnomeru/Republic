package ink.anur.io.common

import org.slf4j.LoggerFactory
import javax.annotation.concurrent.ThreadSafe

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 可注册结束事件的钩子
 */
@ThreadSafe
class ShutDownHooker(val shutDownMsg: String? = null) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Volatile
    private var shutDown: Boolean = false

    private var shutDownConsumer: () -> Unit = {}

    /**
     * 重置到初始状态
     */
    @Synchronized
    fun reset() {
        this.shutDown = false
        this.shutDownConsumer = { }
    }

    /**
     * 触发结束与结束事件
     */
    @Synchronized
    fun shutdown() {
        shutDownMsg?.let { logger.info(it) }
        shutDown = true
        shutDownConsumer.invoke()
    }

    /**
     * 注册结束事件
     */
    @Synchronized
    fun shutDownRegister(shutDownSupplier: () -> Unit) {

        // 如果已经事先触发了关闭，则不需要再注册关闭事件了，直接调用关闭方法
        if (!shutDown) {
            this.shutDownConsumer = shutDownSupplier
        }
    }

    /**
     * 判断是否结束
     */
    fun isShutDown(): Boolean {
        return shutDown
    }
}