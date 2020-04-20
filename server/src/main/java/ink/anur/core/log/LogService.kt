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

package ink.anur.core.log

import ink.anur.config.LogConfiguration
import ink.anur.core.raft.ElectionMetaService
import ink.anur.core.raft.RaftCenterController
import ink.anur.debug.Debugger
import ink.anur.exception.LogException
import ink.anur.inject.Event
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject
import ink.anur.inject.NigateListener
import ink.anur.inject.NigatePostConstruct
import ink.anur.log.Log
import ink.anur.pojo.log.meta.FetchDataInfo
import ink.anur.pojo.log.GenerationAndOffset
import ink.anur.log.common.LogUtil
import ink.anur.log.persistence.LogSegment
import ink.anur.mutex.ReentrantReadWriteLocker
import ink.anur.pojo.log.LogItem
import java.io.File
import java.io.IOException
import java.util.SortedSet
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.math.max

/**
 * Created by Anur IjuoKaruKas on 2020/3/11
 *
 * 日志中心
 */
@NigateBean
class LogService {

    private val logger = Debugger(this::class.java)

    @NigateInject
    private lateinit var logConfiguration: LogConfiguration

    @NigateInject
    private lateinit var electionMetaService: ElectionMetaService

    @NigateInject
    private lateinit var raftCenterController: RaftCenterController

    /** 显式锁 */
    private val appendLock = ReentrantReadWriteLocker()

    /** 隐式锁 */
    private val explicitLock = ReentrantReadWriteLocker()

    /** 管理所有 Log  */
    private val generationDirs = ConcurrentSkipListMap<Long, Log>()

    /** 基础目录  */
    private lateinit var baseDir: File

    /** 初始化时，最新的 Generation 和 Offset  */
    private lateinit var initial: GenerationAndOffset

    /** 最新的那个 GAO  */
    @Volatile
    private lateinit var currentGAO: GenerationAndOffset

    /** Leader节点比较特殊，在集群不可用以后，要销毁掉集群内未提交的操作日志 */
    @Volatile
    private var isLeaderCurrent: Boolean = false

    @NigatePostConstruct
    private fun init() {
        baseDir = File(logConfiguration.getBaseDir() + "/log/aof/")
        baseDir.mkdirs()

        var latestGeneration = 0L
        var init: GenerationAndOffset

        while (true) {
            for (file in baseDir.listFiles()!!) {
                if (!file.isFile) {
                    latestGeneration = max(latestGeneration, Integer.valueOf(file.name).toLong())
                }
            }

            // 只要创建最新的那个 generation 即可
            try {
                val latest = Log(latestGeneration, createGenDirIfNEX(latestGeneration))
                generationDirs[latestGeneration] = latest

                init = GenerationAndOffset(latestGeneration, latest.currentOffset)

                if (init.generation != 0L && init.offset == 0L) {
                    deleteLog(init.generation, latest)
                    latestGeneration = 0L
                } else {
                    break
                }
            } catch (e: IOException) {
                throw LogException("操作日志初始化失败，项目无法启动")
            }
        }

        logger.info("初始化日志管理器，当前最大进度为 {}", init.toString())

        appendLock.switchOff()

        initial = init
        currentGAO = init
    }

    fun getCurrentGao(): GenerationAndOffset {
        return currentGAO
    }

    fun getAllGensGao(): SortedSet<GenerationAndOffset> {
        val result = ConcurrentSkipListSet<GenerationAndOffset>()
        for (file in baseDir.listFiles()!!) {
            if (!file.isFile) {
                val gen = Integer.valueOf(file.name).toLong()
                val latestOffset = Log(gen, createGenDirIfNEX(gen)).currentOffset
                result.add(GenerationAndOffset(gen, latestOffset))
            }
        }
        return result
    }


//    /**
//     * 恢复 aof
//     */
//    @NigatePostConstruct
//    private fun onBootStrap() {
//        var nowRecovery = GenerationAndOffset(0, -1)
//        while (true) {
//            val after = getAfter(nowRecovery.next()) ?: break
//
//            val generation = after.fetchMeta.generation
//            val iterator = after.fos.iterator()
//            while (iterator.hasNext()) {
//                val next = iterator.next()
//                nowRecovery = GenerationAndOffset(generation, next.offset)// todo 这里写的不好
//            }
//            if (nowRecovery == initial) {
//                break
//            }
//        }
//    }

    /**
     * 当集群可用
     *  - 标记曾经做过 leader
     */
    @NigateListener(onEvent = Event.CLUSTER_VALID)
    private fun onClusterValid() {
        if (electionMetaService.isLeader()) {
            isLeaderCurrent = true
        }
    }

