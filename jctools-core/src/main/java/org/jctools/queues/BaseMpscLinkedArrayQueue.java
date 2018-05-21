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

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.queues.CircularArrayOffsetCalculator.allocate;
import static org.jctools.queues.LinkedArrayQueueUtil.length;
import static org.jctools.queues.LinkedArrayQueueUtil.modifiedCalcElementOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

abstract class BaseMpscLinkedArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

// $gen:ordered-fields
abstract class BaseMpscLinkedArrayQueueProducerFields<E> extends BaseMpscLinkedArrayQueuePad1<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(BaseMpscLinkedArrayQueueProducerFields.class, "producerIndex");

    private volatile long producerIndex;

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

abstract class BaseMpscLinkedArrayQueuePad2<E> extends BaseMpscLinkedArrayQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

// $gen:ordered-fields
abstract class BaseMpscLinkedArrayQueueConsumerFields<E> extends BaseMpscLinkedArrayQueuePad2<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(BaseMpscLinkedArrayQueueConsumerFields.class,"consumerIndex");

    private volatile long consumerIndex;
    protected long consumerMask;
    protected E[] consumerBuffer;

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
}

abstract class BaseMpscLinkedArrayQueuePad3<E> extends BaseMpscLinkedArrayQueueConsumerFields<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

// $gen:ordered-fields
abstract class BaseMpscLinkedArrayQueueColdProducerFields<E> extends BaseMpscLinkedArrayQueuePad3<E>
{
    private final static long P_LIMIT_OFFSET = fieldOffset(BaseMpscLinkedArrayQueueColdProducerFields.class,"producerLimit");

    private volatile long producerLimit;
    protected long producerMask;
    protected E[] producerBuffer;

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


/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in linked chunks
 * of the initial size. The queue grows only when the current buffer is full and elements are not copied on
 * resize, instead a link to the new buffer is stored in the old buffer for the consumer to follow.<br>
 *
 * @param <E>
 */
public abstract class BaseMpscLinkedArrayQueue<E> extends BaseMpscLinkedArrayQueueColdProducerFields<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    // No post padding here, subclasses must add
    private static final Object JUMP = new Object();
    private static final int CONTINUE_TO_P_INDEX_CAS = 0;
    private static final int RETRY = 1;
    private static final int QUEUE_FULL = 2;
    private static final int QUEUE_RESIZE = 3;


