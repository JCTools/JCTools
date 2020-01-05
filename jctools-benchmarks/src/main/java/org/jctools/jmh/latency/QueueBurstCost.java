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

import org.jctools.queues.QueueByTypeFactory;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings("serial")
public class QueueBurstCost
{
    private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
    private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
    @Param( {"SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue"})
    String qType;
    @Param( {"100"})
    int burstSize;
    @Param("1")
    int consumerCount;
    @Param("true")
    boolean warmup;
    @Param(value = {"132000"})
    String qCapacity;
    Queue<Event> q;
    private ExecutorService consumerExecutor;
    private Consumer[] consumers;
    private CountDownLatch stopped;

    @Setup(Level.Trial)
    public void setupQueueAndConsumers()
    {
        if (warmup)
        {
            q = QueueByTypeFactory.createQueue(qType, 128);

            final Event event = new Event();

            // stretch the queue to the limit, working through resizing and full
            // 128 * 2 account for the xadd qs that pool by default 2 chunks
            for (int i = 0; i < ((128 * 2) + 100); i++)
            {
                q.offer(event);
            }
            for (int i = 0; i < ((128 * 2) + 100); i++)
            {
                q.poll();
            }
            // make sure the important common case is exercised
            for (int i = 0; i < 20000; i++)
            {
                q.offer(event);
                q.poll();
            }
        }
        q = QueueByTypeFactory.buildQ(qType, qCapacity);
        consumers = new Consumer[consumerCount];
        for (int i = 0; i < consumerCount; i++)
        {
            consumers[i] = new Consumer(q, i);
        }
        consumerExecutor = Executors.newFixedThreadPool(consumerCount);
    }

    @Setup(Level.Iteration)
    public void startConsumers() throws InterruptedException
    {
        stopped = new CountDownLatch(consumerCount);
        final CountDownLatch started = new CountDownLatch(consumerCount);
        final int consumerCount = this.consumerCount;
        for (int i = 0; i < consumerCount; i++)
        {
            consumers[i].isRunning = true;
            consumers[i].stopped = stopped;
            consumers[i].started = started;
        }
        for (int i = 0; i < consumerCount; i++)
        {
            consumerExecutor.execute(consumers[i]);
        }
        started.await();
    }

    @TearDown(Level.Iteration)
    public void stopConsumers() throws InterruptedException
    {
        final int consumerCount = this.consumerCount;
        for (int i = 0; i < consumerCount; i++)
        {
            consumers[i].isRunning = false;
        }
        stopped.await();
    }

    @TearDown(Level.Trial)
    public void stopExecutor()
    {
        consumerExecutor.shutdown();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void burstCost(Event event)
    {
        final int burst = burstSize;
        final Queue<Event> q = this.q;
        event.reset();
        sendBurst(q, event, burst);
        event.waitFor(burst);
    }

    private static void sendBurst(Queue<Event> q, Event event, int burst)
    {
        for (int i = 0; i < burst; i++)
        {
            while (!q.offer(event))
            {
                ;
            }
            if (DELAY_PRODUCER != 0)
            {
                Blackhole.consumeCPU(DELAY_PRODUCER);
            }
        }
    }

    @State(Scope.Thread)
    public static class Event
    {
        private static final long ARRAY_BASE;
        private static final int ELEMENT_SHIFT;
        private static final int LONG_PAD;

        static
        {
            final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(long[].class);
            final int bytesPad = PortableJvmInfo.CACHE_LINE_SIZE * 2;
            if (8 == scale)
            {
                if (bytesPad < 8 || Pow2.align(bytesPad, 8) != bytesPad)
                {
                    throw new IllegalStateException(bytesPad + " is not multiple of the scale (8)!");
                }
                ELEMENT_SHIFT = Integer.numberOfTrailingZeros(bytesPad);
                LONG_PAD = bytesPad / 8;
            }
            else
            {
                throw new IllegalStateException("Unexpected long[] element size");
            }
            // Including the buffer pad in the array base offset
            ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(long[].class);
        }

        private static long calcSequenceOffset(long index)
        {
            return ARRAY_BASE + (index << ELEMENT_SHIFT);
        }

        private long[] handledCount = null;
        private int consumerCount;

        @Setup
        public void init(QueueBurstCost state)
        {
            //pad the array at the beginning
            final int length = (state.consumerCount << ELEMENT_SHIFT) / 8 + LONG_PAD;
            handledCount = new long[length];
            consumerCount = state.consumerCount;
        }

        void reset()
        {
            final long[] values = this.handledCount;
            final int consumerCount = this.consumerCount;
            for (int i = 0; i < consumerCount; i++)
            {
                final long offset = calcSequenceOffset(i);
                UnsafeAccess.UNSAFE.putLong(values, offset, 0);
            }
            UnsafeAccess.UNSAFE.storeFence();
        }

        void waitFor(int value)
        {
            final long[] values = this.handledCount;
            final int consumerCount = this.consumerCount;
            do
            {
                long total = 0;
                for (int i = 0; i < consumerCount; i++)
                {
                    final long offset = calcSequenceOffset(i);
                    total += UnsafeAccess.UNSAFE.getLongVolatile(values, offset);
                    if (total == value)
                    {
                        return;
                    }
                }
            }
            while (true);
        }

        void handle(int consumerId)
        {
            final long[] values = this.handledCount;
            final long offset = calcSequenceOffset(consumerId);
            final long value = UnsafeAccess.UNSAFE.getLong(values, offset);
            UnsafeAccess.UNSAFE.putOrderedLong(values, offset, value + 1);
        }
    }

    static class ConsumerPad
    {
        public long p40, p41, p42, p43, p44, p45, p46;
        public long p30, p31, p32, p33, p34, p35, p36, p37;
    }

    static class ConsumerFields extends ConsumerPad
    {
        Queue<Event> q;
        volatile boolean isRunning = true;
        CountDownLatch stopped;
        CountDownLatch started;
    }

    static class Consumer extends ConsumerFields implements Runnable
    {
        public long p40, p41, p42, p43, p44, p45, p46;
        public long p30, p31, p32, p33, p34, p35, p36, p37;
        private final int consumerId;

        public Consumer(Queue<Event> q, int consumerId)
        {
            this.q = q;
            this.consumerId = consumerId;
        }

        @Override
        public void run()
        {
            final CountDownLatch stopped = this.stopped;
            final CountDownLatch started = this.started;
            final int consumerId = this.consumerId;
            final Queue<Event> q = this.q;
            started.countDown();
            while (isRunning)
            {
                consume(q, consumerId);
            }
            stopped.countDown();
        }

        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        private void consume(Queue<Event> q, int consumerId)
        {
            Event e = null;
            if ((e = q.poll()) == null)
            {
                return;
            }
            if (DELAY_CONSUMER != 0)
            {
                Blackhole.consumeCPU(DELAY_CONSUMER);
            }
            e.handle(consumerId);
        }

    }
}