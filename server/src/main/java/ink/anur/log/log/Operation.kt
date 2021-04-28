package ink.anur.log.log

import ink.anur.pojo.common.AbstractStruct
import ink.anur.pojo.metastruct.SerializableMeta
import ink.anur.util.HessianUtil
import java.nio.ByteBuffer

/**
 * Created by Anur on 2021/2/11
 */
abstract class Operation {
    companion object {
        private const val OperationTypeOffset = 0
        private const val OperationTypeLength = 4
        private const val SizeOffset = OperationTypeOffset + OperationTypeLength
        private const val SizeLength = 4
    }

    val serializableMeta: SerializableMeta

    constructor(serializableMeta: SerializableMeta) {
        this.serializableMeta = serializableMeta
        val ser = HessianUtil.ser(serializableMeta)

        val size = ser.size
        val bf = ByteBuffer.allocate(SizeOffset + SizeLength + size)

        init(AbstractStruct.OriginMessageOverhead + ser.size, requestTypeEnum()) {
            it.put(ser)
        }
    }

    fun init(serializableMeta: SerializableMeta) {
        val bf = ByteBuffer.allocate(capacity)
        bf.position(AbstractStruct.RequestTypeOffset)
        bf.putInt(requestTypeEnum.byteSign) // type
        bf.putInt(AbstractStruct.noneIdentifier) // identifier
        then.invoke(bf)
        bf.reset()

        buffer = bf
    }


}