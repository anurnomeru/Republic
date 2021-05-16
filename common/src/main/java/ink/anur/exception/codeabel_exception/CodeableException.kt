package ink.anur.exception.codeabel_exception

import ink.anur.exception.KanashiException
import ink.anur.inject.bean.NigatePostConstruct

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
abstract class CodeableException : KanashiException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, thr: Throwable) : super(msg, thr)
    constructor(thr: Throwable) : super(thr)
    constructor() : super("Not msg Defined in none-param exception constructor")

    // subclass should anno with @NigateBean for auto registry
    @NigatePostConstruct
    fun init() {
        Register(this.errorCode(), this)
    }

    abstract fun errorCode(): Int

    companion object {
        private val map = mutableMapOf<Int, Class<out CodeableException>>()

        fun Register(errorCode: Int, exception: CodeableException) {
            map[errorCode] = exception.javaClass
        }

        fun Error(errorCode: Int): CodeableException {
            return map[errorCode]?.newInstance() ?: UnkownCodeableException()
        }
    }
}