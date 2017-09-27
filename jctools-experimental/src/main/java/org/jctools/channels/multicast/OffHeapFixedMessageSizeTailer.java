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

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

import org.jctools.util.JvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeDirectByteBuffer;

import static org.jctools.util.UnsafeAccess.UNSAFE;

final class OffHeapFixedMessageSizeTailer {

   public static long EOF = -1;
   public static long LOST = -2;
   private final long mask;
   private final int capacity;
   private final int messageSize;
   private final int slotSize;
   private final ByteBuffer buffer;
   private final long producerIndexAddress;
   private final long bufferAddress;
   private final ByteBuffer messageCopy;
   private final long messageCopyAddress;
   private final long listenedAddress;
   private final long lostAddress;

   /**
    * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
    *
    * @param buffer
    * @param capacity    in messages, actual capacity will be
    * @param messageSize
    */
   protected OffHeapFixedMessageSizeTailer(final ByteBuffer buffer, final int capacity, final int messageSize) {
      final int requiredCapacity = ChannelLayout.getRequiredBufferSize(capacity, messageSize);
      if (buffer.capacity() < requiredCapacity) {
         throw new IllegalStateException("buffer is not big enough; required capacity is " + requiredCapacity + " bytes!");
      }
      this.bufferAddress = UnsafeDirectByteBuffer.getAddress(buffer);
      this.messageSize = messageSize;
      this.slotSize = MessageLayout.slotSize(messageSize);
      this.capacity = Pow2.roundToPowerOfTwo(capacity);
      this.mask = this.capacity - 1;
      final int messageContentBytes = ChannelLayout.messageContentBytes(capacity, messageSize);
      this.producerIndexAddress = ChannelLayout.producerIndexAddress(this.bufferAddress, messageContentBytes);
      this.buffer = buffer;
      final int messageCopySize = (int) Pow2.align(messageSize, JvmInfo.CACHE_LINE_SIZE * 2);
      this.messageCopy = UnsafeDirectByteBuffer.allocateAlignedByteBuffer(messageCopySize + TailerLayout.HEADER_SIZE, JvmInfo.CACHE_LINE_SIZE);
      this.messageCopyAddress = UnsafeDirectByteBuffer.getAddress(messageCopy);
      final long endOfMesssageCopyAddress = messageCopyAddress + messageCopySize;
      this.listenedAddress = TailerLayout.listenedAddress(endOfMesssageCopyAddress);
      this.lostAddress = TailerLayout.lostAddress(endOfMesssageCopyAddress);
   }

   private static long chase(long listenedSequence, long sequence, long lost, long lostAddress, long listenedAddress) {
      final long oldListenedSequence = listenedSequence;
      //if is claimed -> previous sequence is ok
      //if is committed -> current sequence is ok
      listenedSequence = sequence - 1;
      final long lostElements = listenedSequence - oldListenedSequence;
      lost += lostElements;
      TailerLayout.soLost(lostAddress, lost);
      TailerLayout.spListened(listenedAddress, listenedSequence);
      return listenedSequence;
   }

   public ByteBuffer buffer() {
      return buffer;
   }

   public int capacity() {
      return this.capacity;
   }

   public long messageCopyAddress() {
      return this.messageCopyAddress;
   }