    /**
     * 当集群不可用
     *  - 关闭追加入口
     *  - 必须抛弃未 commit 部分，避免将未提交的数据恢复到集群日志中
     */
    @NigateListener(onEvent = Event.CLUSTER_INVALID)
    private fun onClusterInvalid() {
        appendLock.switchOff()
        logger.info("log-service 追加入口关闭~")
//     * TODO 这里可能不太对
//            if (isLeaderCurrent) {
//                CommitProcessManager.discardInvalidMsg()
//            }
    }

    /**
     * 当集群完成recovery，打开追加入口
     */
    @NigateListener(onEvent = Event.RECOVERY_COMPLETE)
    private fun onRecoveryComplete() {
        appendLock.switchOn()
        logger.info("log-service 追加入口开启~")
    }

    /**
     * 追加操作日志到磁盘，只有当集群可用时，才可以进行追加
     */
    fun appendForLeader(logItem: LogItem): GenerationAndOffset {
        return appendLock.writeLockSupplierCompel {
            return@writeLockSupplierCompel explicitLock.writeLockSupplierCompel {
                currentGAO = raftCenterController.genGenerationAndOffset()
                val log = maybeRoll(currentGAO.generation, true)
                log.append(logItem, currentGAO.offset)
                return@writeLockSupplierCompel currentGAO
            }
        }
    }

    /**
     * 追加操作日志到磁盘，如果集群不可用，不会阻塞，供内部集群恢复时调用
     *
     * 允许插入到以前的世代
     */
    fun append(gen: Long, offset: Long, logItem: LogItem) {
        explicitLock.writeLocker {
            val insertion = GenerationAndOffset(gen, offset)
            if (insertion > currentGAO) {
                currentGAO = insertion
            }

            val log = maybeRoll(gen, false)

            // 追加到磁盘
            log.append(logItem, offset)
        }
    }

    /**
     * 在 append 操作时，如果世代更新了，则创建新的 Log 管理
     */
    private fun maybeRoll(generation: Long, active: Boolean): Log {
        val current = if (active) activeLog() else generationDirs[generation]
        if (current == null || generation > current.generation) {
            val dir = createGenDirIfNEX(generation)
            val log: Log
            try {
                log = Log(generation, dir)
            } catch (e: IOException) {
                throw LogException("创建世代为 $generation 的操作日志管理文件 Log 失败")
            }

            generationDirs[generation] = log
            return log
        } else if (generation < current.generation) {
            throw LogException("不应在添加日志时获取旧世代的 Log")
        }

        return current
    }

    /**
     * 创建世代目录
     */
    fun createGenDirIfNEX(generation: Long): File {
        return LogUtil.dirName(baseDir, generation)
    }

    /**
     * 获取最新的一个日志分片管理类 Log
     */
    fun activeLog(): Log {
        return generationDirs.lastEntry().value
    }

    /**
     * 只返回某个 segment 的往后所有消息，需要客户端轮询拉数据（包括拉取本身这条消息）
     *
     * 先获取符合此世代的首个 Log ，称为 needLoad
     *
     * ==>      循环 needLoad，直到拿到首个有数据的 LogSegment，称为 needToRead
     *
     * 如果拿不到 needToRead，则进行递归
     *
     * @param maxBytes 因为单个日志最大支持500m，一次传输太多，卒了会很蛋疼，所以限制一下大小
     */
    fun getAfter(GAO: GenerationAndOffset, maxBytes: Int = 1024 * 1024 * 10): FetchDataInfo? {
        return explicitLock.readLockSupplier {
            val gen = GAO.generation
            val offset = GAO.offset

            //  GAO 过大直接返回null
            if (GAO > currentGAO) return@readLockSupplier null

            /*
             * 如果不存在此世代，则加载此世代
             */
            if (!generationDirs.containsKey(gen)) {
                loadGenLog(gen)

                /*
                 * 如果还是不存在，则拉取更大世代
                 */
                if (!generationDirs.containsKey(gen)) {
                    return@readLockSupplier getAfter(GenerationAndOffset(gen + 1, 0))
                }
            }

            val tailMap = generationDirs.tailMap(gen, true)
            if (tailMap == null || tailMap.size == 0) {
                //此世代还未有预日志
                return@readLockSupplier null
            }

            // 取其最小者
            val firstEntry = tailMap.firstEntry()

            val needLoadGen = firstEntry.key
            val needLoadLog = firstEntry.value

            val logSegmentIterable =
                needLoadLog.getLogSegments(offset, Long.MAX_VALUE).iterator()

            var needToRead: LogSegment? = null
            while (logSegmentIterable.hasNext()) {
                val tmp = logSegmentIterable.next()
                needToRead = tmp
                break
            }

            return@readLockSupplier if (needToRead == null) {
                getAfter(GenerationAndOffset(needLoadGen + 1, offset))
            } else {
                needToRead.read(needLoadGen, offset, Long.MAX_VALUE, maxBytes) ?: getAfter(GenerationAndOffset(gen + 1, 0))
            }
        }
    }

