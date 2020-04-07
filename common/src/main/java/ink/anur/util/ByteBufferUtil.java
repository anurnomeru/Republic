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

import java.nio.ByteBuffer;

/**
 * Created by Anur IjuoKaruKas on 2019/2/27
 */
public class ByteBufferUtil {

    /**
     * Compute the CRC32 of the byte array
     *
     * @param bytes The array to compute the checksum for
     *
     * @return The CRC32
     */
    public static long crc32(byte[] bytes) {
        return crc32(bytes, 0, bytes.length);
    }

    /**
     * @param offset 从哪一位开始计算offset
     * @param size   从上面开始，计算xx位
     */
    public static long crc32(byte[] bytes, int offset, int size) {
        Crc32 crc = new Crc32();
        crc.update(bytes, offset, size);
        return crc.getValue();
    }

    /**
     * Write the given long value as a 4 byte unsigned integer. Overflow is ignored.
     *
     * @param buffer The buffer to write to
     * @param index  The position in the buffer at which to begin writing
     * @param value  The value to write
     */
    public static void writeUnsignedInt(ByteBuffer buffer, int index, long value) {
        buffer.putInt(index, (int) (value & 0xffffffffL));
    }

    /**
     * Read an unsigned integer from the given position without modifying the buffers position
     *
     * @param buffer the buffer to read from
     * @param index  the index from which to read the integer
     *
     * @return The integer read, as a long to avoid signedness
     */
    public static long readUnsignedInt(ByteBuffer buffer, int index) {
        return buffer.getInt(index) & 0xffffffffL;
    }
}
