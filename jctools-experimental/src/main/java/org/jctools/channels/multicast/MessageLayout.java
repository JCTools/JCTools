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

import org.jctools.util.Pow2;

import static org.jctools.util.UnsafeAccess.UNSAFE;

final class MessageLayout {

   public static final int SEQUENCE_INDICATOR_SIZE = 8;
   public static final int SEQUENCE_INDICATOR_OFFSET = 0;

   private MessageLayout() {

   }

   public static long calcSequenceOffset(long bufferAddress, long index, long mask, int slotSize) {
      return bufferAddress + ((index & mask) * slotSize);
   }

   public static void spSequence(long offset, long e) {
      UNSAFE.putLong(null, offset, e);
   }

   public static void soSequence(long offset, long e) {
      UNSAFE.putOrderedLong(null, offset, e);
   }

   public static long lvSequence(long offset) {
      return UNSAFE.getLongVolatile(null, offset);
   }

   public static long lpSequence(long offset) {
      return UNSAFE.getLong(null, offset);
   }

   public static int slotSize(int messageSize) {
      return (int) Pow2.align(messageSize + SEQUENCE_INDICATOR_SIZE, SEQUENCE_INDICATOR_SIZE);
   }

}
