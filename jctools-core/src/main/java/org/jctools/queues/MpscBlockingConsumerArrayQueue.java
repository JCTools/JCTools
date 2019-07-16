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

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;

import static org.jctools.queues.CircularArrayOffsetCalculator.allocate;
import static org.jctools.queues.LinkedArrayQueueUtil.modifiedCalcElementOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

abstract class MpscBlockingConsumerArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}
// $gen:ordered-fields
abstract class MpscBlockingConsumerArrayQueueColdProducerFields<E> extends MpscBlockingConsumerArrayQueuePad1<E>
{
    private final static long P_LIMIT_OFFSET = fieldOffset(MpscBlockingConsumerArrayQueueColdProducerFields.class,"producerLimit");

    private volatile long producerLimit;
    protected final long producerMask;
    protected final E[] producerBuffer;

    MpscBlockingConsumerArrayQueueColdProducerFields(long producerMask, E[] producerBuffer)
    {
        this.producerMask = producerMask;
        this.producerBuffer = producerBuffer;
    }

    final long lvProducerLimit()
    {
        return producerLimit;
    }

    final boolean casProducerLimit(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_LIMIT_OFFSET, expect, newValue);
    }

    final void soProducerLimit(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, newValue);
    }
}

abstract class MpscBlockingConsumerArrayQueuePad2<E> extends MpscBlockingConsumerArrayQueueColdProducerFields<E>
{
    long p0, p1, p2, p3, p4, p5, p6;

    MpscBlockingConsumerArrayQueuePad2(long mask, E[] buffer)
    {
        super(mask, buffer);
    }
}

// $gen:ordered-fields
abstract class MpscBlockingConsumerArrayQueueProducerFields<E> extends MpscBlockingConsumerArrayQueuePad2<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(MpscBlockingConsumerArrayQueueProducerFields.class, "producerIndex");

    private volatile long producerIndex;

    MpscBlockingConsumerArrayQueueProducerFields(long mask, E[] buffer)
    {
        super(mask, buffer);
    }

    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    final void soProducerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

    final boolean casProducerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscBlockingConsumerArrayQueuePad3<E> extends MpscBlockingConsumerArrayQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    MpscBlockingConsumerArrayQueuePad3(long mask, E[] buffer)
    {
        super(mask, buffer);
    }
}

// $gen:ordered-fields
abstract class MpscBlockingConsumerArrayQueueConsumerFields<E> extends MpscBlockingConsumerArrayQueuePad3<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(MpscBlockingConsumerArrayQueueConsumerFields.class,"consumerIndex");
    private final static long BLOCKED_OFFSET = fieldOffset(MpscBlockingConsumerArrayQueueConsumerFields.class,"blocked");

    private volatile long consumerIndex;
    protected final long consumerMask;
    private volatile Thread blocked;
    protected final E[] consumerBuffer;

    MpscBlockingConsumerArrayQueueConsumerFields(long mask, E[] buffer)
    {
        super(mask, buffer);
        consumerMask = mask;
        consumerBuffer = buffer;
    }

    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }
    
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    final void soConsumerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
    
    final Thread lvBlocked()
    {
        return blocked;
    }
    
    final void soBlocked(Thread thread)
    {
        UNSAFE.putOrderedObject(this, BLOCKED_OFFSET, thread);
    }
}



/**
 *
 * @param <E>
 */
