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

import ink.anur.log.logitemset.FileLogItemSet

/**
 * Created by Anur IjuoKaruKas on 2019/7/12
 *
 * 在某个日志文件中读取操作日志时用到，
 * 读取某个 GAO 时，会用此类包装结果
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
    val fos: FileLogItemSet)