    /**
     * 如果某世代日志还未初始化，则将其初始化，并加载部分到内存
     */
    @Synchronized
    fun loadGenLog(gen: Long): Log? {
        if (!generationDirs.containsKey(gen) && LogUtil.dirName(baseDir, gen).exists()) {
            generationDirs[gen] = Log(gen, createGenDirIfNEX(gen))
        }
        return generationDirs[gen]
    }

    /**
     * 丢弃某个 GAO 往后的所有消息
     */
    fun discardAfter(GAO: GenerationAndOffset) {
        explicitLock.writeLocker {
            for (i in GAO.generation..currentGAO.generation) {
                loadGenLog(i)
            }

            var result = doDiscardAfter(GAO)
            while (result) {
                result = doDiscardAfter(GAO)
            }
            currentGAO = GAO
        }
    }


    /**
     * 真正丢弃执行者，注意运行中不要乱调用这个，因为没加锁
     */
    private fun doDiscardAfter(GAO: GenerationAndOffset): Boolean {
        val gen = GAO.generation
        val offset = GAO.offset

        val tailMap = generationDirs.tailMap(gen, true)
        if (tailMap == null || tailMap.size == 0) {
            // 世代过大或者此世代还未有预日志
            return false
        }

        // 取其最大者
        val lastEntry = tailMap.lastEntry()

        val needDeleteGen = lastEntry.key
        val needDeleteLog = lastEntry.value

        // 判断是否需要删除整个世代日志
        // 若存在比当前更大的世代的日志，将其全部删除
        // 不会出现删除比当前世代还大的情况
        // 例如当前最大世代 9，但是要删除世代 10 的日志
        val deleteAll = when {
            needDeleteGen == gen -> false
            needDeleteGen > gen -> true
            else -> throw LogException("注意一下这种情况，比较奇怪！")
        }

        return if (deleteAll) {
            val logSegmentIterable =
                needDeleteLog.getLogSegments(0, Long.MAX_VALUE).iterator()

            logger.info("当前需删除 $GAO 往后的日志，故世代 $needDeleteGen 日志将全部删去")
            logSegmentIterable.forEach {
                it.fileLogItemSet.fileChannel.close()
                val needToDelete = it.fileLogItemSet.file
                logger.debug("删除日志分片 ${needToDelete.absoluteFile} "
                    + (if (needToDelete.delete()) "成功" else "失败")
                    + "。删除对应索引文件"
                    + if (it.index.delete()) "成功" else "失败")
            }

            val dir = needDeleteLog.dir
            val success: Boolean = needDeleteLog.dir.delete()
            logger.debug("删除目录 $dir" + if (success) {
                generationDirs.remove(needDeleteGen)
                "成功"
            } else "失败")
            true
        } else {
            logger.info("当前需删除 $GAO 往后的日志，故世代 $needDeleteGen 日志将部分删去")
            val logSegmentIterable =
                needDeleteLog.getLogSegments(offset, Long.MAX_VALUE).iterator()

            logSegmentIterable.forEach {
                if (it.baseOffset <= offset && offset <= it.lastOffset(needDeleteGen)) {
                    it.truncateTo(offset)
                    logger.debug("删除日志分片 ${it.fileLogItemSet.file} 中大于等于 $offset 的记录移除")
                } else {
                    it.fileLogItemSet.fileChannel.close()
                    val needToDelete = it.fileLogItemSet.file
                    logger.debug("删除日志分片 ${needToDelete.absoluteFile}" + if (needToDelete.delete()) "成功" else "失败")
                }
            }
            needDeleteLog.currentOffset = offset
            false
        }
    }

    private fun deleteLog(generation: Long, log: Log) {
        if (log.currentOffset == 0L) {
            val logSegments = log.getLogSegments(0, Long.MAX_VALUE)
            for (logSegment in logSegments) {
                logSegment.fileLogItemSet.fileChannel.close()
                logSegment.fileLogItemSet.file.delete()

                logSegment.index.delete()
            }
            log.dir.delete()
        }
        logger.info("世代 $generation 没有任何日志，将其删除")
    }

    fun getInitialGAO(): GenerationAndOffset {
        return initial
    }
}