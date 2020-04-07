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

package ink.anur.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * Created by Anur IjuoKaruKas on 2019/2/27
 */
public class FileIOUtil {

    /**
     * 开启一个文件channel
     *
     * mutable，是否可改变（是否不可读）
     * true 可读可写
     * false 只可读
     */
    public static FileChannel openChannel(File file, boolean mutable) throws FileNotFoundException {
        if (mutable) {
            return new RandomAccessFile(file, "rw").getChannel();
        } else {
            return new FileInputStream(file).getChannel();
        }
    }
}
