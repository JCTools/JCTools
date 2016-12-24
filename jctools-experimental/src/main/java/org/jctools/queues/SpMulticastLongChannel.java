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

package org.jctools.queues;

import org.jctools.util.JvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

import static org.jctools.util.UnsafeAccess.UNSAFE;

abstract class SpMulticastLongChannelL0Pad {

   long p01, p02, p03, p04, p05, p06, p07;
   long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class SpMulticastLongChannelSequenceFields extends SpMulticastLongChannelL0Pad {

   private static final long COMMITTED_SEQUENCE_OFFSET;

   static {
      try {
         COMMITTED_SEQUENCE_OFFSET = UNSAFE.objectFieldOffset(SpMulticatChannelSequenceFields.class.getDeclaredField("committedSequence"));
      } catch (NoSuchFieldException e) {
         throw new RuntimeException(e);
      }
   }

   private volatile long committedSequence;

   protected final long lpCommittedSequence() {
      return UNSAFE.getLong(this, COMMITTED_SEQUENCE_OFFSET);
   }

   protected final void soCommittedSequence(long value) {
      UNSAFE.putOrderedLong(this, COMMITTED_SEQUENCE_OFFSET, value);
   }

   protected final long lvCommittedSequence() {
      return UNSAFE.getLongVolatile(this, COMMITTED_SEQUENCE_OFFSET);
   }

}

abstract class SpMulticastLongChannelL1Pad extends SpMulticastLongChannelSequenceFields {

   long p01, p02, p03, p04, p05, p06, p07;
   long p10, p11, p12, p13, p14, p15, p16, p17;
}

/**
 * A Single-Producer channel backed by a pre-allocated buffer.
 * It allows enough fast multiple (0 or more) {@link Listener}s to receive the messages.
 * This is the primitive type specialization of {@link SpMulticastChannel} for long messages.
 */
public final class SpMulticastLongChannel extends SpMulticastLongChannelL1Pad {

   protected static final int SEQ_BUFFER_PAD;
   private static final long ARRAY_BASE;
   private static final int ELEMENT_SHIFT;

   static {
      final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(long[].class);
      if (8 == scale) {
         ELEMENT_SHIFT = 3;
      } else {
         throw new IllegalStateException("Unexpected long[] element size");
      }
      // 2 cache lines pad
      SEQ_BUFFER_PAD = (JvmInfo.CACHE_LINE_SIZE * 2) / scale;
      // Including the buffer pad in the array base offset
      ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(long[].class) + (SEQ_BUFFER_PAD * scale);
   }

   private final long mask;
   private final int capacity;
   private final long[] sequenceBuffer;
   private final long nilValue;

   public SpMulticastLongChannel(int capacity) {
      this(capacity, -1);
   }

   public SpMulticastLongChannel(int capacity, long nilValue) {
      final int actualCapacity = Pow2.roundToPowerOfTwo(capacity * 2);
      this.mask = actualCapacity - 1;
      this.capacity = actualCapacity;
      // pad data on either end with some empty slots.
      this.sequenceBuffer = new long[actualCapacity + SEQ_BUFFER_PAD * 2];
      this.nilValue = nilValue;
   }

   private static long calcSequenceOffset(long index, long mask) {
      return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
   }

   private static final void spSequence(long[] buffer, long offset, long e) {
      UNSAFE.putLong(buffer, offset, e);
   }

   private static final void soSequence(long[] buffer, long offset, long e) {
      UNSAFE.putOrderedLong(buffer, offset, e);
   }

   private static final long lvSequence(long[] buffer, long offset) {
      return UNSAFE.getLongVolatile(buffer, offset);
   }

   private static final long lpSequence(long[] buffer, long offset) {
      return UNSAFE.getLong(buffer, offset);
   }

   /**
    * @return the capacity of this channel
    */
   public int capacity() {
      return this.capacity >> 1;
   }

   /**
    * @return the chosen value to represent a {@code NULL} message
    */
   public long nilValue() {
      return this.nilValue;
   }

   /**
    * The number of elements passed into the channel.
    * This method's accuracy is subject to concurrent modifications happening.
    *
    * @return number of messages thrown in the channel
    */
   public long shouts() {
      return lvCommittedSequence();
   }

