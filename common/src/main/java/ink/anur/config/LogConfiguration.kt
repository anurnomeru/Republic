package ink.anur.config

import ink.anur.config.common.ConfigHelper
import ink.anur.config.common.ConfigurationEnum
import ink.anur.inject.NigateBean
import ink.anur.inject.NigateInject

/**
 * Created by Anur IjuoKaruKas on 2019/7/5
 *
 * 日志配置相关读取类
 */
@NigateBean
class LogConfiguration : ConfigHelper() {

    @NigateInject(useLocalFirst = true)
    private lateinit var inetSocketAddressConfiguration: InetConfig

    val projectDir = System.getProperty("user.dir");

    fun relativelyPath(): String = projectDir+getConfig(ConfigurationEnum.LOG_BASE_PATH) { it } as String

    fun getBaseDir(): String = relativelyPath() + "/" + inetSocketAddressConfiguration.getLocalServerName()

    fun getIndexInterval(): Int = getConfig(ConfigurationEnum.LOG_INDEX_INTERVAL) { Integer.valueOf(it) } as Int

    fun getMaxIndexSize(): Int = getConfig(ConfigurationEnum.LOG_MAX_INDEX_SIZE) { Integer.valueOf(it) } as Int

    fun getMaxLogMessageSize(): Int = getConfig(ConfigurationEnum.LOG_MAX_MESSAGE_SIZE) { Integer.valueOf(it) } as Int

    fun getMaxLogSegmentSize(): Int = getConfig(ConfigurationEnum.LOG_MAX_SEGMENT_SIZE) { Integer.valueOf(it) } as Int
}

