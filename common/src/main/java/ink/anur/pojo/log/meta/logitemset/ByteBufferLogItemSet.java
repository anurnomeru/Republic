/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ink.anur.pojo.log.meta.logitemset;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;
import ink.anur.exception.LogException;
import ink.anur.pojo.log.LogItem;
import ink.anur.pojo.log.meta.LogItemAndOffset;
import ink.anur.util.IteratorTemplate;

/**
 * Created by Anur IjuoKaruKas on 2/25/2019
 *
 * 仿照 Kafka ByteBufferMessageSet 所写
 */
public class ByteBufferLogItemSet extends LogItemSet {

    private ByteBuffer byteBuffer;

    /**
     * 一个日志将要被append到日志之前，需要进行的操作
     */
    public ByteBufferLogItemSet(LogItem logItem, long offset) {
        int size = logItem.size();

        ByteBuffer byteBuffer = ByteBuffer.allocate(size + LogOverhead);
        byteBuffer.putLong(offset);
        byteBuffer.putInt(size);
        byteBuffer.put(logItem.getByteBuffer());

        byteBuffer.rewind();
        this.byteBuffer = byteBuffer;
    }

    public ByteBufferLogItemSet(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public ByteBufferLogItemSet(Collection<LogItemAndOffset> logItemAndOffsets) {
        this.byteBuffer = create(logItemAndOffsets);
    }

    public int writeFullyTo(GatheringByteChannel gatheringByteChannel) throws IOException {
        byteBuffer.mark();
        int written = 0;
        while (written < sizeInBytes()) {
            written += gatheringByteChannel.write(byteBuffer);
        }
        byteBuffer.reset();// mark和reset，为了将byteBuffer的指针还原到写之前的位置
        return written;
    }

    @Override
    public int writeTo(GatheringByteChannel channel, long offset, int maxSize) throws IOException {
        if (offset > Integer.MAX_VALUE) {
            throw new LogException("offset 不应大于 Integer.MaxValue");
        }
        ByteBuffer dup = byteBuffer.duplicate();
        int pos = (int) offset;
        dup.position(pos);
        dup.limit(Math.min(dup.limit(), pos + maxSize));
        return channel.write(dup);
    }

    /**
     * Returns this buffer's limit.
     */
    public int sizeInBytes() {
        return byteBuffer.limit();
    }

    @Override
    public Iterator<LogItemAndOffset> iterator() {
        return new IteratorTemplate<LogItemAndOffset>() {

            private int location = byteBuffer.position();

            @Override
            protected LogItemAndOffset makeNext() {

                if (location + LogOverhead >= sizeInBytes()) {// 如果已经到了末尾，返回空
                    return allDone();
                }

                long offset = byteBuffer.getLong(location);
                int size = byteBuffer.getInt(location + OffsetLength);

                if (location + OffsetLength + size > sizeInBytes()) {
                    return allDone();
                }

                int limitTmp = byteBuffer.limit();
                byteBuffer.position(location + LogOverhead);
                byteBuffer.limit(location + LogOverhead + size);
                ByteBuffer logItem = byteBuffer.slice();
                byteBuffer.limit(limitTmp);

                location += LogOverhead + size;

                return new LogItemAndOffset(new LogItem(logItem), offset);
            }
        };
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public static Stream<ByteBufferLogItemSet> cast(Collection<LogItemAndOffset> logItemAndOffsets) {
        return logItemAndOffsets.parallelStream()
                         .map(oao -> {
                             int allocate = oao.getLogItem()
                                               .totalSize();
                             ByteBuffer byteBuffer = ByteBuffer.allocate(allocate + LogOverhead);
                             byteBuffer.putLong(oao.getOffset());
                             byteBuffer.putInt(oao.getLogItem()
                                                  .totalSize());
                             byteBuffer.put(oao.getLogItem()
                                               .getByteBuffer());
                             byteBuffer.flip();
                             return byteBuffer;
                         })
                         .map(ByteBufferLogItemSet::new);
    }

    private static ByteBuffer create(Collection<LogItemAndOffset> logItemAndOffsets) {
        int count = logItemAndOffsets.size();
        int needToAllocate = logItemAndOffsets.stream()
                                       .map(logItemAndOffset -> logItemAndOffset.getLogItem()
                                                                                .totalSize())
                                       .reduce(Integer::sum)
                                       .orElse(0);

        ByteBuffer byteBuffer = ByteBuffer.allocate(needToAllocate + count * LogOverhead);
        for (LogItemAndOffset logItemAndOffset : logItemAndOffsets) {
            byteBuffer.putLong(logItemAndOffset.getOffset());
            byteBuffer.putInt(logItemAndOffset.getLogItem()
                                       .totalSize());
            byteBuffer.put(logItemAndOffset.getLogItem()
                                    .getByteBuffer());
        }

        byteBuffer.flip();
        return byteBuffer;
    }
}