    /**
     * @param initialCapacity the queue initial capacity. If chunk size is fixed this will be the chunk size.
     *                        Must be 2 or more.
     */
    public BaseMpscLinkedArrayQueue(final int initialCapacity)
    {
        RangeUtil.checkGreaterThanOrEqual(initialCapacity, 2, "initialCapacity");

        int p2capacity = Pow2.roundToPowerOfTwo(initialCapacity);
        // leave lower bit of mask clear
        long mask = (p2capacity - 1) << 1;
        // need extra element to point at next array
        E[] buffer = allocate(p2capacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        soProducerLimit(mask); // we know it's all empty to start with
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
        return (this.lvConsumerIndex() == this.lvProducerIndex());
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

        long mask;
        E[] buffer;
        long pIndex;

        while (true)
        {
            long producerLimit = lvProducerLimit();
            pIndex = lvProducerIndex();
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            if ((pIndex & 1) == 1)
            {
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1)

            // mask/buffer may get changed by resizing -> only use for array access after successful CAS.
            mask = this.producerMask;
            buffer = this.producerBuffer;
            // a successful CAS ties the ordering, lv(pIndex) - [mask/buffer] -> cas(pIndex)

            // assumption behind this optimization is that queue is almost always empty or near empty
            if (producerLimit <= pIndex)
            {
                int result = offerSlowPath(mask, pIndex, producerLimit);
                switch (result)
                {
                    case CONTINUE_TO_P_INDEX_CAS:
                        break;
                    case RETRY:
                        continue;
                    case QUEUE_FULL:
                        return false;
                    case QUEUE_RESIZE:
                        resize(mask, buffer, pIndex, e);
                        return true;
                }
            }

            if (casProducerIndex(pIndex, pIndex + 2))
            {
                break;
            }
        }
        // INDEX visible before ELEMENT
        final long offset = modifiedCalcElementOffset(pIndex, mask);
        soElement(buffer, offset, e); // release element e
        return true;
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
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null)
        {
            if (index != lvProducerIndex())
            {
                // poll() == null iff queue is empty, null element is not strong enough indicator, so we must
                // check the producer index. If the queue is indeed not empty we spin until element is
                // visible.
                do
                {
                    e = lvElement(buffer, offset);
                }
                while (e == null);
            }
            else
            {
                return null;
            }
        }

        if (e == JUMP)
        {
            final E[] nextBuffer = getNextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }

        soElement(buffer, offset, null); // release element null
        soConsumerIndex(index + 2); // release cIndex
        return (E) e;
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
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null && index != lvProducerIndex())
        {
            // peek() == null iff queue is empty, null element is not strong enough indicator, so we must
            // check the producer index. If the queue is indeed not empty we spin until element is visible.
            do
            {
                e = lvElement(buffer, offset);
            }
            while (e == null);
        }
        if (e == JUMP)
        {
            return newBufferPeek(getNextBuffer(buffer, mask), index);
        }
        return (E) e;
    }

    /**
     * We do not inline resize into this method because we do not resize on fill.
     */
    private int offerSlowPath(long mask, long pIndex, long producerLimit)
    {
        final long cIndex = lvConsumerIndex();
        long bufferCapacity = getCurrentBufferCapacity(mask);

        if (cIndex + bufferCapacity > pIndex)
        {
            if (!casProducerLimit(producerLimit, cIndex + bufferCapacity))
            {
                // retry from top
                return RETRY;
            }
            else
            {
                // continue to pIndex CAS
                return CONTINUE_TO_P_INDEX_CAS;
            }
        }
        // full and cannot grow
        else if (availableInQueue(pIndex, cIndex) <= 0)
        {
            // offer should return false;
            return QUEUE_FULL;
        }
        // grab index for resize -> set lower bit
        else if (casProducerIndex(pIndex, pIndex + 1))
        {
            // trigger a resize
            return QUEUE_RESIZE;
        }
        else
        {
            // failed resize attempt, retry from top
            return RETRY;
        }
    }

    /**
     * @return available elements in queue * 2
     */
    protected abstract long availableInQueue(long pIndex, long cIndex);

    @SuppressWarnings("unchecked")
    private E[] getNextBuffer(final E[] buffer, final long mask)
    {
        final long offset = nextArrayOffset(mask);
        final E[] nextBuffer = (E[]) lvElement(buffer, offset);
        soElement(buffer, offset, null);
        return nextBuffer;
    }

    private long nextArrayOffset(long mask)
    {
        return modifiedCalcElementOffset(mask + 2, Long.MAX_VALUE);
    }

    private E newBufferPoll(E[] nextBuffer, long index)
    {
        final long offset = newBufferAndOffset(nextBuffer, index);
        final E n = lvElement(nextBuffer, offset);// LoadLoad
        if (n == null)
        {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        soElement(nextBuffer, offset, null);// StoreStore
        soConsumerIndex(index + 2);
        return n;
    }

    private E newBufferPeek(E[] nextBuffer, long index)
    {
        final long offset = newBufferAndOffset(nextBuffer, index);
        final E n = lvElement(nextBuffer, offset);// LoadLoad
        if (null == n)
        {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        return n;
    }

    private long newBufferAndOffset(E[] nextBuffer, long index)
    {
        consumerBuffer = nextBuffer;
        consumerMask = (length(nextBuffer) - 2) << 1;
        return modifiedCalcElementOffset(index, consumerMask);
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
    public abstract int capacity();

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
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null)
        {
            return null;
        }
        if (e == JUMP)
        {
            final E[] nextBuffer = getNextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }
        soElement(buffer, offset, null);
        soConsumerIndex(index + 2);
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    @Override
    public E relaxedPeek()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == JUMP)
        {
            return newBufferPeek(getNextBuffer(buffer, mask), index);
        }
        return (E) e;
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
        long mask;
        E[] buffer;
        long pIndex;
        int claimedSlots;
        while (true)
        {
            long producerLimit = lvProducerLimit();
            pIndex = lvProducerIndex();
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            if ((pIndex & 1) == 1)
            {
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1)

            // NOTE: mask/buffer may get changed by resizing -> only use for array access after successful CAS.
            // Only by virtue offloading them between the lvProducerIndex and a successful casProducerIndex are they
            // safe to use.
            mask = this.producerMask;
            buffer = this.producerBuffer;
            // a successful CAS ties the ordering, lv(pIndex) -> [mask/buffer] -> cas(pIndex)

            // we want 'limit' slots, but will settle for whatever is visible to 'producerLimit'
            long batchIndex = Math.min(producerLimit, pIndex + 2 * batchSize);

            if (pIndex >= producerLimit || producerLimit < batchIndex)
            {
                int result = offerSlowPath(mask, pIndex, producerLimit);
                switch (result)
                {
                    case CONTINUE_TO_P_INDEX_CAS:
                        // offer slow path verifies only one slot ahead, we cannot rely on indication here
                    case RETRY:
                        continue;
                    case QUEUE_FULL:
                        return 0;
                    case QUEUE_RESIZE:
                        resize(mask, buffer, pIndex, s.get());
                        return 1;
                }
            }

            // claim limit slots at once
            if (casProducerIndex(pIndex, batchIndex))
            {
                claimedSlots = (int) ((batchIndex - pIndex) / 2);
                break;
            }
        }

        for (int i = 0; i < claimedSlots; i++)
        {
            final long offset = modifiedCalcElementOffset(pIndex + 2 * i, mask);
            soElement(buffer, offset, s.get());
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

    private void resize(long oldMask, E[] oldBuffer, long pIndex, E e)
    {
        int newBufferLength = getNextBufferSize(oldBuffer);
        final E[] newBuffer = allocate(newBufferLength);

        producerBuffer = newBuffer;
        final int newMask = (newBufferLength - 2) << 1;
        producerMask = newMask;

        final long offsetInOld = modifiedCalcElementOffset(pIndex, oldMask);
        final long offsetInNew = modifiedCalcElementOffset(pIndex, newMask);

        soElement(newBuffer, offsetInNew, e);// element in new array
        soElement(oldBuffer, nextArrayOffset(oldMask), newBuffer);// buffer linked

        // ASSERT code
        final long cIndex = lvConsumerIndex();
        final long availableInQueue = availableInQueue(pIndex, cIndex);
        RangeUtil.checkPositive(availableInQueue, "availableInQueue");

        // Invalidate racing CASs
        // We never set the limit beyond the bounds of a buffer
        soProducerLimit(pIndex + Math.min(newMask, availableInQueue));

        // make resize visible to the other producers
        soProducerIndex(pIndex + 2);

        // INDEX visible before ELEMENT, consistent with consumer expectation

        // make resize visible to consumer
        soElement(oldBuffer, offsetInOld, JUMP);
    }

    /**
     * @return next buffer size(inclusive of next array pointer)
     */
    protected abstract int getNextBufferSize(E[] buffer);

    /**
     * @return current buffer capacity for elements (excluding next pointer and jump entry) * 2
     */
    protected abstract long getCurrentBufferCapacity(long mask);
}
