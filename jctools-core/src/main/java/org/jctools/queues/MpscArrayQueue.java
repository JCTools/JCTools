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

import org.jctools.util.PortableJvmInfo;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class MpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E>
{
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;

    MpscArrayQueueL1Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpscArrayQueueProducerIndexField<E> extends MpscArrayQueueL1Pad<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(MpscArrayQueueProducerIndexField.class, "producerIndex");

    private volatile long producerIndex;

    MpscArrayQueueProducerIndexField(int capacity)
    {
        super(capacity);
    }

    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    final boolean casProducerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscArrayQueueMidPad<E> extends MpscArrayQueueProducerIndexField<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    MpscArrayQueueMidPad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpscArrayQueueProducerLimitField<E> extends MpscArrayQueueMidPad<E>
{
    private final static long P_LIMIT_OFFSET = fieldOffset(MpscArrayQueueProducerLimitField.class, "producerLimit");

    // First unavailable index the producer may claim up to before rereading the consumer index
    private volatile long producerLimit;

    MpscArrayQueueProducerLimitField(int capacity)
    {
        super(capacity);
        this.producerLimit = capacity;
    }

    final long lvProducerLimit()
    {
        return producerLimit;
    }

    final void soProducerLimit(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, newValue);
    }
}

abstract class MpscArrayQueueL2Pad<E> extends MpscArrayQueueProducerLimitField<E>
{
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;

    MpscArrayQueueL2Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpscArrayQueueConsumerIndexField<E> extends MpscArrayQueueL2Pad<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(MpscArrayQueueConsumerIndexField.class, "consumerIndex");

    private volatile long consumerIndex;

    MpscArrayQueueConsumerIndexField(int capacity)
    {
        super(capacity);
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
}

abstract class MpscArrayQueueL3Pad<E> extends MpscArrayQueueConsumerIndexField<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    MpscArrayQueueL3Pad(int capacity)
    {
        super(capacity);
    }
}

/**
 * A Multi-Producer-Single-Consumer queue based on a {@link org.jctools.queues.ConcurrentCircularArrayQueue}. This
 * implies that any thread may call the offer method, but only a single thread may call poll/peek for correctness to
 * maintained. <br>
 * This implementation follows patterns documented on the package level for False Sharing protection.<br>
 * This implementation is using the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * method for polling from the queue (with minor change to correctly publish the index) and an extension of
 * the Leslie Lamport concurrent queue algorithm (originated by Martin Thompson) on the producer side.<br>
 *
 * @param <E>
 * @author nitsanw
 */
public class MpscArrayQueue<E> extends MpscArrayQueueL3Pad<E>
{

    public MpscArrayQueue(final int capacity)
    {
        super(capacity);
    }

    /**
     * {@link #offer}} if {@link #size()} is less than threshold.
     *
     * @param e         the object to offer onto the queue, not null
     * @param threshold the maximum allowable size
     * @return true if the offer is successful, false if queue size exceeds threshold
     * @since 1.0.1
     */
    public boolean offerIfBelowThreshold(final E e, int threshold)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final long mask = this.mask;
        final long capacity = mask + 1;

