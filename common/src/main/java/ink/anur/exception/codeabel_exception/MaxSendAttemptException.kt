package ink.anur.exception.codeabel_exception

import ink.anur.inject.bean.NigateBean

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
@NigateBean
class MaxSendAttemptException : CodeableException {
    constructor() : super()
    constructor(attempt: Int) : super("Reached maximum $attempt attempts when sending bytebuffer")

    override fun errorCode(): Int {
        return 2
    }
}