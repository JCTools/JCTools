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

import static org.jctools.util.UnsafeAccess.UNSAFE;

// Layout of the Tailer statistics (assuming 64b cache line):
// listenedSequence(8b), pad(56b) |
// pad(64b)
// lost(8b), pad(56b) |
// pad(64b)
// dummy(8b), pad(56b) |
// pad(64b)
final class TailerLayout {

   public static final int LISTENED_OFFSET = 0;
   public static final int LISTENED_SIZE = 8;
   public static final int LOST_OFFSET = JvmInfo.CACHE_LINE_SIZE * 2;
   public static final int LOST_SIZE = 8;
   public static final int HEADER_SIZE = JvmInfo.CACHE_LINE_SIZE * 4;

   private TailerLayout() {
   }

   public static long listenedAddress(long bufferAddress) {
      return bufferAddress + LISTENED_OFFSET;
   }

   public static long lostAddress(long bufferAddress) {
      return bufferAddress + LOST_OFFSET;
   }

   public static long lpListened(long listenedAddress) {
      return UNSAFE.getLong(null, listenedAddress);
   }

   public static void spListened(long listenedAddress, long value) {
      UNSAFE.putLong(null, listenedAddress, value);
   }

   public static long lpLost(long lostAddress) {
      return UNSAFE.getLong(null, lostAddress);
   }

   public static long lvLost(long lostAddress) {
      return UNSAFE.getLongVolatile(null, lostAddress);
   }

   public static void soLost(long lostAddress, long value) {
      UNSAFE.putOrderedLong(null, lostAddress, value);
   }

}
