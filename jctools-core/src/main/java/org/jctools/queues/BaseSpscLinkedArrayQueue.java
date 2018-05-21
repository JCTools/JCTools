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

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.queues.CircularArrayOffsetCalculator.calcElementOffset;
import static org.jctools.queues.LinkedArrayQueueUtil.length;
import static org.jctools.queues.LinkedArrayQueueUtil.nextArrayOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

abstract class BaseSpscLinkedArrayQueuePrePad<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15;
    //  p16, p17; drop 2 longs, the cold fields act as buffer
}

abstract class BaseSpscLinkedArrayQueueConsumerColdFields<E> extends BaseSpscLinkedArrayQueuePrePad<E>
{
    protected long consumerMask;
    protected E[] consumerBuffer;
}

// $gen:ordered-fields
abstract class BaseSpscLinkedArrayQueueConsumerField<E> extends BaseSpscLinkedArrayQueueConsumerColdFields<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(BaseSpscLinkedArrayQueueConsumerField.class, "consumerIndex");

    private volatile long consumerIndex;

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

abstract class BaseSpscLinkedArrayQueueL2Pad<E> extends BaseSpscLinkedArrayQueueConsumerField<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

// $gen:ordered-fields
abstract class BaseSpscLinkedArrayQueueProducerFields<E> extends BaseSpscLinkedArrayQueueL2Pad<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(BaseSpscLinkedArrayQueueProducerFields.class,"producerIndex");

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

    final long lpProducerIndex()
    {
        return UNSAFE.getLong(this, P_INDEX_OFFSET);
    }
    
}

abstract class BaseSpscLinkedArrayQueueProducerColdFields<E> extends BaseSpscLinkedArrayQueueProducerFields<E>
{
    protected long producerBufferLimit;
    protected long producerMask; // fixed for chunked and unbounded

    protected E[] producerBuffer;
}

abstract class BaseSpscLinkedArrayQueue<E> extends BaseSpscLinkedArrayQueueProducerColdFields<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{

    private static final Object JUMP = new Object();

    @Override
    public final Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int size()
    {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public final boolean isEmpty()
    {
        return IndexedQueueSizeUtil.isEmpty(this);
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    @Override
    public long currentProducerIndex()
    {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex();
    }

    protected final void soNext(E[] curr, E[] next)
    {
        long offset = nextArrayOffset(curr);
        soElement(curr, offset, next);
    }

    @SuppressWarnings("unchecked")
    protected final E[] lvNextArrayAndUnlink(E[] curr)
    {
        final long offset = nextArrayOffset(curr);
        final E[] nextBuffer = (E[]) lvElement(curr, offset);
        // prevent GC nepotism
        soElement(curr, offset, null);
        return nextBuffer;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @Override
    public E relaxedPoll()
    {
        return poll();
    }

    @Override
    public E relaxedPeek()
    {
        return peek();
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return MessagePassingQueueUtil.drain(this, c);
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
    public int drain(Consumer<E> c, int limit)
    {
        return MessagePassingQueueUtil.drain(this, c, limit);
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        for (int i = 0; i < limit; i++)
        {
            // local load of field to avoid repeated loads after volatile reads
            final E[] buffer = producerBuffer;
            final long index = lpProducerIndex();
            final long mask = producerMask;
            final long offset = calcElementOffset(index, mask);
            // expected hot path
            if (index < producerBufferLimit)
            {
                writeToQueue(buffer, s.get(), index, offset);
            }
            else
            {
                if (!offerColdPath(buffer, mask, index, offset, null, s))
                {
                    return i;
                }
            }
        }
        return limit;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, wait, exit);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        while (exit.keepRunning())
        {
            while (fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH) != 0 && exit.keepRunning())
            {
                continue;
            }
            int idleCounter = 0;
            while (exit.keepRunning() && fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH) == 0)
            {
                idleCounter = wait.idle(idleCounter);
            }

        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e)
    {
        // Objects.requireNonNull(e);
        if (null == e)
        {
            throw new NullPointerException();
        }
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = producerBuffer;
        final long index = lpProducerIndex();
        final long mask = producerMask;
        final long offset = calcElementOffset(index, mask);
        // expected hot path
        if (index < producerBufferLimit)
        {
            writeToQueue(buffer, e, index, offset);
            return true;
        }
        return offerColdPath(buffer, mask, index, offset, e, null);
    }

    abstract boolean offerColdPath(
        E[] buffer,
        long mask,
        long pIndex,
        long offset,
        E v,
        Supplier<? extends E> s);

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E poll()
    {
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;
        final long offset = calcElementOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        boolean isNextBuffer = e == JUMP;
        if (null != e && !isNextBuffer)
        {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(buffer, offset, null);
            return (E) e;
        }
        else if (isNextBuffer)
        {
            return newBufferPoll(buffer, index);
        }

        return null;
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
        final long offset = calcElementOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (e == JUMP)
        {
            return newBufferPeek(buffer, index);
        }

        return (E) e;
    }

    final void linkOldToNew(
        final long currIndex,
        final E[] oldBuffer, final long offset,
        final E[] newBuffer, final long offsetInNew,
        final E e)
    {
        soElement(newBuffer, offsetInNew, e);// StoreStore
        // link to next buffer and add next indicator as element of old buffer
        soNext(oldBuffer, newBuffer);
        soElement(oldBuffer, offset, JUMP);
        // index is visible after elements (isEmpty/poll ordering)
        soProducerIndex(currIndex + 1);// this ensures atomic write of long on 32bit platforms
    }

    final void writeToQueue(final E[] buffer, final E e, final long index, final long offset)
    {
        soElement(buffer, offset, e);// StoreStore
        soProducerIndex(index + 1);// this ensures atomic write of long on 32bit platforms
    }

    private E newBufferPeek(final E[] buffer, final long index)
    {
        E[] nextBuffer = lvNextArrayAndUnlink(buffer);
        consumerBuffer = nextBuffer;
        final long mask = length(nextBuffer) - 2;
        consumerMask = mask;
        final long offset = calcElementOffset(index, mask);
        return lvElement(nextBuffer, offset);// LoadLoad
    }

    private E newBufferPoll(final E[] buffer, final long index)
    {
        E[] nextBuffer = lvNextArrayAndUnlink(buffer);
        consumerBuffer = nextBuffer;
        final long mask = length(nextBuffer) - 2;
        consumerMask = mask;
        final long offset = calcElementOffset(index, mask);
        final E n = lvElement(nextBuffer, offset);// LoadLoad
        if (null == n)
        {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        else
        {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(nextBuffer, offset, null);// StoreStore
            return n;
        }
    }
}
