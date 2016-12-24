/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jctools.channels.multicast;

import org.jctools.util.JvmInfo;
import org.jctools.util.Pow2;

import static org.jctools.util.UnsafeAccess.UNSAFE;

// Layout of the Channel (assuming 64b cache line):
// buffer ((capacity * slotSize) aligned to cache line )
// producerIndex(8b), pad(56b) |
// pad(64b)
// dummy(8b), pad(56b) |
// pad(64b)
final class ChannelLayout {

   public static final int PRODUCER_INDEX_OFFSET = 0;
   public static final int PRODUCER_INDEX_SIZE = 8;
   public static final int HEADER_SIZE = JvmInfo.CACHE_LINE_SIZE * 2;

   private ChannelLayout() {
   }

   public static long producerIndexAddress(long bufferAddress, final int messageContentBytes) {
      return bufferAddress + messageContentBytes;
   }

   public static long lvCommittedSequence(long producerIndexAddress) {
      return UNSAFE.getLongVolatile(null, producerIndexAddress);
   }

   public static long lpCommittedSequence(long producerIndexAddress) {
      return UNSAFE.getLong(null, producerIndexAddress);
   }

   public static void soCommittedSequence(long producerIndexAddress, long value) {
      UNSAFE.putOrderedLong(null, producerIndexAddress, value);
   }

   public static int messageContentBytes(final int capacity, final int messageSize) {
      return (int) Pow2.align(Pow2.roundToPowerOfTwo(capacity) * MessageLayout.slotSize(messageSize), JvmInfo.CACHE_LINE_SIZE);
   }

   public static int getRequiredBufferSize(final int capacity, final int messageSize) {
      return messageContentBytes(capacity, messageSize) + HEADER_SIZE;
   }

}
