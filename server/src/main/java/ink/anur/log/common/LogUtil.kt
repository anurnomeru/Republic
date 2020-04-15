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

package ink.anur.log.common

import java.io.File
import java.text.NumberFormat

/**
 * Created by Anur IjuoKaruKas on 2019/7/12
 *
 * log 文件相关工具类以及静态变量
 */
class LogUtil {

    companion object {
        /** a log file  */
        val LogFileSuffix = ".log"

        /** an index file  */
        val IndexFileSuffix = ".index"

        /** A temporary file used when swapping files into the log  */
        val SwapFileSuffix = ".swap"

        /**
         * Construct a log file name in the given dir with the given base offset
         *
         * @param dir The directory in which the log will reside
         * @param offset The base offset of the log file
         */
        fun logFilename(dir: File, offset: Long): File {
            return File(dir, filenamePrefixFromOffset(offset) + LogFileSuffix)
        }

        /**
         * Construct an index file name in the given dir using the given base offset
         *
         * @param dir The directory in which the log will reside
         * @param offset The base offset of the log file
         */
        fun indexFilename(dir: File, offset: Long): File {
            return File(dir, filenamePrefixFromOffset(offset) + IndexFileSuffix)
        }

        fun dirName(baseDir: File, generation: Long): File {
            return File(baseDir.toString() + "/" + filenamePrefixFromOffset(generation))
        }

        /**
         * Make log segment file name from offset bytes. All this does is pad out the offset number with zeros
         * so that ls sorts the files numerically.
         *
         * @param offset The offset to use in the file name
         *
         * @return The filename
         */
        fun filenamePrefixFromOffset(offset: Long): String {
            val nf = NumberFormat.getInstance()
            nf.minimumIntegerDigits = 20
            nf.maximumFractionDigits = 0
            nf.isGroupingUsed = false
            return nf.format(offset)
        }

    }
}