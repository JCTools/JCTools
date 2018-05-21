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

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

abstract class SpscArrayQueueColdField<E> extends ConcurrentCircularArrayQueue<E>
{
    public static final int MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);
    final int lookAheadStep;

    SpscArrayQueueColdField(int capacity)
    {
        super(capacity);
        lookAheadStep = Math.min(capacity() / 4, MAX_LOOK_AHEAD_STEP);
    }
}

abstract class SpscArrayQueueL1Pad<E> extends SpscArrayQueueColdField<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    SpscArrayQueueL1Pad(int capacity)
    {
        super(capacity);
    }
}

// $gen:ordered-fields
abstract class SpscArrayQueueProducerIndexFields<E> extends SpscArrayQueueL1Pad<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(SpscArrayQueueProducerIndexFields.class, "producerIndex");

    private volatile long producerIndex;
    protected long producerLimit;

    SpscArrayQueueProducerIndexFields(int capacity)
    {
        super(capacity);
    }

    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }
    
    final long lpProducerIndex()
    {
        return UNSAFE.getLong(this, P_INDEX_OFFSET);
    }

    final void soProducerIndex(final long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

}

abstract class SpscArrayQueueL2Pad<E> extends SpscArrayQueueProducerIndexFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    SpscArrayQueueL2Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class SpscArrayQueueConsumerIndexField<E> extends SpscArrayQueueL2Pad<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(SpscArrayQueueConsumerIndexField.class, "consumerIndex");

    private volatile long consumerIndex;

    SpscArrayQueueConsumerIndexField(int capacity)
    {
        super(capacity);
    }

    public final long lvConsumerIndex()
    {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }
    
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }
    
    final void soConsumerIndex(final long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
}

abstract class SpscArrayQueueL3Pad<E> extends SpscArrayQueueConsumerIndexField<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    SpscArrayQueueL3Pad(int capacity)
    {
        super(capacity);
    }
}


/**
 * A Single-Producer-Single-Consumer queue backed by a pre-allocated buffer.
 * <p>
 * This implementation is a mashup of the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * algorithm with an optimization of the offer method taken from the <a
 * href="http://staff.ustc.edu.cn/~bhua/publications/IJPP_draft.pdf">BQueue</a> algorithm (a variation on Fast
 * Flow), and adjusted to comply with Queue.offer semantics with regards to capacity.<br>
 * For convenience the relevant papers are available in the resources folder:<br>
 * <i>2010 - Pisa - SPSC Queues on Shared Cache Multi-Core Systems.pdf<br>
 * 2012 - Junchang- BQueue- Efﬁcient and Practical Queuing.pdf <br>
 * </i> This implementation is wait free.
 *
 * @param <E>
 * @author nitsanw
 */
public class SpscArrayQueue<E> extends SpscArrayQueueL3Pad<E>
{

    public SpscArrayQueue(final int capacity)
    {
        super(Math.max(capacity, 4));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long producerIndex = this.lpProducerIndex();

        if (producerIndex >= producerLimit &&
            !offerSlowPath(buffer, mask, producerIndex))
        {
            return false;
        }
        final long offset = calcElementOffset(producerIndex, mask);

        soElement(buffer, offset, e); // StoreStore
        soProducerIndex(producerIndex + 1); // ordered store -> atomic and ordered for size()
        return true;
    }

