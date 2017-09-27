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

import org.jctools.channels.ChannelProducer;

/**
 * {@inheritDoc}
 *
 * This producer by contract can't return {@code false} on {@link #claim} and will not stop to produce even
 * if a {@link SpMulticastChannelConsumer} would die.
 */
public abstract class SpMulticastChannelProducer<E> implements ChannelProducer<E> {

   private final OffHeapFixedMessageSizeAppender appender;
   protected long pointer;
   private long sequence;

   public SpMulticastChannelProducer(final ByteBuffer buffer, final int capacity, final int messageSize) {
      this.appender = new OffHeapFixedMessageSizeAppender(buffer, capacity, messageSize);
      this.sequence = -1;
      this.pointer = -1;
   }

   final int size() {
      return (int) Math.min(this.appender.shouts(), this.appender.capacity());
   }

   /**
    * The number of elements produced.
    * This method's accuracy is subject to concurrent modifications happening.
    *
    * @return number of messages thrown in the {@link SpMulticastChannel}
    */
   public final long produced() {
      return this.appender.shouts();
   }

   /**
    * {@inheritDoc}
    *
    * @return true
    */
   @Override
   public final boolean claim() {
      this.sequence = this.appender.writeAcquire();
      this.pointer = this.appender.messageOffset(sequence);
      return true;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final boolean commit() {
      if (sequence < 0)
         return false;
      this.appender.writeRelease(sequence);
      this.pointer = -1;
      this.sequence = -1;
      return true;
   }

}