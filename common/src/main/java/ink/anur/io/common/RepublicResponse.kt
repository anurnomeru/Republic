package ink.anur.io.common

import ink.anur.exception.KanashiException
import ink.anur.exception.UnKnownException

class RepublicResponse<T>(private val result: T?, private val ke: KanashiException?) {

    private lateinit var handler: (Throwable) -> Unit

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

    fun Resp(): T = result ?: throw (ke ?: UnKnownException("Do not receive any response and Exception not defined"))
}

