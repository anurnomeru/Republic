package ink.anur.core.common

import ink.anur.common.struct.RepublicNode
import ink.anur.inject.bean.NigatePostConstruct
import ink.anur.io.common.transport.Connection
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/25
 *
 * 定义一个消息如何消费的顶级接口
 */
abstract class AbstractClusterValidRequestMapping : RequestMapping {

    @NigatePostConstruct
    fun init() {
        Connection.registerRequestMapping(this.typeSupport(), this)
    }

    override fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer) {
        handleClusterValidRequest(republicNode, msg)

    }

    abstract fun handleClusterValidRequest(republicNode: RepublicNode, msg: ByteBuffer)
}