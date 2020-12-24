package ink.anur.common

/**
 * Created by Anur IjuoKaruKas on 2020/2/22
 *
 * 调用 start 方法，自动在线程池注册此任务
 */
abstract class KanashiRunnable : Runnable {

    var name: String? = null

    var isDaemon: Boolean? = null

    fun start() {
        if (name != null || isDaemon != null) {
            val thread = Thread(this)
            name?.also { thread.name = it }
            isDaemon?.also { thread.isDaemon = it }
            KanashiExecutors.execute(thread)
        } else {
            KanashiExecutors.execute(this)
        }
    }
}