package ink.anur.core.common

import ink.anur.inject.bean.NigatePostConstruct
import ink.anur.io.common.transport.Connection

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 *
 * 定义一个消息如何消费的顶级接口
 */
abstract class AbstractRequestMapping : RequestMapping {

    @NigatePostConstruct
    fun init() {
        Connection.registerRequestMapping(this.typeSupport(), this)
    }
}