public class MpscBlockingConsumerArrayQueue<E> extends MpscBlockingConsumerArrayQueueConsumerFields<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    private static final int CONTINUE_TO_P_INDEX_CAS = 0;
    private static final int RETRY = 1;
    private static final int QUEUE_FULL = 2;


    public MpscBlockingConsumerArrayQueue(final int capacity)
    {
        // leave lower bit of mask clear
        super((long) ((Pow2.roundToPowerOfTwo(capacity) - 1) << 1), 
            (E[])allocate(Pow2.roundToPowerOfTwo(capacity)));
        
        RangeUtil.checkGreaterThanOrEqual(capacity, 1, "capacity");
        soProducerLimit((long) ((Pow2.roundToPowerOfTwo(capacity) - 1) << 1)); // we know it's all empty to start with
    }

    @Override
    public final Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int size()
    {
        // NOTE: because indices are on even numbers we cannot use the size util.

        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        long size;
        while (true)
        {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after)
            {
                size = ((currentProducerIndex - after) >> 1);
                break;
            }
        }
        // Long overflow is impossible, so size is always positive. Integer overflow is possible for the unbounded
        // indexed queues.
        if (size > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        else
        {
            return (int) size;
        }
    }

    @Override
    public final boolean isEmpty()
    {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return ((this.lvConsumerIndex()/2) == (this.lvProducerIndex()/2));
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }

        final long mask = this.producerMask;
        final E[] buffer = this.producerBuffer;
        long pIndex;
        while (true)
        {
            pIndex = lvProducerIndex();
            // lower bit is indicative of blocked consumer
            if ((pIndex & 1) == 1)
            {
                if (offerAndWakeup(buffer, mask, pIndex, e))
                    return true;
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1), consumer is awake
            final long producerLimit = lvProducerLimit();

            // Use producer limit to save a read of the more rapidly mutated consumer index.
            // Assumption: queue is usually empty or near empty
            if (producerLimit <= pIndex)
            {
                if (!recalculateProducerLimit(mask, pIndex, producerLimit))
                {
                    return false;
                }
            }

            // Claim the index
            if (casProducerIndex(pIndex, pIndex + 2))
            {
                break;
            }
        }
        final long offset = modifiedCalcElementOffset(pIndex, mask);
        // INDEX visible before ELEMENT
        soElement(buffer, offset, e); // release element e
        return true;
    }

    private boolean offerAndWakeup(E[] buffer, long mask, long pIndex, E e)
    {
        final long offset = modifiedCalcElementOffset(pIndex, mask);
        final Thread consumerThread = lvBlocked();

        // We could see a null here through a race with the consumer not yet storing the reference, or through a race
        // with another producer. Just retry.
        if (consumerThread == null)
        {
            return false;
        }
        
        // Claim the slot and the responsibility of unparking
        if(!casProducerIndex(pIndex, pIndex + 1))
        {
            return false;
        }
        
        soElement(buffer, offset, e);
        soBlocked(null); // releases the consumer from the park loop
        LockSupport.unpark(consumerThread);
        return true;
    }

    private boolean recalculateProducerLimit(long mask, long pIndex, long producerLimit)
    {
        final long cIndex = lvConsumerIndex();
        final long bufferCapacity = mask + 2;

        if (cIndex + bufferCapacity > pIndex)
        {
            casProducerLimit(producerLimit, cIndex + bufferCapacity); 
        }
        // full and cannot grow
        else if (pIndex - cIndex == bufferCapacity)
        {
            // offer should return false;
            return false;
        }
        else 
            throw new IllegalStateException();
        return true;
    }

    private void wakeupConsumer()
    {
        Thread consumerThread;
        do
        {
            consumerThread = lvBlocked();
        } while (consumerThread == null);
        soBlocked(null);
        LockSupport.unpark(consumerThread);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    public E take() throws InterruptedException
    {
        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;
        
        final long cIndex = lpConsumerIndex();
        final long offset = modifiedCalcElementOffset(cIndex, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null)
        {
            long pIndex = lvProducerIndex();
            if (cIndex == pIndex && casProducerIndex(pIndex, pIndex + 1))
            {
                boolean unblocked = false;
                try
                {
                    // producers only try a wakeup when both the index and the blocked thread are visible
                    soBlocked(Thread.currentThread());
                    do
                    {
                        LockSupport.park();
                        if (Thread.interrupted())
                        {
                            throw new InterruptedException();
                        }
                    }
                    while (lvBlocked() != null);
                    unblocked = true;
                }
                finally
                {
                    if (!unblocked) {
                        // revert blocking state
                        if (casProducerIndex(pIndex + 1, pIndex))
                        {
                            soBlocked(null);
                        }
                    }
                }
            }
            // producer index is visible before element, so if we wake up between the index moving and the element
            // store we could see a null.
            e = spinWaitForElement(buffer, offset);
        }

        soElement(buffer, offset, null); // release element null
        soConsumerIndex(cIndex + 2); // release cIndex
        
        return (E) e;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E poll()
    {
        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;

        final long index = lpConsumerIndex();
        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null)
        {
            // consumer can't see the odd producer index
            if (index != lvProducerIndex())
            {
                // poll() == null iff queue is empty, null element is not strong enough indicator, so we must
                // check the producer index. If the queue is indeed not empty we spin until element is
                // visible.
                e = spinWaitForElement(buffer, offset);
            }
            else
            {
                return null;
            }
        }

        soElement(buffer, offset, null); // release element null
        soConsumerIndex(index + 2); // release cIndex
        return (E) e;
    }

    private Object spinWaitForElement(E[] buffer, long offset)
    {
        Object e;
        do
        {
            e = lvElement(buffer, offset);
        }
        while (e == null);
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E peek()
    {
        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;

        final long index = lpConsumerIndex();
        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null && index != lvProducerIndex())
        {
            // peek() == null iff queue is empty, null element is not strong enough indicator, so we must
            // check the producer index. If the queue is indeed not empty we spin until element is visible.
            e = spinWaitForElement(buffer, offset);
        }
        
        return (E) e;
    }

    @Override
    public long currentProducerIndex()
    {
        return lvProducerIndex() / 2;
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex() / 2;
    }

    @Override
    public int capacity()
    {
        return (int) ((consumerMask + 2) >> 1);
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E relaxedPoll()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        E e = lvElement(buffer, offset);// LoadLoad
        if (e == null)
        {
            return null;
        }
        soElement(buffer, offset, null);
        soConsumerIndex(index + 2);
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        E e = lvElement(buffer, offset);// LoadLoad
        return e;
    }

    @Override
    public int fill(Supplier<E> s)
    {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = capacity();
        do
        {
            final int filled = fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH);
            if (filled == 0)
            {
                return (int) result;
            }
            result += filled;
        }
        while (result <= capacity);
        return (int) result;
    }

    @Override
    public int fill(Supplier<E> s, int batchSize)
    {
        if (batchSize == 0)
            return 0;
        
        final long mask = this.producerMask;
        final E[] buffer = this.producerBuffer;
        
        long pIndex;
        int claimedSlots;
        boolean wakeup = false;
        long batchIndex = 0;
        while (true)
        {
            pIndex = lvProducerIndex();
            long producerLimit = lvProducerLimit();
            
            // lower bit is indicative of blocked consumer
            if ((pIndex & 1) == 1)
            {
                if(!casProducerIndex(pIndex, pIndex + 1))
                {
                    continue;
                }
                // We've claimed pIndex, now we need to wake up consumer and set the element
                wakeup = true;
                batchIndex = pIndex + 1;
                pIndex = pIndex - 1;
                break;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1), consumer is awake

            // we want 'limit' slots, but will settle for whatever is visible to 'producerLimit'
            batchIndex = Math.min(producerLimit, pIndex + 2 * batchSize);

            // Use producer limit to save a read of the more rapidly mutated consumer index.
            // Assumption: queue is usually empty or near empty
            if (pIndex >= producerLimit || producerLimit < batchIndex)
            {
                if (!recalculateProducerLimit(mask, pIndex, producerLimit))
                {
                    return 0;
                }
                batchIndex = Math.min(lvProducerLimit(), pIndex + 2 * batchSize);
            }

            // Claim the index
            if (casProducerIndex(pIndex, batchIndex))
            {
                break;
            }
        }
        claimedSlots = (int) ((batchIndex - pIndex) / 2);
        
        // first element offset might be a wakeup, so peeled from loop
        for (int i = 0; i < claimedSlots; i++)
        {
            long offset = modifiedCalcElementOffset(pIndex + 2 * i, mask);
            soElement(buffer, offset, s.get());
        }
        
        if (wakeup)
        {
            wakeupConsumer();
        }
        
        return claimedSlots;
    }

    @Override
    public void fill(
        Supplier<E> s,
        WaitStrategy w,
        ExitCondition exit)
    {

        while (exit.keepRunning())
        {
            if (fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH) == 0)
            {
                int idleCounter = 0;
                while (exit.keepRunning() && fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH) == 0)
                {
                    idleCounter = w.idle(idleCounter);
                }
            }
        }
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return drain(c, capacity());
    }

    @Override
    public int drain(final Consumer<E> c, final int limit)
    {
        // Impl note: there are potentially some small gains to be had by manually inlining relaxedPoll() and hoisting
        // reused fields out to reduce redundant reads.
        int i = 0;
        E m;
        for (; i < limit && (m = relaxedPoll()) != null; i++)
        {
            c.accept(m);
        }
        return i;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy w, ExitCondition exit)
    {
        int idleCounter = 0;
        while (exit.keepRunning())
        {
            E e = relaxedPoll();
            if (e == null)
            {
                idleCounter = w.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            c.accept(e);
        }
    }
}