   /**
    * Inserts the specified element into this channel.
    * It can be called safely only by one producer thread.
    *
    * @param item not {@link #nilValue()}, will throw IAE if it is
    */
   public void shout(long item) {
      if (item == this.nilValue) {
         throw new IllegalArgumentException("can't shout the nil value!");
      }
      final long[] sequenceBuffer = this.sequenceBuffer;
      final long mask = this.mask;
      final long sequence = lpCommittedSequence();
      final long nextSequence = sequence + 1;

      final long sequenceOffset = calcSequenceOffset(sequence << 1, mask);
      spSequence(sequenceBuffer, sequenceOffset, sequence);
      final long elementOffset = sequenceOffset + 8;
      //StoreStore + LoadStore
      soSequence(sequenceBuffer, elementOffset, item);
      //StoreStore + LoadStore
      soSequence(sequenceBuffer, sequenceOffset, nextSequence);
      //StoreStore + LoadStore
      soCommittedSequence(nextSequence);
   }

   /**
    * Creates an instance of {@link Listener} which can be used for sequential reads from this channel.
    *
    * @return a new {@link Listener} instance
    */
   public Listener newListener() {
      return new Listener(this);
   }

   abstract static class ListenerL0Pad {

      long p01, p02, p03, p04, p05, p06, p07;
      long p10, p11, p12, p13, p14, p15, p16, p17;
   }

   abstract static class ListenerLostField extends ListenerL0Pad {

      private static final long LOST_OFFSET;

      static {
         try {
            LOST_OFFSET = UNSAFE.objectFieldOffset(ListenerLostField.class.getDeclaredField("lost"));
         } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
         }
      }

      private volatile long lost;

      protected final long lpLost() {
         return UNSAFE.getLong(this, LOST_OFFSET);
      }

      protected final long lvLost() {
         return UNSAFE.getLongVolatile(this, LOST_OFFSET);
      }

      protected final void spLost(long value) {
         UNSAFE.putLong(this, LOST_OFFSET, value);
      }

      protected final void soLost(long value) {
         UNSAFE.putOrderedLong(this, LOST_OFFSET, value);
      }

   }

   abstract static class ListenerL1Pad extends ListenerLostField {

      long p01, p02, p03, p04, p05, p06, p07;
      long p10, p11, p12, p13, p14, p15, p16, p17;
   }

   abstract static class ListenerSequenceField extends ListenerL1Pad {

      protected long listenedSequence;

   }

   abstract static class ListenerL2Pad extends ListenerSequenceField {

      long p01, p02, p03, p04, p05, p06, p07;
      long p10, p11, p12, p13, p14, p15, p16, p17;
   }

   /**
    * It allows sequentially reading messages from a {@link SpMulticastLongChannel}.
    * It can join the transmission at any point without slowing down the producer.
    * If it cannot keep up with the transmission loss will be experienced and recorded.
    */
   public static final class Listener extends ListenerL2Pad {

      private final SpMulticastLongChannel channel;
      private final long[] sequenceBuffer;
      private final int capacity;
      private final long mask;
      private final long nilValue;
      private final int channelCapacity;

      private Listener(SpMulticastLongChannel channel) {
         this.channel = channel;
         this.capacity = channel.capacity;
         this.mask = channel.mask;
         this.sequenceBuffer = channel.sequenceBuffer;
         this.nilValue = channel.nilValue;
         this.channelCapacity = channel.capacity();
      }

      private static void chase(long listenedSequence, long sequence, long lost, Listener listener) {
         final long oldListenedSequence = listenedSequence;
         //if is claimed -> previous sequence is ok
         //if is committed -> current sequence is ok
         listenedSequence = sequence - 1;
         final long lostElements = listenedSequence - oldListenedSequence;
         lost += lostElements;
         listener.soLost(lost);
         listener.listenedSequence = listenedSequence;
      }

      /**
       * @see SpMulticastLongChannel#capacity()
       */
      public int capacity() {
         return this.channelCapacity;
      }

      /**
       * @see SpMulticastLongChannel#nilValue()
       */
      public long nilValue() {
         return nilValue;
      }

      /**
       * Transfer all available items from the channel and hand to consume.
       * It returns when loss is experienced, with {@link #lost()} incremented by the size of the lost items.
       *
       * @return the number of polled elements
       */
      public int listen(LongConsumer onMessage) {
         return listen(onMessage, this.channelCapacity);
      }

