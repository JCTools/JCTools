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

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueByTypeFactory;
import org.jctools.util.UnsafeAccess;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

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
public class MpqBurstCost
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
    int qCapacity;
    MessagePassingQueue<Event> q;
    private ExecutorService consumerExecutor;
    private Consumer consumer;

    @Setup(Level.Trial)
    public void setupQueueAndConsumers()
    {
        if (warmup)
        {
            q = MessagePassingQueueByTypeFactory.createQueue(qType, 128);

            final Event event = new Event();

            // stretch the queue to the limit, working through resizing and full
            for (int i = 0; i < 128 + 100; i++)
            {
                q.offer(event);
            }
            for (int i = 0; i < 128 + 100; i++)
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
        q = MessagePassingQueueByTypeFactory.createQueue(qType, qCapacity);
        consumer = new Consumer(q, consumerCount > 1);
        consumerExecutor = Executors.newFixedThreadPool(consumerCount);
    }

    @Setup(Level.Iteration)
    public void startConsumers()
    {
        consumer.isRunning = true;
        consumer.stopped = new CountDownLatch(consumerCount);
        for (int i = 0; i < consumerCount; i++)
        {
            consumerExecutor.execute(consumer);
        }

    }

    @TearDown(Level.Iteration)
    public void stopConsumers() throws InterruptedException
    {
        consumer.isRunning = false;
        consumer.stopped.await();
    }

    @TearDown(Level.Trial)
    public void stopExecutor() throws InterruptedException
    {
        consumerExecutor.shutdown();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void burstCost(Event event)
    {
        final int burst = burstSize;
        final MessagePassingQueue<Event> q = this.q;
        event.reset();
        sendBurst(q, event, burst);
        event.waitFor(burst);
    }

    private static void sendBurst(MessagePassingQueue<Event> q, Event event, int burst)
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

    //do not extends AtomicLong here: JMH will take care of padding fields for us
    @State(Scope.Thread)
    public static class Event
    {
        private static final long handledOffset = UnsafeAccess.fieldOffset(Event.class, "handled");
        private long handled;

        void reset()
        {
            UnsafeAccess.UNSAFE.putOrderedLong(this, handledOffset, 0);
        }

        void waitFor(int value)
        {
            while (UnsafeAccess.UNSAFE.getLongVolatile(this, handledOffset) != value)
            {

            }
        }

        void sharedHandle()
        {
            UnsafeAccess.UNSAFE.getAndAddLong(this, handledOffset, 1);
        }

        void exclusiveHandle()
        {
            UnsafeAccess.UNSAFE.putOrderedLong(this, handledOffset, handled + 1);
        }
    }

    static class ConsumerPad
    {
        public long p40, p41, p42, p43, p44, p45, p46;
        public long p30, p31, p32, p33, p34, p35, p36, p37;
    }

    static class ConsumerFields extends ConsumerPad
    {
        MessagePassingQueue<Event> q;
        volatile boolean isRunning = true;
        CountDownLatch stopped;
    }

    static class Consumer extends ConsumerFields implements Runnable
    {
        public long p40, p41, p42, p43, p44, p45, p46;
        public long p30, p31, p32, p33, p34, p35, p36, p37;
        private final boolean shared;

        public Consumer(MessagePassingQueue<Event> q, boolean shared)
        {
            this.q = q;
            this.shared = shared;
        }

        @Override
        public void run()
        {
            final CountDownLatch stopped = this.stopped;
            final MessagePassingQueue<Event> q = this.q;
            final boolean shared = this.shared;
            while (isRunning)
            {
                consume(q, shared);
            }
            stopped.countDown();
        }

        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        private void consume(MessagePassingQueue<Event> q, boolean shared)
        {
            Event e = null;
            if ((e = q.relaxedPoll()) == null)
            {
                return;
            }
            if (DELAY_CONSUMER != 0)
            {
                Blackhole.consumeCPU(DELAY_CONSUMER);
            }
            if (shared)
            {
                e.sharedHandle();
            }
            else
            {
                e.exclusiveHandle();
            }
        }

    }
}
