package ink.anur.exception.codeabel_exception

import ink.anur.inject.bean.NigateBean

/**
 * Created by Anur IjuoKaruKas on 2021/5/16
 */
@NigateBean
class UnkownCodeableException() : CodeableException("Exception code from remote can't not be identified") {
    override fun errorCode(): Int {
        return -1
    }
}