   public int read(LongConsumer consumer, int limit) {
      final long mask = this.mask;
      final long bufferAddress = this.bufferAddress;
      final int slotSize = this.slotSize;
      final long listenedAddress = this.listenedAddress;
      final long lostAddress = this.lostAddress;
      final long messageCopyAddress = this.messageCopyAddress;
      final long messageSize = this.messageSize;
      long listenedSequence = TailerLayout.lpListened(listenedAddress);
      final long lost = TailerLayout.lpLost(lostAddress);

      for (int i = 0; i < limit; i++) {
         final long sequenceOffset = MessageLayout.calcSequenceOffset(bufferAddress, listenedSequence, mask, slotSize);
         long sequence = MessageLayout.lvSequence(sequenceOffset);
         //LoadLoad + LoadStore
         final long expectedCommittedSequence = listenedSequence + 1;
         if (sequence == expectedCommittedSequence) {
            //listened sequence is committed, can bulk copy the message
            final long elementOffset = sequenceOffset + MessageLayout.SEQUENCE_INDICATOR_SIZE;
            //this content read before the explicit fence will act as a read-acquire that allows the last sequence's intent to leak and be read atomically, if changed
            UNSAFE.copyMemory(elementOffset, messageCopyAddress, messageSize);
            UNSAFE.loadFence();
            //LoadLoad + LoadStore
            sequence = MessageLayout.lvSequence(sequenceOffset);
            //LoadLoad + LoadStore
            //still valid?
            if (sequence == expectedCommittedSequence) {
               //is valid: the first read-acquire on the sequence has copied a valid content of the message
               consumer.accept(messageCopyAddress);
               listenedSequence++;
            } else {
               chase(listenedSequence, sequence, lost, lostAddress, listenedAddress);
               return i;
            }
         } else {
            if (sequence > expectedCommittedSequence) {
               chase(listenedSequence, sequence, lost, lostAddress, listenedAddress);
               return i;
            } else {
               TailerLayout.spListened(listenedAddress, listenedSequence);
               //empty!
               return i;
            }
         }
      }
      //no loss is experienced!
      TailerLayout.spListened(listenedAddress, listenedSequence);
      return limit;

   }

   public long readAcquire() {
      final long mask = this.mask;
      final long bufferAddress = this.bufferAddress;
      final int slotSize = this.slotSize;
      final long listenedAddress = this.listenedAddress;
      final long lostAddress = this.lostAddress;
      final long messageCopyAddress = this.messageCopyAddress;
      final long messageSize = this.messageSize;

      final long listenedSequence = TailerLayout.lpListened(listenedAddress);
      long lost = TailerLayout.lpLost(lostAddress);

      final long sequenceOffset = MessageLayout.calcSequenceOffset(bufferAddress, listenedSequence, mask, slotSize);
      long sequence = MessageLayout.lvSequence(sequenceOffset);
      //LoadLoad + LoadStore
      final long expectedCommittedSequence = listenedSequence + 1;
      if (sequence == expectedCommittedSequence) {
         //listened sequence is committed, can bulk copy the message
         final long elementOffset = sequenceOffset + MessageLayout.SEQUENCE_INDICATOR_SIZE;
         //this content read before the explicit fence will act as a read-racquire that allows the last sequence's intent to leak and be read atomically, if changed
         UNSAFE.copyMemory(elementOffset, messageCopyAddress, messageSize);
         UNSAFE.loadFence();
         //LoadLoad + LoadStore
         sequence = MessageLayout.lvSequence(sequenceOffset);
         //LoadLoad + LoadStore
         //still valid?
         if (sequence == expectedCommittedSequence) {
            //is valid: the first read-acquire on the sequence has copied a valid content of the message
            return listenedSequence;
         } else {
            //lost experienced!
            chase(listenedSequence, sequence, lost, lostAddress, listenedAddress);
            return LOST;
         }
      } else {
         if (sequence > expectedCommittedSequence) {
            //lost experienced!
            chase(listenedSequence, sequence, lost, lostAddress, listenedAddress);
            return LOST;
         } else {
            //empty!
            return EOF;
         }
      }
   }

   public void readRelease(long listenedSequence) {
      final long listenedAddress = this.listenedAddress;
      final long nextListenedSequence = listenedSequence + 1;
      TailerLayout.spListened(listenedAddress, nextListenedSequence);
   }

   public long chase() {
      final long producerIndexAddress = this.producerIndexAddress;
      final long listenedAddress = this.listenedAddress;
      final long lostAddress = this.lostAddress;

      final long sequenceToChase = Math.max(0, ChannelLayout.lvCommittedSequence(producerIndexAddress) - 1);
      final long lostSequence = sequenceToChase - TailerLayout.lpListened(listenedAddress);
      TailerLayout.spListened(listenedAddress, sequenceToChase);
      final long oldLost = TailerLayout.lpLost(lostAddress);
      TailerLayout.soLost(lostAddress, oldLost + lostSequence);
      return lostSequence;
   }

   public long lost() {
      return TailerLayout.lvLost(this.lostAddress);
   }
}
