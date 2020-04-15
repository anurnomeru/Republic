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

package ink.anur.log;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.collect.Lists;
import ink.anur.config.LogConfiguration;
import ink.anur.debug.Debugger;
import ink.anur.exception.LogException;
import ink.anur.inject.Nigate;
import ink.anur.inject.NigateInject;
import ink.anur.log.common.LogUtil;
import ink.anur.log.logitemset.ByteBufferLogItemSet;
import ink.anur.log.persistence.LogSegment;
import ink.anur.mutex.ReentrantLocker;
import ink.anur.pojo.log.LogItem;

/**
 * Created by Anur IjuoKaruKas on 2019/3/6
 * <p>
 * 对应磁盘上一个 log 的世代目录
 */
public class Log extends ReentrantLocker {

    private static Debugger logger = new Debugger(Log.class);

    @NigateInject
    private LogConfiguration logConfiguration;

    /**
     * 当前目录所处世代
     */
    public final long generation;

    /**
     * 日志文件相关目录，目录以世代命名
     */
    public final File dir;

    /**
     * 管理这个世代下所有的日志分片文件
     */
    private final ConcurrentSkipListMap<Long, LogSegment> segments = new ConcurrentSkipListMap<>();

    /**
     * 上一次进行刷盘的时间
     */
    private final AtomicLong lastFlushedTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 此 offset 之前的数据都已经刷盘
     */
    public long recoveryPoint;

    /**
     * 最近一个添加到 append 到日志文件中的 offset，默认取 baseOffset
     */
    private long currentOffset;

    {
        Nigate.INSTANCE.injectOnly(this);
    }

    public Log(long generation, File dir) throws IOException {
        this.generation = generation;
        this.dir = dir;
        this.currentOffset = load();
        this.recoveryPoint = currentOffset;
    }

    private long load() throws IOException {
        // 如果目录不存在，则创建此目录
        dir.mkdirs();
        logger.info("正在读取日志文件目录 {}", dir.getAbsolutePath());

        for (File file : dir.listFiles()) {
            if (file.isFile()) {

                if (!file.canRead()) {
                    throw new LogException("日志分片文件或索引文件不可读！");
                }

                String filename = file.getName();
                if (filename.endsWith(LogUtil.Companion.getIndexFileSuffix())) {
                    File logFile = new File(file.getAbsolutePath()
                                                .replace(LogUtil.Companion.getIndexFileSuffix(), LogUtil.Companion.getLogFileSuffix()));

                    if (!logFile.exists()) {
                        logger.debug("世代 {} 日志索引文件 {} 被创建了，但并没有创建相应的日志切片文件", generation, filename);
                        file.delete();
                        break;
                    }
                } else if (filename.endsWith(LogUtil.Companion.getLogFileSuffix())) {
                    long start = Long.valueOf(filename.substring(0, filename.length() - LogUtil.Companion.getLogFileSuffix()
                                                                                                         .length()));
                    File indexFile = LogUtil.Companion.indexFilename(dir, start);
                    LogSegment thisSegment;
                    try {
                        thisSegment = new LogSegment(dir, start, logConfiguration.getIndexInterval(), logConfiguration.getMaxIndexSize());
                    } catch (IOException e) {
                        throw new LogException("创建或映射日志分片文件 " + filename + " 失败");
                    }

                    if (indexFile.exists()) {
                        // 检查下这个索引文件有没有大的问题，比如大小不正确等
                        try {
                            thisSegment.getIndex()
                                       .sanityCheck();
                        } catch (Exception e) {
                            logger.info("世代 {} 日志 {} 的索引文件存在异常，正在重建索引文件。", generation, filename);
                            indexFile.delete();
                            long startTs = System.currentTimeMillis();
                            thisSegment.recover(logConfiguration.getMaxLogMessageSize());
                            logger.info("索引重建完毕，耗时 {} ms。", System.currentTimeMillis() - startTs);
                        }
                    } else {
                        logger.info("世代 {} 日志 {} 的索引文件不存在，正在重建索引文件。", generation, filename);
                        thisSegment.recover(logConfiguration.getMaxLogMessageSize());
                    }

                    segments.put(start, thisSegment);
                }
            }
        }

        if (segments.size() == 0) {
            logger.info("当前世代 {} 目录 {} 还未创建任何日志分片，将创建开始下标为 1L 的日志分片", generation, dir.getAbsolutePath());
            segments.put(0L, new LogSegment(dir, 0, logConfiguration.getIndexInterval(), logConfiguration.getMaxIndexSize()));
        }

        return activeSegment().lastOffset(generation);
    }

    /**
     * 最近加入到日志中的那个 offset
     */
    public long getCurrentOffset() {
        return currentOffset;
    }

    public void setCurrentOffset(long currentOffset) {
        this.currentOffset = currentOffset;
    }

