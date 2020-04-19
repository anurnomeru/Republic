package ink.anur.pojo.log.meta

import ink.anur.pojo.log.GenerationAndOffset
import java.io.Serializable
import java.util.SortedSet

/**
 * Created by Anur IjuoKaruKas on 2020/4/19
 */
class RecoveryCompleteMeta(val nowGen: Long, val allGensGao: SortedSet<GenerationAndOffset>) : Serializable