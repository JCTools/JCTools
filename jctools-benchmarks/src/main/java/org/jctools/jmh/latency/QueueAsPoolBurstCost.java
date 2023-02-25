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
package org.jctools.jmh.latency;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@SuppressWarnings("serial")
public class QueueAsPoolBurstCost {

   @Param({"None", "ArrayBlockingQueue", "ConcurrentLinkedQueue", "MpmcUnboundedXaddArrayQueue", "MpmcArrayQueue"})
   String qType;
   @Param({"1", "10"})
   int burstSize;
   @Param("true")
   boolean warmup;
   @Param(value = {"132000"})
   String qCapacity;
   Queue<Object> q;

   @Param({"0", "10", "100"})
   int work;

   @Setup
   public void init() {
      final boolean noQ = qType.equals("None");
      if (!noQ && warmup) {
         q = QueueByTypeFactory.createQueue(qType, 128);

         final Object o = new Object();

         // stretch the queue to the limit, working through resizing and full
         // 128 * 2 account for the xadd qs that pool by default 2 chunks
         for (int i = 0; i < ((128 * 2) + 100); i++) {
            q.offer(o);
         }
         for (int i = 0; i < ((128 * 2) + 100); i++) {
            q.poll();
         }
         // make sure the important common case is exercised
         for (int i = 0; i < 20000; i++) {
            q.offer(o);
            q.poll();
         }
      }
      q = noQ ? null : QueueByTypeFactory.buildQ(qType, qCapacity);
      // fill the qs, if any
      final Object o = new Object();
      if (q != null) {
         for (int i = 0; i < Integer.parseInt(qCapacity); i++) {
            if (!q.offer(o)) {
               throw new IllegalStateException("qCapacity isn't enough to hold all elements for " + qType);
            }
         }
      }
   }

   @Benchmark
   @CompilerControl(CompilerControl.Mode.DONT_INLINE)
   public void acquireAndRelease(ThreadLocalPool tlPool) {
      final int work = this.work;
      final int burst = burstSize;
      final Queue<Object> pool = q == null ? tlPool.pool : q;
      final ArrayDeque<Object> tmp = tlPool.acquired;
      for (int i = 0; i < burst; i++) {
         tmp.offer(pool.poll());
      }
      if (work > 0) {
         Blackhole.consumeCPU(work);
      }
      for (int i = 0; i < burst; i++) {
         pool.offer(tmp.pollLast());
      }
   }

   @State(Scope.Thread)
   public static class ThreadLocalPool {

      private ArrayDeque<Object> pool;
      private ArrayDeque<Object> acquired;

      @Setup
      public void init(QueueAsPoolBurstCost benchmark) {
         acquired = new ArrayDeque<Object>(benchmark.burstSize);
         pool = new ArrayDeque<Object>(benchmark.burstSize);
         if ("None".equals(benchmark.qType)) {
            final Object o = new Object();
            for (int i = 0; i < benchmark.burstSize; i++) {
               pool.add(o);
            }
         }
      }
   }
}
