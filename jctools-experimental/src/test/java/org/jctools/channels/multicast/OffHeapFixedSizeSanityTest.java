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

import java.util.ArrayList;
import java.util.function.LongConsumer;

import org.jctools.util.JvmInfo;
import org.jctools.util.UnsafeAccess;
import org.jctools.util.UnsafeDirectByteBuffer;
import org.junit.Before;
import org.junit.Test;
import sun.nio.ch.DirectBuffer;

import static org.junit.Assert.assertEquals;

public class OffHeapFixedSizeSanityTest {

   private static final int SIZE = 8192 * 2;
   private static final int MESSAGE_SIZE = 12;
   private OffHeapFixedMessageSizeAppender appender;
   private OffHeapFixedMessageSizeTailer tailer;

   @Before
   public void init() {
      final int capacity = SIZE;
      final int bufferSize = ChannelLayout.getRequiredBufferSize(capacity, MESSAGE_SIZE);
      this.appender = new OffHeapFixedMessageSizeAppender(UnsafeDirectByteBuffer.allocateAlignedByteBuffer(bufferSize, JvmInfo.CACHE_LINE_SIZE), capacity, MESSAGE_SIZE);
      this.tailer = new OffHeapFixedMessageSizeTailer(this.appender.buffer(), capacity, MESSAGE_SIZE);
   }

   @Test
   public void noElementsToListen() {
      assertEquals(0, appender.shouts());
      assertEquals(OffHeapFixedMessageSizeTailer.EOF, tailer.readAcquire());
      assertEquals(0, tailer.lost());
   }

   @Test
   public void noElementsToBatchListen() {
      assertEquals(0, appender.shouts());
      assertEquals(0, tailer.read(new LongConsumer() {
         @Override
         public void accept(long value) {

         }
      }, Integer.MAX_VALUE));
      assertEquals(0, tailer.lost());
   }

   @Test
   public void chaseWithNoTransmission() {
      assertEquals(0, appender.shouts());
      assertEquals(0, tailer.chase());
      assertEquals(OffHeapFixedMessageSizeTailer.EOF, tailer.readAcquire());
   }

   @Test
   public void chaseWithTransmission() {
      assertEquals(0, appender.shouts());
      long i = 0;
      while (i < appender.capacity()) {
         final long writeSequence = appender.writeAcquire();
         try {
            UnsafeAccess.UNSAFE.putLong(appender.messageOffset(writeSequence), i);
            i++;
         } finally {
            appender.writeRelease(writeSequence);
         }
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, appender.shouts());
      final long expectedLost = expectedShouts - 1;
      assertEquals(expectedLost, tailer.chase());
      final long readSequence = tailer.readAcquire();
      try {
         final long value = UnsafeAccess.UNSAFE.getLong(tailer.messageCopyAddress());
         assertEquals(expectedLost, value);
      } finally {
         tailer.readRelease(readSequence);
      }
   }

   @Test
   public void cantBlockProducer() {
      assertEquals(0, appender.shouts());
      long i = 0;
      while (i < appender.capacity() + 1) {
         final long writeSequence = appender.writeAcquire();
         try {
            UnsafeAccess.UNSAFE.putLong(appender.messageOffset(writeSequence), i);
            i++;
         } finally {
            appender.writeRelease(writeSequence);
         }
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, appender.shouts());
   }

   @Test
   public void expectedAppended() {
      final int values = 10;
      assertEquals(0, appender.shouts());
      //fill
      int i = 0;
      final long[] addresses = new long[values];
      while (i < values) {
         final long writeSequence = appender.writeAcquire();
         try {
            addresses[i] = appender.messageOffset(writeSequence);
            UnsafeAccess.UNSAFE.putLong(appender.messageOffset(writeSequence), i);
            i++;
         } finally {
            appender.writeRelease(writeSequence);
         }
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, appender.shouts());
      for(int v = 0;v<addresses.length;v++){
         assertEquals(v,UnsafeAccess.UNSAFE.getLong(addresses[v]));
      }
   }

   @Test
   public void expectOrderedListen() {
      assertEquals(0, appender.shouts());
      //fill
      long i = 0;
      while (i < appender.capacity()) {
         final long writeSequence = appender.writeAcquire();
         try {
            UnsafeAccess.UNSAFE.putLong(appender.messageOffset(writeSequence), i);
            i++;
         } finally {
            appender.writeRelease(writeSequence);
         }
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, appender.shouts());
      //ordered listen
      i = 0;
      long readSequence;
      while ((readSequence = tailer.readAcquire()) >= 0) {
         try {
            final long value = UnsafeAccess.UNSAFE.getLong(tailer.messageCopyAddress());
            assertEquals(value, i++);
         } finally {
            tailer.readRelease(readSequence);
         }
      }
      assertEquals(0, tailer.lost());
   }

   @Test
   public void expectBatchListen() {
      assertEquals(0, appender.shouts());
      //fill
      long i = 0;
      while (i < appender.capacity()) {
         final long writtenValue = i;
         final long writeSequence = appender.writeAcquire();
         try {
            UnsafeAccess.UNSAFE.putLong(appender.messageOffset(writeSequence), writtenValue);
            i++;
         } finally {
            appender.writeRelease(writeSequence);
         }
         assertEquals(1,tailer.read(messageAddress->{
            final long readValue = UnsafeAccess.UNSAFE.getLong(messageAddress);
            assertEquals(writtenValue,readValue);
         }, tailer.capacity()));
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, appender.shouts());
      assertEquals(0, tailer.lost());
      assertEquals(0,tailer.read(messageAddress->{}, tailer.capacity()));
   }

   @Test
   public void expectOrderedBatchListen() {
      assertEquals(0, appender.shouts());
      //fill
      long i = 0;
      while (i < appender.capacity()) {
         final long writeSequence = appender.writeAcquire();
         try {
            UnsafeAccess.UNSAFE.putLong(appender.messageOffset(writeSequence), i);
            i++;
         } finally {
            appender.writeRelease(writeSequence);
         }
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, appender.shouts());
      //ordered batch listen
      final ArrayList<Long> values = new ArrayList<>();
      final int listened = tailer.read(new LongConsumer() {
         @Override
         public void accept(long messageAddress) {
            final long value = UnsafeAccess.UNSAFE.getLong(messageAddress);
            values.add(value);
         }
      }, Integer.MAX_VALUE);
      assertEquals(tailer.capacity(), listened);
      assertEquals(0, tailer.lost());
      i = 0;
      for (Long value : values) {
         assertEquals(value.longValue(), i++);
      }
   }



}
