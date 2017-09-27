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

package org.jctools.queues;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MulticastLongChannelSanityTest {

   private static final int SIZE = 8192 * 2;
   private SpMulticastLongChannel multicastChannel;

   @Before
   public void init() {
      this.multicastChannel = new SpMulticastLongChannel(SIZE);
   }

   @Test
   public void noElementsToListen() {
      assertEquals(0, multicastChannel.shouts());
      final SpMulticastLongChannel.Listener listener = multicastChannel.newListener();
      assertEquals(listener.listen(), listener.nilValue());
      assertEquals(0, listener.lost());
   }

   @Test
   public void noElementsToBatchListen() {
      assertEquals(0, multicastChannel.shouts());
      final SpMulticastLongChannel.Listener listener = multicastChannel.newListener();
      assertEquals(0, listener.listen(new SpMulticastLongChannel.Listener.LongConsumer() {
         @Override
         public void accept(long value) {

         }
      }, Integer.MAX_VALUE));
      assertEquals(0, listener.lost());
   }

   @Test
   public void chaseWithNoTransmission() {
      assertEquals(0, multicastChannel.shouts());
      final SpMulticastLongChannel.Listener listener = multicastChannel.newListener();
      assertEquals(0, listener.chase());
      assertEquals(listener.nilValue(), listener.listen());
   }

   @Test
   public void chaseWithTransmission() {
      assertEquals(0, multicastChannel.shouts());
      long i = 0;
      while (i < multicastChannel.capacity()) {
         multicastChannel.shout(i);
         i++;
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, multicastChannel.shouts());

      final SpMulticastLongChannel.Listener listener = multicastChannel.newListener();
      final long expectedLost = expectedShouts - 1;
      assertEquals(expectedLost, listener.chase());
      assertEquals(expectedLost, listener.listen());
   }

   @Test
   public void cantBlockProducer() {
      assertEquals(0, multicastChannel.shouts());
      long i = 0;
      while (i < multicastChannel.capacity() + 1) {
         multicastChannel.shout(i);
         i++;
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, multicastChannel.shouts());
   }

   @Test
   public void expectOrderedListen() {
      assertEquals(0, multicastChannel.shouts());
      //fill
      long i = 0;
      while (i < multicastChannel.capacity()) {
         multicastChannel.shout(i);
         i++;
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, multicastChannel.shouts());
      //ordered listen
      final SpMulticastLongChannel.Listener listener = multicastChannel.newListener();
      i = 0;
      long e;
      while ((e = listener.listen()) != listener.nilValue()) {
         assertEquals(e, i++);
      }
      assertEquals(0, listener.lost());
   }

   @Test
   public void expectOrderedBatchListen() {
      assertEquals(0, multicastChannel.shouts());
      //fill
      long i = 0;
      while (i < multicastChannel.capacity()) {
         multicastChannel.shout(i);
         i++;
      }
      long expectedShouts = i;
      assertEquals(expectedShouts, multicastChannel.shouts());
      //ordered batch listen
      final SpMulticastLongChannel.Listener listener = multicastChannel.newListener();
      final ArrayList<Long> values = new ArrayList<>();
      final int listened = listener.listen(new SpMulticastLongChannel.Listener.LongConsumer() {
         @Override
         public void accept(long value) {
            values.add(value);
         }
      }, Integer.MAX_VALUE);
      assertEquals(multicastChannel.capacity(), listened);
      assertEquals(0, listener.lost());
      i = 0;
      for (Long value : values) {
         assertEquals(value.longValue(), i++);
      }
   }
}
