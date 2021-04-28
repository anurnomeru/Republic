package ink.anur.log.operationset

import ink.anur.exception.LogException
import ink.anur.log.common.OffsetAndPosition
import ink.anur.log.common.OperationAndOffset
import ink.anur.log.ex.IteratorTemplate
import ink.anur.pojo.metastruct.MetaStruct
import ink.anur.pojo.metastruct.SerializableMeta
import ink.anur.util.FileIOUtil
import ink.anur.util.IteratorTemplate
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Created by Anur on 2021/1/17
 */
class FileOperationSet(private val file: File, private val fileChannel: FileChannel, private val start: Int, private val end: Int, private val isSlice: Boolean) : OperationSet() {

    constructor(file: File, fileChannel: FileChannel) {
        FileOperationSet(file, fileChannel, 0, Int.MAX_VALUE, false)
    }

    constructor(file: File) {
        FileOperationSet(file, FileIOUtil.openChannel(file, true))
    }

    constructor(file: File, mutable: Boolean) {
        FileOperationSet(file, FileIOUtil.openChannel(file, mutable))
    }

    constructor(file: File, channel: FileChannel, start: Int, end: Int) {
        FileOperationSet(file, channel, start, end, true)
    }

    private var currentSize: AtomicInteger

    init {
        val s: AtomicInteger
        if (isSlice) {
            s = AtomicInteger(end - start)
        } else {
            val channelEnd = Math.min(fileChannel.size().toInt(), end)
            s = AtomicInteger(channelEnd - start)
            fileChannel.position(channelEnd.toLong())
        }
        currentSize = s
    }

    fun append(byteBufferOperationSet: ByteBufferOperationSet) {
        val written = byteBufferOperationSet.writeFullyTo(fileChannel)
        currentSize.getAndAdd(written)
    }

    /**
     * Return a message set which is a view into this set starting from the given position and with the given size limit.
     *
     * If the size is beyond the end of the file, the end will be based on the size of the file at the time of the read.
     * If this message set is already sliced, the position will be taken relative to that slicing.
     *
     * @param position The start position to begin the read from
     * @param size The number of bytes after the start position to include
     *
     * @return A sliced wrapper on this message set limited based on the given position and size
     */
    fun read(position: Int, size: Int): FileOperationSet {
        require(position >= 0) { "Invalid position: $position" }
        require(size >= 0) { "Invalid size: $size" }
        return FileOperationSet(file,
                fileChannel,
                start + position,
                min(start + position + size, sizeInBytes()))
    }

    /**
     * From starting position, find the first offset larger than target
     *
     * return null if not found
     */
    fun searchFor(targetOffset: Long, startingPosition: Int): OffsetAndPosition? {
        var position = startingPosition
        val buffer = ByteBuffer.allocate(LogOverhead)
        val size: Int = this.sizeInBytes()

        while (position + LogOverhead < size) {
            buffer.rewind()
            fileChannel.read(buffer, position.toLong())
            check(!buffer.hasRemaining()) { "Failed to read complete buffer for targetOffset $targetOffset startPosition $startingPosition in ${file.absolutePath}" }
            buffer.rewind()
            val offset = buffer.long
            if (offset >= targetOffset) {
                return OffsetAndPosition(offset, position)
            }
            val messageSize = buffer.int
            check(messageSize >= MetaStruct.MinMessageOverhead) { "Invalid message size: $messageSize" }
            position += LogOverhead + messageSize
        }
        return null
    }

    fun truncateTo(targetSize: Int): Int {
        val originalSize: Int = this.sizeInBytes()
        if (targetSize > originalSize || targetSize < 0) {
            throw LogException("try truncate log file to  $targetSize  bytes but failed, the file original size is $originalSize bytes")
        }
        try {
            if (targetSize < fileChannel.size()) {
                fileChannel.truncate(targetSize.toLong())
                fileChannel.position(targetSize.toLong())
                currentSize.set(targetSize)
            }
        } catch (e: IOException) {
            throw LogException("try truncate log file to  $targetSize  bytes but failed, the file original size is $originalSize bytes, may cause by ${e.message}")
        }
        return originalSize - targetSize
    }

    /**
     * The number of bytes taken up by this file set
     */
    fun sizeInBytes(): Int {
        return currentSize.get()
    }

    /**
     * Write some of this set to the given channel.
     *
     * @param channel The channel to write to.
     * @param writePosition The position in the message set to begin writing from.
     * @param size The maximum number of bytes to write
     *
     * @return The number of bytes actually written.
     */
    override fun writeTo(channel: GatheringByteChannel, writePosition: Long, size: Int): Int {
        val newSize = min(fileChannel.size().toInt(), end) - start
        val currentSize = currentSize.get()
        if (newSize < currentSize) {
            throw LogException(String.format("Can't write the file {${file.absolutePath}} into channel, new size $newSize is smaller than current size $currentSize."))
        }
        val position = start + writePosition.toInt() // The position in the message set to begin writing from.
        val count = min(size, sizeInBytes())



        return fileChannel.transferTo(position.toLong(), count.toLong(), channel).toInt()
    }

    override fun iterator(): Iterator<OperationAndOffset?>? {
        return this.iterator(Int.MAX_VALUE)
    }

    /**
     * 获取某个文件的迭代器
     */
    fun iterator(maxMessageSize: Int): Iterator<OperationAndOffset<*>> {
        return object : IteratorTemplate<OperationAndOffset<*>>() {
            private var location = start
            private val sizeOffsetBuffer = ByteBuffer.allocate(LogOverhead)
            override fun makeNext(): OperationAndOffset<*>? {
                if (location + LogOverhead >= end) { // 如果已经到了末尾，返回空
                    return allDone()
                }

                // read the size of the item
                sizeOffsetBuffer.rewind()
                try {
                    fileChannel.read(sizeOffsetBuffer, location.toLong())
                } catch (e: IOException) {
                    throw LogException("Error occurred while reading data from fileChannel.")
                }
                if (sizeOffsetBuffer.hasRemaining()) {
                    return allDone()
                }
                sizeOffsetBuffer.flip()
                val offset = sizeOffsetBuffer.long
                val size = sizeOffsetBuffer.int
                if (size < MetaStruct.MinMessageOverhead || location + LogOverhead + size > end) {
                    return allDone()
                }
                if (size > maxMessageSize) {
                    throw LogException(String.format("Message size exceeds the largest allowable message size (%d).", maxMessageSize))
                }

                // read the item itself
                val buffer = ByteBuffer.allocate(size)
                try {
                    fileChannel.read(buffer, location + LogOverhead.toLong())
                } catch (e: IOException) {
                    throw LogException("Error occurred while reading data from fileChannel.")
                }
                if (buffer.hasRemaining()) { // 代表没读完，其实和上面一样，可能是消息写到最后写不下了，或者被截取截没了
                    return allDone()
                }
                buffer.rewind()

                // increment the location and return the item
                location += size + LogOverhead
                return OperationAndOffset(MetaStruct(buffer), offset)
            }
        }
    }


    fun trim() {
        truncateTo(sizeInBytes())
    }

    fun flush() {
        try {
            fileChannel.force(true)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getFileChannel(): FileChannel? {
        return fileChannel
    }
}