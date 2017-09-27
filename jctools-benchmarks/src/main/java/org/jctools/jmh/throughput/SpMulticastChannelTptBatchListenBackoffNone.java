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

package org.jctools.jmh.throughput;

import java.util.concurrent.TimeUnit;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpMulticastChannel;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class SpMulticastChannelTptBatchListenBackoffNone {

   private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
   private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
   Integer ONE = 777;
   @Param(value = {"132000"})
   String requestedCapacity;

   SpMulticastChannel<Integer> channel;
   SpMulticastChannel.Listener<Integer> listener;
   private Integer escape;
   private MessagePassingQueue.Consumer<Integer> consumer;

   @Setup()
   public void init() {
      this.channel = new SpMulticastChannel<Integer>(Integer.parseInt(requestedCapacity));
      this.listener = this.channel.newListener();
      this.consumer = new MessagePassingQueue.Consumer<Integer>() {
         @Override
         public void accept(Integer e) {
            if (e != ONE) {
               escape = e;
            }
         }
      };
   }

   @Benchmark
   @Group("tpt")
   public void shout() {
      channel.shout(ONE);
      if (DELAY_PRODUCER != 0) {
         Blackhole.consumeCPU(DELAY_PRODUCER);
      }
   }

   @Benchmark
   @Group("tpt")
   public void batchListen(PollCounters counters) {
      final long oldLost = listener.lost();
      final int listened = listener.listen(consumer);
      final long newLost = listener.lost();
      final long lost = newLost - oldLost;
      if (lost > 0) {
         counters.lost += lost;
      }
      if (listened == 0) {
         counters.empty++;
         backoff();
      } else {
         counters.listened += listened;
      }
      if (DELAY_CONSUMER != 0) {
         Blackhole.consumeCPU(DELAY_CONSUMER);
      }
   }

   protected void backoff() {

   }

   @Setup(Level.Iteration)
   public void chase() {
      listener.chase();
   }

   @AuxCounters
   @State(Scope.Thread)
   public static class PollCounters {

      public long empty;
      public long lost;
      public long listened;

      @Setup(Level.Iteration)
      public void clean() {
         empty = lost = listened = 0;
      }
   }
}
