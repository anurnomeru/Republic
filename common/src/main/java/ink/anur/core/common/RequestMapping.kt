package ink.anur.core.common

import ink.anur.common.struct.RepublicNode
import ink.anur.pojo.common.RequestTypeEnum
import java.lang.Exception
import java.nio.ByteBuffer

/**
 * Created by Anur IjuoKaruKas on 2020/2/24
 */
interface RequestMapping {

    fun typeSupport(): RequestTypeEnum

    fun handleRequest(republicNode: RepublicNode, msg: ByteBuffer)
}