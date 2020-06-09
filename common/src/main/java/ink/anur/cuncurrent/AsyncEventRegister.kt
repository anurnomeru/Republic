package ink.anur.cuncurrent

import ink.anur.inject.NigateInject
import ink.anur.util.TimeUtil
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Created by Anur on 2020/6/9
 */
@NigateInject
class AsyncEventRegister {

    private val random = Random(1)

    private val Mapping = ConcurrentHashMap<AsyncEvent, ConcurrentHashMap<Long, (() -> Unit)?>>()

    fun registerCallback(asyncEvent: AsyncEvent, callback: (() -> Unit)?): Long {
        val nextLong = random.nextLong()
        val putIfAbsent = take(asyncEvent).putIfAbsent(nextLong, callback)
        return putIfAbsent?.let { registerCallback(asyncEvent, callback) } ?: nextLong
    }

    fun registerCallbackTs(asyncEvent: AsyncEvent, callback: (() -> Unit)?): Long {
        val ts = TimeUtil.getTime()
        val putIfAbsent = take(asyncEvent).putIfAbsent(ts, callback)
        return putIfAbsent?.let { registerCallbackTs(asyncEvent, callback) } ?: ts
    }

    fun triggerCallback(asyncEvent: AsyncEvent, sign: Long) {
        take(asyncEvent)[sign]?.invoke()
    }

    private fun take(asyncEvent: AsyncEvent): ConcurrentHashMap<Long, (() -> Unit)?> {
        return Mapping.compute(asyncEvent) { _, u -> u ?: ConcurrentHashMap() }!!
    }

    enum class AsyncEvent {
        REGISTER
    }
}