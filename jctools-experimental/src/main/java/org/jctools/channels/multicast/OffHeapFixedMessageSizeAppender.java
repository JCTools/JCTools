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

import org.jctools.util.Pow2;
import org.jctools.util.UnsafeDirectByteBuffer;

import static org.jctools.util.UnsafeAccess.UNSAFE;

final class OffHeapFixedMessageSizeAppender {

   private final long mask;
   private final int capacity;
   private final int slotSize;
   private final ByteBuffer buffer;
   private final long producerIndexAddress;
   private final long bufferAddress;

   /**
    * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
    *
    * @param buffer
    * @param capacity    in messages, actual capacity will be
    * @param messageSize
    */
   protected OffHeapFixedMessageSizeAppender(final ByteBuffer buffer, final int capacity, final int messageSize) {
      final int requiredCapacity = ChannelLayout.getRequiredBufferSize(capacity, messageSize);
      if (buffer.capacity() < requiredCapacity) {
         throw new IllegalStateException("buffer is not big enough; required capacity is " + requiredCapacity + " bytes!");
      }
      this.bufferAddress = UnsafeDirectByteBuffer.getAddress(buffer);
      this.slotSize = MessageLayout.slotSize(messageSize);
      this.capacity = Pow2.roundToPowerOfTwo(capacity);
      this.mask = this.capacity - 1;
      final int messageContentBytes = ChannelLayout.messageContentBytes(capacity, messageSize);
      this.producerIndexAddress = ChannelLayout.producerIndexAddress(this.bufferAddress, messageContentBytes);
      this.buffer = buffer;
   }

   public ByteBuffer buffer() {
      return buffer;
   }

   public int capacity() {
      return this.capacity;
   }

   public long shouts() {
      return ChannelLayout.lvCommittedSequence(this.producerIndexAddress);
   }

   public long messageOffset(long sequence) {
      return MessageLayout.calcSequenceOffset(bufferAddress, sequence, mask, slotSize) + MessageLayout.SEQUENCE_INDICATOR_SIZE;
   }

   public long writeAcquire() {
      final long mask = this.mask;
      final long producerIndexAddress = this.producerIndexAddress;
      final long bufferAddress = this.bufferAddress;
      final int slotSize = this.slotSize;

      final long sequence = ChannelLayout.lpCommittedSequence(producerIndexAddress);
      //publish sequence's intent
      final long sequenceOffset = MessageLayout.calcSequenceOffset(bufferAddress, sequence, mask, slotSize);
      MessageLayout.spSequence(sequenceOffset, sequence);
      UNSAFE.storeFence();
      //StoreStore + LoadStore
      //any message content written after the explicit fence will act as a write-release that will publish the sequence's intent too.
      //A tailer can use this "leak" to recognize if a read-acquired content is no longer valid by checking that the leaked value of the sequence is not changed
      //from the first value read (that was previously written in the writeRelease phase)
      return sequence;
   }

   public void writeRelease(final long sequence) {
      final long producerIndexAddress = this.producerIndexAddress;
      final long sequenceOffset = MessageLayout.calcSequenceOffset(this.bufferAddress, sequence, this.mask, this.slotSize);
      final long nextSequence = sequence + 1;
      //StoreStore + LoadStore
      //the write-release of the sequence allows a consumer that perform a read-acquire of it to read safely the message content too,
      //until a new intent will change this same sequence value.
      MessageLayout.soSequence(sequenceOffset, nextSequence);
      //StoreStore + LoadStore
      ChannelLayout.soCommittedSequence(producerIndexAddress, nextSequence);
   }
}
