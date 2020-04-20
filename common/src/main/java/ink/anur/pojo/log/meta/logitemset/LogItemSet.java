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

package ink.anur.pojo.log.meta.logitemset;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;
import java.util.Collection;
import java.util.Iterator;
import ink.anur.pojo.log.LogItem;
import ink.anur.pojo.log.meta.LogItemAndOffset;

/**
 * Created by Anur IjuoKaruKas on 2/25/2019
 *
 * ”仿照“Kafka写的日志存储类
 */
public abstract class LogItemSet {

    public static final int OffsetLength = 8;

    public static final int MessageSizeLength = 4;

    public static final int LogOverhead = OffsetLength + MessageSizeLength;

    /**
     * 循环 logItemCollection，得出里面所有 LogItem 在装载完 LogOverhead 后的大小
     * 注意，这里在每个 LogItem 的前面还预留了 LogOverhead 的大小
     */
    public static int messageSetSize(Collection<LogItem> logItemCollection) {
        return logItemCollection.stream()
                                .map(LogItemSet::entrySize)
                                .reduce(Integer::sum)
                                .orElse(0);
    }

    /**
     * The size of a size-delimited entry in a logItemSet
     */
    public static int entrySize(LogItem logItem) {
        return LogOverhead + logItem.size();
    }

    /**
     * 向指定的channel从某个指定的offset开始，将LogItemSet写入这个channel，
     * 这个方法将返回已经写入的字节
     */
    public abstract int writeTo(GatheringByteChannel channel, long offset, int maxSize) throws IOException;

    /**
     * 返回此LogItemSet内部的迭代器
     */
    public abstract Iterator<LogItemAndOffset> iterator();
}