        long producerLimit = lvProducerLimit(); // LoadLoad
        long pIndex;
        do
        {
            pIndex = lvProducerIndex(); // LoadLoad
            long available = producerLimit - pIndex;
            long size = capacity - available;
            if (size >= threshold)
            {
                final long cIndex = lvConsumerIndex(); // LoadLoad
                size = pIndex - cIndex;
                if (size >= threshold)
                {
                    return false; // the size exceeds threshold
                }
                else
                {
                    // update producer limit to the next index that we must recheck the consumer index
                    producerLimit = cIndex + capacity;

                    // this is racy, but the race is benign
                    soProducerLimit(producerLimit);
                }
            }
        }
        while (!casProducerIndex(pIndex, pIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to poll() we would need to handle the case where the element is not visible.
         */

        // Won CAS, move on to storing
        final long offset = calcElementOffset(pIndex, mask);
        soElement(buffer, offset, e); // StoreStore
        return true; // AWESOME :)
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free offer using a single CAS. As class name suggests access is permitted to many threads
     * concurrently.
     *
     * @see java.util.Queue#offer
     * @see org.jctools.queues.MessagePassingQueue#offer
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }

        // use a cached view on consumer index (potentially updated in loop)
        final long mask = this.mask;
        long producerLimit = lvProducerLimit(); // LoadLoad
        long pIndex;
        do
        {
            pIndex = lvProducerIndex(); // LoadLoad
            if (pIndex >= producerLimit)
            {
                final long cIndex = lvConsumerIndex(); // LoadLoad
                producerLimit = cIndex + mask + 1;

                if (pIndex >= producerLimit)
                {
                    return false; // FULL :(
                }
                else
                {
                    // update producer limit to the next index that we must recheck the consumer index
                    // this is racy, but the race is benign
                    soProducerLimit(producerLimit);
                }
            }
        }
        while (!casProducerIndex(pIndex, pIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to poll() we would need to handle the case where the element is not visible.
         */

        // Won CAS, move on to storing
        final long offset = calcElementOffset(pIndex, mask);
        soElement(buffer, offset, e); // StoreStore
        return true; // AWESOME :)
    }

    /**
     * A wait free alternative to offer which fails on CAS failure.
     *
     * @param e new element, not null
     * @return 1 if next element cannot be filled, -1 if CAS failed, 0 if successful
     */
    public final int failFastOffer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final long mask = this.mask;
        final long capacity = mask + 1;
        final long pIndex = lvProducerIndex(); // LoadLoad
        long producerLimit = lvProducerLimit(); // LoadLoad
        if (pIndex >= producerLimit)
        {
            final long cIndex = lvConsumerIndex(); // LoadLoad
            producerLimit = cIndex + capacity;
            if (pIndex >= producerLimit)
            {
                return 1; // FULL :(
            }
            else
            {
                // update producer limit to the next index that we must recheck the consumer index
                soProducerLimit(producerLimit); // StoreLoad
            }
        }

        // look Ma, no loop!
        if (!casProducerIndex(pIndex, pIndex + 1))
        {
            return -1; // CAS FAIL :(
        }

        // Won CAS, move on to storing
        final long offset = calcElementOffset(pIndex, mask);
        soElement(buffer, offset, e);
        return 0; // AWESOME :)
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free poll using ordered loads/stores. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll
     * @see org.jctools.queues.MessagePassingQueue#poll
     */
    @Override
    public E poll()
    {
        final long cIndex = lpConsumerIndex();
        final long offset = calcElementOffset(cIndex);
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        // If we can't see the next available element we can't poll
        E e = lvElement(buffer, offset); // LoadLoad
        if (null == e)
        {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (cIndex != lvProducerIndex())
            {
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

        spElement(buffer, offset, null);
        soConsumerIndex(cIndex + 1); // StoreStore
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free peek using ordered loads. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll
     * @see org.jctools.queues.MessagePassingQueue#poll
     */
    @Override
    public E peek()
    {
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        final long cIndex = lpConsumerIndex(); // LoadLoad
        final long offset = calcElementOffset(cIndex);
        E e = lvElement(buffer, offset);
        if (null == e)
        {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (cIndex != lvProducerIndex())
            {
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
        return e;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @Override
    public E relaxedPoll()
    {
        final E[] buffer = this.buffer;
        final long cIndex = lpConsumerIndex();
        final long offset = calcElementOffset(cIndex);

        // If we can't see the next available element we can't poll
        E e = lvElement(buffer, offset); // LoadLoad
        if (null == e)
        {
            return null;
        }

        spElement(buffer, offset, null);
        soConsumerIndex(cIndex + 1); // StoreStore
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long cIndex = lpConsumerIndex();
        return lvElement(buffer, calcElementOffset(cIndex, mask));
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return drain(c, capacity());
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
    public int drain(final Consumer<E> c, final int limit)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long cIndex = lpConsumerIndex();

        for (int i = 0; i < limit; i++)
        {
            final long index = cIndex + i;
            final long offset = calcElementOffset(index, mask);
            final E e = lvElement(buffer, offset);// LoadLoad
            if (null == e)
            {
                return i;
            }
            spElement(buffer, offset, null);
            soConsumerIndex(index + 1); // ordered store -> atomic and ordered for size()
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        final long mask = this.mask;
        final long capacity = mask + 1;
        long producerLimit = lvProducerLimit(); // LoadLoad
        long pIndex;
        int actualLimit = 0;
        do
        {
            pIndex = lvProducerIndex(); // LoadLoad
            long available = producerLimit - pIndex;
            if (available <= 0)
            {
                final long cIndex = lvConsumerIndex(); // LoadLoad
                producerLimit = cIndex + capacity;
                available = producerLimit - pIndex;
                if (available <= 0)
                {
                    return 0; // FULL :(
                }
                else
                {
                    // update producer limit to the next index that we must recheck the consumer index
                    soProducerLimit(producerLimit); // StoreLoad
                }
            }
            actualLimit = Math.min((int) available, limit);
        }
        while (!casProducerIndex(pIndex, pIndex + actualLimit));
        // right, now we claimed a few slots and can fill them with goodness
        final E[] buffer = this.buffer;
        for (int i = 0; i < actualLimit; i++)
        {
            // Won CAS, move on to storing
            final long offset = calcElementOffset(pIndex + i, mask);
            soElement(buffer, offset, s.get());
        }
        return actualLimit;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy w, ExitCondition exit)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long cIndex = lpConsumerIndex();

        int counter = 0;
        while (exit.keepRunning())
        {
            for (int i = 0; i < 4096; i++)
            {
                final long offset = calcElementOffset(cIndex, mask);
                final E e = lvElement(buffer, offset);// LoadLoad
                if (null == e)
                {
                    counter = w.idle(counter);
                    continue;
                }
                cIndex++;
                counter = 0;
                spElement(buffer, offset, null);
                soConsumerIndex(cIndex); // ordered store -> atomic and ordered for size()
                c.accept(e);
            }
        }
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy w, ExitCondition exit)
    {
        int idleCounter = 0;
        while (exit.keepRunning())
        {
            if (fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH) == 0)
            {
                idleCounter = w.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
        }
    }
}
