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

package org.jctools.handrolled.throughput;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpMulticastChannel;

public class SpMulticastChannelPerfTest {

   public static void main(String[] args) throws InterruptedException {
      final int TESTS = 20;
      final int MESSAGES = 50000000;
      final int CONSUMERS = 1;
      final int CAPACITY = 1 << 17;
      final Long value = new Long(0);
      final SpMulticastChannel<Long> channel = new SpMulticastChannel<Long>(CAPACITY);
      final CountDownLatch started;
      if (CONSUMERS > 0) {
         started = new CountDownLatch(CONSUMERS);
      } else {
         started = null;
      }
      final Thread[] listenerRunners = new Thread[CONSUMERS];
      final SpMulticastChannel.Listener<Long>[] listeners = new SpMulticastChannel.Listener[CONSUMERS];
      for (int i = 0; i < CONSUMERS; i++) {
         final int consumerId = i + 1;
         final SpMulticastChannel.Listener<Long> chaser = channel.newListener();
         listeners[i] = chaser;
         final Thread listenerRunner = new Thread(new Runnable() {
            @Override
            public void run() {
               final SpMulticastChannel.Listener<Long> listener = chaser;
               final MessagePassingQueue.Consumer<Long> consumer = new MessagePassingQueue.Consumer<Long>() {
                  @Override
                  public void accept(Long e) {
                     if (e == null)
                        throw new IllegalStateException("WTF!");
                  }
               };
               long count = 0;
               long isEmpty = 0;
               started.countDown();
               while (!Thread.currentThread().isInterrupted()) {
                  final long oldLost = listener.lost();
                  final long listened = listener.listen(consumer, CAPACITY);
                  if (listened == 0 && listener.lost() == oldLost) {
                     isEmpty++;
                     LockSupport.parkNanos(1L);
                  }
                  count += listened;
               }
               System.out.println("[" + consumerId + "] polled: " + count + " total lost: " + listener.lost() + " empty: " + isEmpty);
            }
         });
         listenerRunner.start();
         listenerRunners[i] = listenerRunner;
      }
      final long[] lost = new long[CONSUMERS];
      if (CONSUMERS > 0) {
         started.await();
      }
      for (int i = 0; i < TESTS; i++) {

         for (int l = 0; l < CONSUMERS; l++) {
            lost[l] = listeners[l].lost();
         }
         final long start = System.nanoTime();
         for (int m = 0; m < MESSAGES; m++) {
            channel.shout(value);
         }
         final long elapsed = System.nanoTime() - start;
         System.out.println((MESSAGES * 1000000000L) / elapsed + " msg/sec");
         TimeUnit.SECONDS.sleep(2);
         for (int l = 0; l < CONSUMERS; l++) {
            System.out.println("lost msgs [" + (l + 1) + "]: " + (listeners[l].lost() - lost[l]));

         }
      }
      for (Thread listenerRunner : listenerRunners) {
         listenerRunner.interrupt();
         listenerRunner.join();
      }
   }
}
