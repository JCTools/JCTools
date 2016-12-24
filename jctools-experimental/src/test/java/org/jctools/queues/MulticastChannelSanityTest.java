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
import static org.junit.Assert.assertNull;

public class MulticastChannelSanityTest {

   private static final int SIZE = 8192 * 2;
   private SpMulticastChannel<Long> multicastChannel;

   @Before
   public void init() {
      this.multicastChannel = new SpMulticastChannel<>(SIZE);
   }

   @Test
   public void noElementsToListen() {
      assertEquals(0, multicastChannel.shouts());
      final SpMulticastChannel.Listener<Long> listener = multicastChannel.newListener();
      assertNull(listener.listen());
      assertEquals(0, listener.lost());
   }

   @Test
   public void noElementsToBatchListen() {
      assertEquals(0, multicastChannel.shouts());
      final SpMulticastChannel.Listener<Long> listener = multicastChannel.newListener();
      assertEquals(0, listener.listen(new MessagePassingQueue.Consumer<Long>() {
         @Override
         public void accept(Long e) {

         }
      }), Integer.MAX_VALUE);
      assertEquals(0, listener.lost());
   }

   @Test
   public void chaseWithNoTransmission() {
      assertEquals(0, multicastChannel.shouts());
      final SpMulticastChannel.Listener<Long> listener = multicastChannel.newListener();
      assertEquals(0, listener.chase());
      assertEquals(null, listener.listen());
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

      final SpMulticastChannel.Listener<Long> listener = multicastChannel.newListener();
      final long expectedLost = expectedShouts - 1;
      assertEquals(expectedLost, listener.chase());
      assertEquals(expectedLost, listener.listen().longValue());
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
      final SpMulticastChannel.Listener<Long> listener = multicastChannel.newListener();
      i = 0;
      Long e;
      while ((e = listener.listen()) != null) {
         assertEquals(e.longValue(), i++);
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
      final SpMulticastChannel.Listener<Long> listener = multicastChannel.newListener();
      final ArrayList<Long> values = new ArrayList<>();
      final int listened = listener.listen(new MessagePassingQueue.Consumer<Long>() {
         @Override
         public void accept(Long e) {
            values.add(e);
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
