package ink.anur.log.common

import ink.anur.log.log.LogOffsetMetadata
import ink.anur.log.operationset.FileOperationSet

/**
 * Created by Anur IjuoKaruKas on 2019/7/12
 */
class FetchDataInfo(

    /**
     *  A log offset structure, including:
     * 1. the generation
     * 2. the message offset
     * 3. the base message offset of the located segment
     * 4. the physical position on the located segment
     */
    val fetchMeta: LogOffsetMetadata,

        /**
     * the GAO file in
     */
    val fos: FileOperationSet)