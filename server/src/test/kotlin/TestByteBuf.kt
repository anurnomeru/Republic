import io.netty.buffer.PooledByteBufAllocator
import org.junit.Test

/**
 * Created by Anur IjuoKaruKas on 2022/1/12
 */
class TestByteBuf {

    @Test
    fun testByteBuf() {
        val byteBuf = PooledByteBufAllocator().buffer()
        byteBuf.writeBytes("testandtest".encodeToByteArray())

        val writerIndex = byteBuf.writerIndex()

        val slice = byteBuf.slice(0, writerIndex / 2)

        val readBytes = slice.readBytes(slice.capacity())

        println()
    }
}