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

import org.jctools.channels.Channel;
import org.jctools.channels.ChannelReceiver;
import org.jctools.channels.mapping.Mapper;
import org.jctools.util.Pow2;
import org.jctools.util.Template;

/**
 * A Single-Producer channel backed by a pre-allocated buffer.
 * It allows enough fast multiple (0 or more) {@link SpMulticastChannelConsumer}s to receive the messages.
 * The {@link SpMulticastChannelProducer} can't be stopped by any slow {@link SpMulticastChannelConsumer} that
 * could experience losses when not fast enough.
 */
public final class SpMulticastChannel<E> implements Channel<E> {

   private static final boolean debugEnabled = Boolean.getBoolean("jctools.channels.compile.dump");

   private final int elementSize;
   private final Mapper<E> mapper;
   private final ByteBuffer buffer;
   private final int requestedCapacity;
   private final int maximumCapacity;
   private final SpMulticastChannelProducer<E> producer;

   /**
    * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
    *
    * @param buffer
    * @param requestedCapacity
    */
   public SpMulticastChannel(final ByteBuffer buffer, final int requestedCapacity, final Class<E> type) {
      this.requestedCapacity = requestedCapacity;
      this.maximumCapacity = Pow2.roundToPowerOfTwo(requestedCapacity);
      this.buffer = buffer;
      this.mapper = new Mapper<E>(type, debugEnabled);
      this.elementSize = mapper.getSizeInBytes();

      checkSufficientCapacity();
      checkByteBuffer();

      producer = newProducer(type, buffer, requestedCapacity, elementSize);
   }

   private void checkByteBuffer() {
      if (!buffer.isDirect()) {
         throw new IllegalArgumentException("Channels only work with direct or memory mapped buffers");
      }
   }

   private void checkSufficientCapacity() {
      final int requiredCapacityInBytes = ChannelLayout.getRequiredBufferSize(requestedCapacity, elementSize);
      if (buffer.capacity() < requiredCapacityInBytes) {
         throw new IllegalArgumentException("Failed to meet required maximumCapacity in bytes: " + requiredCapacityInBytes);
      }
   }

   /**
    * {@inherited}
    */
   @Override
   public SpMulticastChannelConsumer<E> consumer(ChannelReceiver<E> receiver) {
      return newConsumer(buffer, requestedCapacity, elementSize, receiver);
   }

   /**
    * {@inherited}
    */
   @Override
   public SpMulticastChannelProducer<E> producer() {
      return producer;
   }

   /**
    * @deprecated Due to the nature of the {@link SpMulticastChannelConsumer} that act as reader not capable to "consume"
    * any message of the produced stream, this value is near to useless, but anyway provides a behaviour consistent
    * with the original contract of {@link Channel#size}.
    *
    * {@inherited}
    */
   public int size() {
      return this.producer.size();
   }

   /**
    * {@inherited}
    */
   @Override
   public int maximumCapacity() {
      return this.maximumCapacity;
   }

   /**
    * {@inherited}
    */
   @Override
   public int requestedCapacity() {
      return requestedCapacity;
   }

   /**
    * @deprecated Due to the nature of the {@link SpMulticastChannelConsumer} that act as reader not capable to "consume"
    * any message of the produced stream, this value is near to useless, but anyway provides a behaviour consistent
    * with the original contract of {@link Channel#isEmpty()}.
    *
    * {@inherited}
    */
   public boolean isEmpty() {
      return size() == 0;
   }

   @SuppressWarnings("unchecked")
   private SpMulticastChannelProducer<E> newProducer(final Class<E> type, final Object... args) {
      return mapper.newFlyweight(SpMulticastChannelProducer.class, "ChannelProducerTemplate.java", Template.fromFile(Channel.class, "ChannelProducerTemplate.java"), args);
   }

   @SuppressWarnings("unchecked")
   private SpMulticastChannelConsumer<E> newConsumer(Object... args) {
      return mapper.newFlyweight(SpMulticastChannelConsumer.class, "ChannelBatchConsumerTemplate.java", Template.fromFile(Channel.class, "ChannelBatchConsumerTemplate.java"), args);
   }

}
