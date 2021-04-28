package ink.anur.log.common

import ink.anur.core.raft.GenerationAndOffset
import ink.anur.pojo.metastruct.MetaStruct


/**
 * Created by Anur IjuoKaruKas on 2019/10/11
 */
class OperationAndGAO(val operation: MetaStruct<*>, val GAO: GenerationAndOffset)