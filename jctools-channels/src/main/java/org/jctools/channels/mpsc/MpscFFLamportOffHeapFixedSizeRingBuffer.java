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
package org.jctools.channels.mpsc;

import java.nio.ByteBuffer;

import org.jctools.channels.OffHeapFixedMessageSizeRingBuffer;

import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeDirectByteBuffer.allocateAlignedByteBuffer;

/**
 * A Multi-Producer-Single-Consumer ring buffer. This implies that
 * any thread may call the write methods, but only a single thread may call reads for correctness to
 * maintained. <br>
 * This implementation follows patterns for False Sharing protection.<br>
 * This implementation is using the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * method for polling from the queue (with minor change to correctly publish the index) and an extension of
 * the Leslie Lamport concurrent queue algorithm (originated by Martin Thompson) on the producer side.<br>
 **/
public final class MpscFFLamportOffHeapFixedSizeRingBuffer extends OffHeapFixedMessageSizeRingBuffer {

   public MpscFFLamportOffHeapFixedSizeRingBuffer(final int capacity, final int primitiveMessageSize, final int referenceMessageSize) {
      this(allocateAlignedByteBuffer(getRequiredBufferSize(capacity, primitiveMessageSize), PortableJvmInfo.CACHE_LINE_SIZE), Pow2.roundToPowerOfTwo(capacity), true, true, true, primitiveMessageSize, createReferenceArray(capacity, referenceMessageSize), referenceMessageSize);
   }

   private final long consumerIndexCacheAddress;

   /**
    * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
    *
    * @param buff
    * @param capacity
    */
   protected MpscFFLamportOffHeapFixedSizeRingBuffer(final ByteBuffer buff,
                                                     final int capacity,
                                                     final boolean isProducer,
                                                     final boolean isConsumer,
                                                     final boolean initialize,
                                                     final int primitiveMessageSize,
                                                     final Object[] references,
                                                     final int referenceMessageSize) {
      super(buff, capacity, isProducer, isConsumer, initialize, primitiveMessageSize, references, referenceMessageSize);
      // Layout of the RingBuffer (assuming 64b cache line):
      // consumerIndex(8b), pad(56b) |
      // pad(64b) |
      // producerIndex(8b), consumerIndexCache(8b), pad(48b) |
      // pad(64b) |
      // buffer (capacity * slotSize)
      this.consumerIndexCacheAddress = this.producerIndexAddress + 8;
   }

   private long slowPathWriteAcquire(final long wrapPoint) {
      final long currHead = lvConsumerIndex(); // LoadLoad
      if (currHead <= wrapPoint) {
         return EOF; // FULL :(
      }
      else {
         // update cached value of the consumerIndex
         spConsumerIndexCache(currHead);
         return currHead;
      }
   }

   @Override
   protected final long writeAcquire() {
      final long mask = this.mask;
      final long capacity = mask + 1;
      long consumerIndexCache = lpConsumerIndexCache();
      long currentProducerIndex;
      do {
         currentProducerIndex = lvProducerIndex(); // LoadLoad
         final long wrapPoint = currentProducerIndex - capacity;
         if (consumerIndexCache <= wrapPoint) {
            // update on stack copy, we might need this value again if we lose the CAS.
            consumerIndexCache = slowPathWriteAcquire(wrapPoint);
            if (consumerIndexCache == EOF) {
               return EOF;
            }
         }
      } while (!casProducerIndex(currentProducerIndex, currentProducerIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to read it, we would need to handle the case where the element is not visible.
         */
      // Won CAS, move on to storing
      final long offsetForIndex = offsetForIndex(currentProducerIndex);
      // return offset for current producer index
      return offsetForIndex;
   }

   @Override
   protected final void writeRelease(long offset) {
      //Store-Store: ensure publishing for the consumer - only one single writer per offset
      writeReleaseState(offset);
   }

   @Override
   protected final void writeRelease(long offset, int callTypeId) {
       UNSAFE.putOrderedInt(null, offset, callTypeId);
   }

   @Override
   protected final long readAcquire() {
      final long consumerIndex = lpConsumerIndex();
      final long offset = offsetForIndex(consumerIndex);
      // If we can't see the next available element we can't poll
      if (isReadReleased(offset)) { // LoadLoad
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
         if (consumerIndex != lvProducerIndex()) {
            while (isReadReleased(offset)) {
               //NOP
            }
         }
         else {
            return EOF;
         }
      }
      return offset;
   }

   @Override
   protected final void readRelease(long offset) {
      //it will not used by producer hence it can be a plain store
      spReadReleaseState(offset);
      //retrieve the old stored consumer index
      //TO_TEST: on-heap variable vs off-heap
      final long consumerIndex = lpConsumerIndex();
      //ensure visibility of the new index AFTER writing on the slot!!!
      soConsumerIndex(consumerIndex + 1); // StoreStore
   }

   private boolean casProducerIndex(final long expected, long update) {
      return UNSAFE.compareAndSwapLong(null, producerIndexAddress, expected, update);
   }

   private long lpConsumerIndexCache() {
      return UNSAFE.getLong(null, consumerIndexCacheAddress);
   }

   private void spConsumerIndexCache(final long value) {
      UNSAFE.putLong(null, consumerIndexCacheAddress, value);
   }

   private static void spReadReleaseState(final long offset) {
      UNSAFE.putInt(null, offset, READ_RELEASE_INDICATOR);
   }

}