    private boolean offerSlowPath(final E[] buffer, final long mask, final long producerIndex)
    {
        final int lookAheadStep = this.lookAheadStep;
        if (null == lvElement(buffer, calcElementOffset(producerIndex + lookAheadStep, mask)))
        {// LoadLoad
            producerLimit = producerIndex + lookAheadStep;
        }
        else
        {
            final long offset = calcElementOffset(producerIndex, mask);
            if (null != lvElement(buffer, offset))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E poll()
    {
        final long consumerIndex = this.lpConsumerIndex();
        final long offset = calcElementOffset(consumerIndex);
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = this.buffer;
        final E e = lvElement(buffer, offset);// LoadLoad
        if (null == e)
        {
            return null;
        }
        soElement(buffer, offset, null);// StoreStore
        soConsumerIndex(consumerIndex + 1); // ordered store -> atomic and ordered for size()
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E peek()
    {
        return lvElement(buffer, calcElementOffset(lpConsumerIndex()));
    }

    @Override
    public boolean relaxedOffer(final E message)
    {
        return offer(message);
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
    public int drain(final Consumer<E> c)
    {
        return drain(c, capacity());
    }

    @Override
    public int fill(final Supplier<E> s)
    {
        return fill(s, capacity());
    }

    @Override
    public int drain(final Consumer<E> c, final int limit)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long consumerIndex = this.lpConsumerIndex();

        for (int i = 0; i < limit; i++)
        {
            final long index = consumerIndex + i;
            final long offset = calcElementOffset(index, mask);
            final E e = lvElement(buffer, offset);// LoadLoad
            if (null == e)
            {
                return i;
            }
            soElement(buffer, offset, null);// StoreStore
            soConsumerIndex(index + 1); // ordered store -> atomic and ordered for size()
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(final Supplier<E> s, final int limit)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final int lookAheadStep = this.lookAheadStep;
        final long producerIndex = this.lpProducerIndex();

        for (int i = 0; i < limit; i++)
        {
            final long index = producerIndex + i;
            final long lookAheadElementOffset = calcElementOffset(index + lookAheadStep, mask);
            if (null == lvElement(buffer, lookAheadElementOffset))
            {// LoadLoad
                int lookAheadLimit = Math.min(lookAheadStep, limit - i);
                for (int j = 0; j < lookAheadLimit; j++)
                {
                    final long offset = calcElementOffset(index + j, mask);
                    soElement(buffer, offset, s.get()); // StoreStore
                    soProducerIndex(index + j + 1); // ordered store -> atomic and ordered for size()
                }
                i += lookAheadLimit - 1;
            }
            else
            {
                final long offset = calcElementOffset(index, mask);
                if (null != lvElement(buffer, offset))
                {
                    return i;
                }
                soElement(buffer, offset, s.get()); // StoreStore
                soProducerIndex(index + 1); // ordered store -> atomic and ordered for size()
            }

        }
        return limit;
    }

    @Override
    public void drain(final Consumer<E> c, final WaitStrategy w, final ExitCondition exit)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long consumerIndex = this.lpConsumerIndex();

        int counter = 0;
        while (exit.keepRunning())
        {
            for (int i = 0; i < 4096; i++)
            {
                final long offset = calcElementOffset(consumerIndex, mask);
                final E e = lvElement(buffer, offset);// LoadLoad
                if (null == e)
                {
                    counter = w.idle(counter);
                    continue;
                }
                consumerIndex++;
                counter = 0;
                soElement(buffer, offset, null);// StoreStore
                soConsumerIndex(consumerIndex); // ordered store -> atomic and ordered for size()
                c.accept(e);
            }
        }
    }

    @Override
    public void fill(final Supplier<E> s, final WaitStrategy w, final ExitCondition e)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final int lookAheadStep = this.lookAheadStep;
        long producerIndex = this.lpProducerIndex();
        int counter = 0;
        while (e.keepRunning())
        {
            final long lookAheadElementOffset = calcElementOffset(producerIndex + lookAheadStep, mask);
            if (null == lvElement(buffer, lookAheadElementOffset))
            {// LoadLoad
                for (int j = 0; j < lookAheadStep; j++)
                {
                    final long offset = calcElementOffset(producerIndex, mask);
                    producerIndex++;
                    soElement(buffer, offset, s.get()); // StoreStore
                    soProducerIndex(producerIndex); // ordered store -> atomic and ordered for size()
                }
            }
            else
            {
                final long offset = calcElementOffset(producerIndex, mask);
                if (null != lvElement(buffer, offset))
                {// LoadLoad
                    counter = w.idle(counter);
                    continue;
                }
                producerIndex++;
                counter = 0;
                soElement(buffer, offset, s.get()); // StoreStore
                soProducerIndex(producerIndex); // ordered store -> atomic and ordered for size()
            }
        }
    }
}