      /**
       * Transfer up to {@code limit} items from the channel and hand to consume.
       * It returns when loss is experienced, with {@link #lost()} incremented by the size of the lost items.
       *
       * @return the number of polled elements
       */
      public int listen(LongConsumer onMessage, int limit) {
         final long[] sequenceBuffer = this.sequenceBuffer;
         final long mask = this.mask;
         long listenedSequence = this.listenedSequence;
         final long lost = lpLost();

         for (int i = 0; i < limit; i++) {
            final long sequenceOffset = calcSequenceOffset(listenedSequence << 1, mask);
            long sequence = lvSequence(sequenceBuffer, sequenceOffset);
            //LoadLoad + LoadStore
            final long expectedCommittedSequence = listenedSequence + 1;
            if (sequence == expectedCommittedSequence) {
               //listened sequence is committed, can read
               final long elementOffset = sequenceOffset + 8;
               final long element = lvSequence(sequenceBuffer, elementOffset);
               //LoadLoad + LoadStore
               //TODO a plain load could be enough?
               sequence = lvSequence(sequenceBuffer, sequenceOffset);
               //LoadLoad + LoadStore
               //still valid?
               if (sequence == expectedCommittedSequence) {
                  onMessage.accept(element);
                  //move to the next element
                  listenedSequence++;
               } else {
                  //lost experienced!
                  chase(listenedSequence, sequence, lost, this);
                  return i;
               }
            } else {
               if (sequence > expectedCommittedSequence) {
                  //lost experienced!
                  chase(listenedSequence, sequence, lost, this);
                  return i;
               } else {
                  //empty!
                  this.listenedSequence = listenedSequence;
                  return i;
               }
            }
         }
         //no loss is experienced!
         this.listenedSequence = listenedSequence;
         return limit;
      }

      /**
       * Retrieves one item from the channel, if available.
       * It returns {@link #nilValue()} if there are no new items available or loss is experienced.
       *
       * @return the item of the channel or {@link #nilValue()} if loss is experienced or no new items are available
       */
      public long listen() {
         final long[] sequenceBuffer = this.sequenceBuffer;
         final long mask = this.mask;
         final long listenedSequence = this.listenedSequence;
         final long nilValue = this.nilValue;
         final long lost = lpLost();

         final long sequenceOffset = calcSequenceOffset(listenedSequence << 1, mask);
         long sequence = lvSequence(sequenceBuffer, sequenceOffset);
         //LoadLoad + LoadStore
         final long expectedCommittedSequence = listenedSequence + 1;
         if (sequence == expectedCommittedSequence) {
            //listened sequence is committed, can read
            final long elementOffset = sequenceOffset + 8;
            final long element = lvSequence(sequenceBuffer, elementOffset);
            //LoadLoad + LoadStore
            //TODO a plain load could be enough?
            sequence = lvSequence(sequenceBuffer, sequenceOffset);
            //LoadLoad + LoadStore
            //still valid?
            if (sequence == expectedCommittedSequence) {
               //move to the next element
               this.listenedSequence = listenedSequence + 1;
               return element;
            } else {
               //lost experienced!
               chase(listenedSequence, sequence, lost, this);
               return nilValue;
            }
         } else {
            if (sequence > expectedCommittedSequence) {
               //lost experienced!
               chase(listenedSequence, sequence, lost, this);
               return nilValue;
            } else {
               //empty!
               return nilValue;
            }
         }
      }

      /**
       * Force this listener to chase the last element thrown in the channel.
       *
       * @return the number of elements lost by this listener due to the chase
       */
      public long chase() {
         final long sequenceToChase = Math.max(0, this.channel.lvCommittedSequence() - 1);
         final long lostSequence = sequenceToChase - this.listenedSequence;
         soLost(lpLost() + lostSequence);
         this.listenedSequence = sequenceToChase;
         return lostSequence;
      }

      /**
       * The number of elements lost by this listener until the creation of the listened {@link SpMulticastLongChannel}.
       * This method's accuracy is subject to concurrent modifications happening.
       *
       * @return the number of elements lost by this listener
       */
      public long lost() {
         return lvLost();
      }

      /**
       * Represents an operation that accepts a single {@code long}-valued argument and
       * returns no result.
       */
      public interface LongConsumer {

         /**
          * Performs this operation on the given argument.
          *
          * @param value the input argument
          */
         void accept(long value);

      }
   }
}
