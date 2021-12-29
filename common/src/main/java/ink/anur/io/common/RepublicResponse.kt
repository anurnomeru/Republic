package ink.anur.io.common

import ink.anur.exception.KanashiException
import ink.anur.exception.UnKnownException

class RepublicResponse<T>(private val result: T?, private val ke: KanashiException?) {

    private var handler: ((Throwable) -> Unit)? = null

    constructor(result: T) : this(result, null)

    companion object {
        fun <T> ExceptionWith(ke: KanashiException): RepublicResponse<T> {
            return RepublicResponse(null, ke)
        }
    }

    fun ExceptionHandler(handler: (Throwable) -> Unit): RepublicResponse<T> {
        this.handler = handler
        return this
    }

    fun Resp(): T {
        if (result != null) {
            return result
        }

        val exception = (ke ?: UnKnownException("Do not receive any response and Exception not defined"))
        handler?.let { it(exception) }
        throw exception
    }
}

