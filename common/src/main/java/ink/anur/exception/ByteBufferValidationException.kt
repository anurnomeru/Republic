package ink.anur.exception


/**
 * Created by Anur IjuoKaruKas on 2019/7/5
 *
 * byteBuffer 没有通过校验
 */
class ByteBufferValidationException(msg: String = "Message is corrupt") : KanashiException(msg)