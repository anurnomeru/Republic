package ink.anur.log.ex

import java.util.*

/**
 * Created by Anur on 2021/1/17
 */
abstract class IteratorTemplate<T> : Iterator<T> {
    private var state = State.NOT_READY

    private var nextItem: T? = null

    override operator fun next(): T {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        state = State.NOT_READY // 需要滚到下一个item，这期间不给next
        return nextItem!!
    }

    fun peek(): T? {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        return nextItem
    }

    override operator fun hasNext(): Boolean {
        check(state != State.FAILED) { "Iterator is in failed state" }
        return when (state) {
            State.DONE -> false
            State.READY -> true
            else -> maybeComputeNext()
        }
    }

    abstract fun makeNext(): T?

    fun maybeComputeNext(): Boolean {
        state = State.FAILED
        nextItem = makeNext()
        return if (state == State.DONE) {
            false
        } else {
            state = State.READY
            true
        }
    }

    protected fun allDone(): T? {
        state = State.DONE
        return null
    }

    fun remove() {
        throw UnsupportedOperationException("Removal not supported")
    }

    protected fun resetState() {
        state = State.NOT_READY
    }

    internal enum class State {
        DONE, READY, NOT_READY, FAILED
    }
}