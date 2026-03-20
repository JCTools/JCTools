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
package org.jctools.queues;

import static org.jctools.util.PortableJvmInfo.CPUs;
import static org.jctools.util.Pow2.isPowerOfTwo;
import static org.jctools.util.Pow2.roundToPowerOfTwo;

import org.jctools.queues.MpscArrayQueue;
import org.jctools.util.RangeUtil;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

abstract class MpscBlockingQueueL0Pad<E> extends AbstractQueue<E>
{
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

abstract class MpscBlockingQueueColdFields<E> extends MpscBlockingQueueL0Pad<E>
{
    protected final int parallelQueues;
    protected final int parallelQueuesMask;
    protected final MpscArrayQueue<E>[] queues;
    protected final ConcurrentLinkedQueue<Thread>[] fullWaiters;

    @SuppressWarnings("unchecked")
    MpscBlockingQueueColdFields(int capacity, int queueParallelism)
    {
        parallelQueues = isPowerOfTwo(queueParallelism) ? queueParallelism
                : roundToPowerOfTwo(queueParallelism) / 2;
        parallelQueuesMask = parallelQueues - 1;
        queues = new MpscArrayQueue[parallelQueues];
        fullWaiters = new ConcurrentLinkedQueue[parallelQueues];
        for (int i = 0; i < parallelQueues; i++)
        {
            fullWaiters[i] = new ConcurrentLinkedQueue<>();
        }
        int fullCapacity = roundToPowerOfTwo(capacity);
        RangeUtil.checkGreaterThanOrEqual(fullCapacity, parallelQueues, "fullCapacity");
        for (int i = 0; i < parallelQueues; i++)
        {
            queues[i] = new MpscArrayQueue<E>(fullCapacity);
        }
    }
}

abstract class MpscBlockingQueueMidPad<E> extends MpscBlockingQueueColdFields<E>
{
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    MpscBlockingQueueMidPad(int capacity, int queueParallelism)
    {
        super(capacity, queueParallelism);
    }
}

abstract class MpscBlockingQueueConsumerFields<E> extends MpscBlockingQueueMidPad<E>
{
    int consumerQueueIndex;
    protected final ConcurrentLinkedQueue<Thread> emptyWaiters = new ConcurrentLinkedQueue<>();

    MpscBlockingQueueConsumerFields(int capacity, int queueParallelism)
    {
        super(capacity, queueParallelism);
    }
}

abstract class MpscBlockingQueueL2Pad<E> extends MpscBlockingQueueConsumerFields<E>
{
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    MpscBlockingQueueL2Pad(int capacity, int queueParallelism)
    {
        super(capacity, queueParallelism);
    }
}

/**
 * A blocking MPSC queue implementation based on JCTools MpscArrayQueue.
 * This queue uses multiple parallel queues to reduce contention among producers.
 *
 * @param <E> the type of elements held in this queue
 */
public class MpscBlockingQueue<E> extends MpscBlockingQueueL2Pad<E> implements BlockingQueue<E>
{
    private final AtomicInteger threadQueueIndexCounter = new AtomicInteger(0);
    private final ThreadLocal<Integer> threadQueueIndex = new ThreadLocal<Integer>()
    {
        @Override
        protected Integer initialValue()
        {
            return threadQueueIndexCounter.getAndIncrement() & parallelQueuesMask;
        }
    };

    public MpscBlockingQueue(int capacity)
    {
        super(capacity, CPUs);
    }

    public MpscBlockingQueue(int capacity, int queueParallelism)
    {
        super(capacity, queueParallelism);
    }


    @Override
    public E poll()
    {
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        final int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++)
        {
            final int id = qIndex & parallelQueuesMask;
            e = queues[id].poll();
            if (e != null)
            {
                unparkOneFullWaiter(id);
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
    }

    @Override
    public E peek()
    {
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        final int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++)
        {
            e = queues[qIndex & parallelQueuesMask].peek();
            if (e != null)
            {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
    }

    @Override
    public int size()
    {
        int size = 0;
        for (int i = 0; i < queues.length; i++)
        {
            size += queues[i].size();
        }
        return size;
    }

    public int capacity()
    {
        return queues.length * queues[0].capacity();
    }

    @Override
    public E take() throws InterruptedException
    {
        return awaitNotEmpty(false, 0);
    }

    @Override
    public E poll(long time, TimeUnit unit) throws InterruptedException
    {
        return awaitNotEmpty(true, unit.toNanos(time));
    }

    @Override
    public boolean offer(final E e)
    {
        if (queues[threadQueueIndex.get()].offer(e))
        {
            unparkOneEmptyWaiter();
            return true;
        }
        return false;
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException
    {
        return awaitNotFull(e, true, unit.toNanos(timeout));
    }

    @Override
    public void put(E e) throws InterruptedException
    {
        awaitNotFull(e, false, 0);
    }

    @Override
    public int remainingCapacity()
    {
        return capacity() - size();
    }

    @Override
    public int drainTo(Collection<? super E> c)
    {
        final int limit = capacity();
        return drainTo(c, limit);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements)
    {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;

        int n = 0;
        E e;
        while (n < maxElements && (e = poll()) != null)
        {
            c.add(e);
            n++;
        }
        return n;
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    private void unparkOneFullWaiter(int id)
    {
        Thread waiterThread = fullWaiters[id].poll();
        if (waiterThread != null)
        {
            LockSupport.unpark(waiterThread);
        }
    }

    private void unparkOneEmptyWaiter()
    {
        Thread waiterLocal = emptyWaiters.poll();
        if (waiterLocal != null)
        {
            LockSupport.unpark(waiterLocal);
        }
    }

    private E awaitNotEmpty(boolean timed, long nanos) throws InterruptedException
    {
        final Thread currentThread = Thread.currentThread();

        for (; ; )
        {
            E retval = poll();
            if (retval != null) return retval;

            if (timed && nanos <= 0)
            {
                return null;
            }

            emptyWaiters.add(currentThread);

            if (timed)
            {
                LockSupport.parkNanos(this, nanos);
                nanos = 0;
            }
            else
            {
                LockSupport.park(this);
            }

            emptyWaiters.remove(currentThread);

            if (currentThread.isInterrupted())
            {
                throw new InterruptedException();
            }
        }
    }

    private boolean awaitNotFull(E e, boolean timed, long nanos) throws InterruptedException
    {
        final Thread currentThread = Thread.currentThread();

        for (; ; )
        {
            boolean retval = offer(e);
            if (retval) return retval;

            if (timed && nanos <= 0)
            {
                return false;
            }

            fullWaiters[threadQueueIndex.get()].add(currentThread);

            if (timed)
            {
                LockSupport.parkNanos(currentThread, nanos);
                nanos = 0;
            }
            else
            {
                LockSupport.park(currentThread);
            }

            if (currentThread.isInterrupted())
            {
                throw new InterruptedException();
            }
        }
    }
}
