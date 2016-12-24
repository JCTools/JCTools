/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelReceiver;

/**
 * {@inheritDoc}
 *
 * It allows sequentially reading messages from a {@link SpMulticastChannel}.
 * It can join the transmission at any point without slowing down the {@link SpMulticastChannelProducer}.
 * If it cannot keep up with the transmission loss will be experienced and recorded.
 */
public abstract class SpMulticastChannelConsumer<E> implements ChannelConsumer {

   protected static final long EOF = OffHeapFixedMessageSizeTailer.EOF;
   protected final ChannelReceiver<E> receiver;
   private final OffHeapFixedMessageSizeTailer tailer;
   private final long messageCopyAddress;
   protected long pointer;
   private long sequence;

   public SpMulticastChannelConsumer(final ByteBuffer buffer,
                                     final int capacity,
                                     final int messageSize,
                                     final ChannelReceiver<E> receiver) {
      this.tailer = new OffHeapFixedMessageSizeTailer(buffer, capacity, messageSize);
      this.receiver = receiver;
      this.sequence = EOF;
      this.pointer = EOF;
      this.messageCopyAddress = tailer.messageCopyAddress();
   }

   /**
    * Force this consumer to chase the last message thrown in the channel.
    *
    * @return the number of elements lost by this listener due to the chase
    */
   public final long chase() {
      return this.tailer.chase();
   }

   /**
    * The number of messages lost by this consumer until the creation of the listened {@link SpMulticastChannel}.
    * This method's accuracy is subject to concurrent modifications happening.
    *
    * @return the number of messages lost by this consumer
    */
   public final long lost() {
      return this.tailer.lost();
   }

   /**
    * {@inheritDoc}
    *
    * It could returns if unable to keep up on the messages flow.
    */
   protected final int read(LongConsumer onReadPointer, int limit) {
      return this.tailer.read(onReadPointer, limit);
   }

   protected final long readAcquire() {
      final long sequence = this.tailer.readAcquire();
      if (sequence >= 0) {
         this.sequence = sequence;
         return this.messageCopyAddress;
      } else {
         return EOF;
      }
   }

   protected final void readRelease(long pointer) {
      if (pointer != this.messageCopyAddress) {
         throw new IllegalStateException("BAD TEMPLATE IMPLEMENTATION!");
      }
      this.tailer.readRelease(this.sequence);
   }

}
