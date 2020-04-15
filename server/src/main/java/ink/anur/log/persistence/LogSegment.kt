/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ink.anur.log.persistence

import ink.anur.log.common.FetchDataInfo
import ink.anur.log.common.LogOffsetMetadata
import ink.anur.log.common.LogUtil
import ink.anur.log.common.OffsetAndPosition
import ink.anur.log.index.OffsetIndex
import ink.anur.log.logitemset.ByteBufferLogItemSet
import ink.anur.log.logitemset.FileLogItemSet
import ink.anur.log.logitemset.LogItemSet
import ink.anur.util.CoreUtils
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Created by Anur IjuoKaruKas on 2019/8/6
 *
 * 仿照自 Kafka LogSegment
 */
class LogSegment(val fileLogItemSet: FileLogItemSet,
                 val index: OffsetIndex,
                 val baseOffset: Long,
                 private val indexIntervalBytes: Int) {

    /* the number of bytes since we last added an entry in the offset index */
    private var bytesSinceLastIndexEntry = 0

    /**
     * 常规创建一个日志分片
     */
    @Throws(IOException::class)
    constructor(dir: File, startOffset: Long, indexIntervalBytes: Int, maxIndexSize: Int) :
        this(FileLogItemSet(LogUtil.logFilename(dir, startOffset)),
            OffsetIndex(LogUtil.indexFilename(dir, startOffset),
                startOffset, maxIndexSize),
            startOffset,
            indexIntervalBytes)


    /**
     * 将传入的 ByteBufferLogItemSet 追加到文件之中，offset的值为 messages 的初始offset
     */
    @Throws(IOException::class)
    fun append(offset: Long, messages: ByteBufferLogItemSet) {
        if (messages.sizeInBytes() > 0) {
            // append an entry to the index (if needed)
            // 追加到了一定的容量，添加索引
            if (bytesSinceLastIndexEntry > indexIntervalBytes) {

                // 追加 offset 以及当前文件的 size，也就是写的 position到索引文件中
                index.append(offset, fileLogItemSet.sizeInBytes())
                this.bytesSinceLastIndexEntry = 0
            }
            // 追加消息到 fileLogItemSet 中
            // append the messages
            fileLogItemSet.append(messages)
            this.bytesSinceLastIndexEntry += messages.sizeInBytes()
        }
    }

    /**
     * 找遍整个文件，找到第一个大于等于目标 offset 的地址信息
     */
    @Throws(IOException::class)
    fun translateOffset(offset: Long): OffsetAndPosition? {
        return translateOffset(offset, 0)
    }

    /**
     * 从 startingPosition开始，找到第一个大于等于目标 offset 的地址信息
     */
    @Throws(IOException::class)
    fun translateOffset(offset: Long, startingPosition: Int): OffsetAndPosition? {

        // 找寻小于或者等于传入 offset 的最大 offset 索引，返回这个索引的绝对 offset 和 position
        val offsetAndPosition = index.lookup(offset)

        // 从 startingPosition开始 ，找到第一个大于等于目标offset的物理地址
        return fileLogItemSet.searchFor(offset, max(offsetAndPosition.position, startingPosition))
    }

    fun read(generation: Long, startOffset: Long, maxOffset: Long?, maxSize: Int): FetchDataInfo? {
        return read(generation, startOffset, maxOffset, maxSize, fileLogItemSet.sizeInBytes().toLong())
    }

    /**
     * Read a message set from this segment beginning with the first offset >= startOffset. The message set will include
     * no more than maxSize bytes and will end before maxOffset if a maxOffset is specified.
     *
     * 从这个日志文件中读取一个 message set，读取从 startOffset 开始，如果指定了 maxOffset， 这个 message set 将不会包含大于 maxSize 的数据，
     * 并且在 maxOffset 之前结束。
     *
     * @param startOffset A lower bound on the first offset to include in the message set we read
     * @param maxSize The maximum number of bytes to include in the message set we read
     * @param maxOffset An optional maximum offset for the message set we read
     * @param maxPosition The maximum position in the log segment that should be exposed for read
     *
     * @return The fetched data and the offset metadata of the first message whose offset is >= startOffset,
     * or null if the startOffset is larger than the largest offset in this log
     *
     * 返回获取到的数据以及 第一个 offset 相关的元数据，这个 offset >= startOffset。
     * 如果 startOffset 大于这个日志文件存储的最大的 offset ，将返回 null
     */
    fun read(generation: Long, startOffset: Long, maxOffset: Long?, maxSize: Int, maxPosition: Long): FetchDataInfo? {
        if (maxSize < 0) {
            throw IllegalArgumentException(String.format("Invalid max size for log read (%d)", maxSize))
        }

        val logSize = fileLogItemSet.sizeInBytes() // this may change, need to save a consistent copy
        var startPosition: OffsetAndPosition? = null// 查找第一个大于等于 startOffset 的 Offset 和 Position

        try {
            startPosition = translateOffset(startOffset)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (startPosition == null) {
            return null// 代表 fileLogItemSet 里最大的 offset 也没startOffset大
        }

        val logOffsetMetadata = LogOffsetMetadata(generation, startOffset, this.baseOffset, startPosition.position)

        // if the size is zero, still return a log segment but with zero size
        if (maxSize == 0) {
            return null
        }

        var length = 0

        if (maxOffset == null) {
            // length 取 maxPosition - 第一个大于等于 startOffset 的 Position，最大不超过 maxSize
            length = min(maxPosition - startPosition.position, maxSize.toLong()).toInt()
        } else {
            if (maxOffset < startOffset) {
                return null
            }
            // 查找第一个大于等于 maxOffset 的 Offset 和 Position
            var end: OffsetAndPosition? = null
            try {
                end = translateOffset(maxOffset, startPosition.position)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            val endPosition: Int
            endPosition = end?.position ?: logSize// end最大只能取到logSize

            length = min(min(maxPosition, endPosition.toLong()) - startPosition.position, maxSize.toLong()).toInt()
        }

        var fetchDataInfo: FetchDataInfo? = null
        try {
            fetchDataInfo = FetchDataInfo(logOffsetMetadata, fileLogItemSet.read(startPosition.position, length))
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return fetchDataInfo
    }

    /**
     * 传入 offset 如若有效，则
     * 1、移除大于此 offset 的所有索引
     * 2、移除大于此 offset 的所有操作日志
     */
    @Throws(IOException::class)
    fun truncateTo(offset: Long): Int {
        val offsetAndPosition = translateOffset(offset) ?: return 0

        index.truncateTo(offset)

        // after truncation, reset and allocate more space for the (new currently  active) index
        index.resize(index.maxIndexSize)

        val bytesTruncated = fileLogItemSet.truncateTo(offsetAndPosition.position)

        //        if(log.sizeInBytes == 0){
        //            // kafka存在删除一整个日志文件的情况
        //        }

        bytesSinceLastIndexEntry = 0
        return bytesTruncated
    }

    /**
     * 重建该日志分片的索引文件
     */
    fun recover(maxLogMessageSize: Int): Int {

        // 将日志文件的 position 归 0，删除索引
        index.truncate()

        // 重新建立 mmap 映射，设置 limit 为 maxLogMessageSize（抹除 8 的余数）
        index.resize(index.maxIndexSize)

        var validBytes = 0// 循环到哪个字节了
        var lastIndexEntry = 0// 最后一个索引的字节
        val iter = fileLogItemSet.iterator(maxLogMessageSize)
        while (iter.hasNext()) {
            val logItemAndOffset = iter.next()

            // 校验 CRC
            logItemAndOffset.logItem
                .ensureValid()

            if (validBytes - lastIndexEntry > indexIntervalBytes) {
                // we need to decompress the message, if required, to get the offset of the first uncompressed message
                val startOffset = logItemAndOffset.offset
                index.append(startOffset, validBytes)
                lastIndexEntry = validBytes
            }
            validBytes += (LogItemSet.LogOverhead + logItemAndOffset.logItem
                .size())
        }

        val truncated = fileLogItemSet.sizeInBytes() - validBytes
        fileLogItemSet.truncateTo(validBytes)
        index.trimToValidSize()
        return truncated
    }

    fun size(): Long {
        return fileLogItemSet.sizeInBytes().toLong()
    }

    /**
     * 获取当前日志文件的最后一个 offset
     * 先从索引文件中找到最后一个被记载的 offset
     * 返回从这个 offset 的 position - 文件末尾的所有 日志
     *
     * 然后取最后一个
     */
    fun lastOffset(gen: Long): Long {
        val fetchDataInfo = read(gen, index.lastOffset, null, fileLogItemSet.sizeInBytes())
        return if (fetchDataInfo == null) {
            baseOffset
        } else {
            val iterator = fetchDataInfo.fos.iterator()
            var lastOffset = baseOffset

            while (iterator.hasNext()) {
                lastOffset = (iterator.next()
                    .offset)// 因为文件里存储的是相对 offset
            }
            lastOffset
        }
    }


    /**
     * Flush this log segment to disk
     */
    fun flush() {
        this.fileLogItemSet.flush()
        this.index.flush()
    }

    fun close() {
        CoreUtils.swallow {
            index.close()
        }
        CoreUtils.swallow {
            fileLogItemSet.close()
        }
    }
}