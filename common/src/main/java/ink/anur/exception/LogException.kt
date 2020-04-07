package ink.anur.exception


/**
 * Created by Anur IjuoKaruKas on 2019/7/5
 */
class LogException : KanashiException {
    constructor(message: String) : super(message)
    constructor(throwable: Throwable) : super(throwable)
}