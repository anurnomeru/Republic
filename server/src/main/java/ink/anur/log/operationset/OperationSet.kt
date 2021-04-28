package ink.anur.log.operationset

import ink.anur.log.common.OperationAndOffset
import ink.anur.pojo.metastruct.MetaStruct
import java.io.IOException
import java.nio.channels.GatheringByteChannel

/**
 * Created by Anur on 2021/1/17
 */
abstract class OperationSet {

    companion object {
        private const val OffsetLength = 8
        private const val MessageSizeLength = 4
         const val LogOverhead = OffsetLength + MessageSizeLength
    }

    /**
     * The size of a message set containing the given operationCollections
     */
    fun messageSetSize(operationCollection: List<MetaStruct<*>>): Int = operationCollection.size * LogOverhead + operationCollection.sumBy { it.totalSize() }

    @Throws(IOException::class)
    abstract fun writeTo(channel: GatheringByteChannel, offset: Long, maxSize: Int): Int

    abstract operator fun iterator(): Iterator<OperationAndOffset?>?
}