    /**
     * 将一个操作添加到日志文件中
     */
    public void append(LogItem logItem, long offset) {
        if (offset < currentOffset) {
            throw new LogException(String.format("一定是哪里有问题，current %s, append %s", currentOffset, offset));
        }

        LogSegment logSegment = maybeRoll(logItem.size());

        ByteBufferLogItemSet byteBufferLogItemSet = new ByteBufferLogItemSet(logItem, offset);
        try {
            // 追加到磁盘
            logSegment.append(offset, byteBufferLogItemSet);
        } catch (IOException e) {
            throw new LogException("写入日志文件失败：" + logItem.toString());
        }

        currentOffset = offset;
    }

    /**
     * 如果要 append 的日志过大，则需要滚动到下一个日志分片，如果滚动到下一个日志分片，则将上一个日志文件刷盘。
     */
    public LogSegment maybeRoll(int size) {
        LogSegment logSegment = activeSegment();

        if (
            logSegment.size() + size > logConfiguration.getMaxLogSegmentSize() // 即将 append 的消息将超过分片容纳最大大小
                || logSegment.getIndex() // 可索引的 index 已经达到最大
                             .isFull()) {
            logger.info("即将开启新的日志分片，上个分片大小为 {}/{}， 对应的索引文件共建立了 {}/{} 个索引。", logSegment.size(), logConfiguration.getMaxLogSegmentSize(),
                logSegment.getIndex()
                          .getEntries(), logSegment.getIndex()
                                                   .getMaxEntries());
            return roll();
        } else {
            return logSegment;
        }
    }

    /**
     * 获取最后一个日志分片文件
     */
    private LogSegment activeSegment() {
        return segments.lastEntry()
                       .getValue();
    }

    /**
     * 滚动到下一个日志分片文件
     */
    private LogSegment roll() {
        return this.lockSupplier(() -> {
            long newOffset = currentOffset + 1;
            File newFile = LogUtil.Companion.logFilename(dir, newOffset);
            File indexFile = LogUtil.Companion.indexFilename(dir, newOffset);

            Lists.newArrayList(newFile, indexFile)
                 .forEach(file -> Optional.of(newFile)
                                          .filter(File::exists)
                                          .ifPresent(f -> {
                                              logger.info("新创建的日志分片或索引竟然已经存在，将其抹除。");
                                              f.delete();
                                          }));

            // 将原日志分片进行 trim 处理
            Optional.ofNullable(segments.lastEntry())
                    .ifPresent(e -> {
                        e.getValue()
                         .getIndex()
                         .trimToValidSize();
                        e.getValue()
                         .getFileLogItemSet()
                         .trim();
                    });

            LogSegment newLogSegment;
            try {
                newLogSegment = new LogSegment(dir, newOffset, logConfiguration.getIndexInterval(), logConfiguration.getMaxIndexSize());
            } catch (IOException e) {
                logger.error("滚动时创建新的日志分片失败，分片目录：{}, 创建的文件为：{}", dir.getAbsolutePath(), newFile.getName());
                throw new LogException("滚动时创建新的日志分片失败");
            }

            if (addSegment(newLogSegment) != null) {
                logger.error("滚动时创建新的日志分片失败，该分片已经存在");
            }

            flush(newOffset);
            return newLogSegment;
        });
    }

    /**
     * 将日志纳入跳表来管理
     */
    private LogSegment addSegment(LogSegment segment) {
        return this.segments.put(segment.getBaseOffset(), segment);
    }

    /**
     * 将消息刷盘
     */
    public void flush(long offset) {
        if (offset <= recoveryPoint) {
            return;
        }

        logger.debug("将世代 {} 日志 {} 刷盘，现刷盘至 offset {}，上次刷盘时间为 {}，现共有 {} 条消息还未刷盘", generation, name(), offset, lastFlushedTime.get(), unFlushedMessages());
        for (LogSegment logSegment : getLogSegments(recoveryPoint, offset)) {
            logSegment.flush();
        }

        lockSupplier(() -> {
            if (offset > this.recoveryPoint) {
                this.recoveryPoint = offset;
                lastFlushedTime.set(System.currentTimeMillis());
            }
            return null;
        });
    }

    public String name() {
        return dir.getName();
    }

    /**
     * 如果这个世代下所有的日志分片文件不存在小于等于 fromOffset 的
     * <p>
     * - 返回从头直到 toOffset 的 LogSegment
     * <p>
     * - 否则返回区间内的 logSegment
     */
    public Iterable<LogSegment> getLogSegments(long fromOffset, long toOffset) {
        return lockSupplier(() -> {

            // 返回的最大键，小于或等于给定的键
            // floorKey(K key) 方法用于返回的最大键小于或等于给定的键，或null，
            Long floor = segments.floorKey(fromOffset);
            if (floor == null) {// 代表 segments 的所有键都大于 fromOffset
                return segments.headMap(toOffset)
                               .values();
            } else {
                return segments.subMap(floor, true, toOffset, false)
                               .values();
            }
        });
    }

    /**
     * 还未完全刷盘的消息数量
     */
    public long unFlushedMessages() {
        return currentOffset - recoveryPoint;
    }

    /**
     * Close this log
     */
    public void close() {

        logger.debug("Closing log " + name());
        lockSupplier(() -> {
            for (LogSegment value : segments.values()) {
                value.close();
            }
            return null;
        });
    }
}
