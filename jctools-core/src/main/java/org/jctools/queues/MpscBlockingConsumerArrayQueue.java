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
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;

import static org.jctools.queues.LinkedArrayQueueUtil.modifiedCalcCircularRefElementOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class MpscBlockingConsumerArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
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
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    // byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b

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
 * This is a partial implementation of the {@link java.util.concurrent.BlockingQueue} on the consumer side only on top
 * of the mechanics described in {@link BaseMpscLinkedArrayQueue}, but with the reservation bit used for blocking rather
 * than resizing in this instance.
 */
public class MpscBlockingConsumerArrayQueue<E> extends MpscBlockingConsumerArrayQueueConsumerFields<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators, BlockingQueue<E>
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
    private static final int CONTINUE_TO_P_INDEX_CAS = 0;
    private static final int RETRY = 1;
    private static final int QUEUE_FULL = 2;


    public MpscBlockingConsumerArrayQueue(final int capacity)
    {
        // leave lower bit of mask clear
        super((long) ((Pow2.roundToPowerOfTwo(capacity) - 1) << 1), (E[]) allocateRefArray(Pow2.roundToPowerOfTwo(capacity)));

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
        final long offset = modifiedCalcCircularRefElementOffset(pIndex, mask);
        // INDEX visible before ELEMENT
        soRefElement(buffer, offset, e); // release element e
        return true;
    }

    @Override
    public void put(E e) throws InterruptedException
    {
        if (!offer(e))
            throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException
    {
        if (offer(e))
            return true;
        throw new UnsupportedOperationException();
    }

    private boolean offerAndWakeup(E[] buffer, long mask, long pIndex, E e)
    {
        final long offset = modifiedCalcCircularRefElementOffset(pIndex, mask);
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

        soRefElement(buffer, offset, e);
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
        final long offset = modifiedCalcCircularRefElementOffset(cIndex, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null)
        {
            final long pIndex = lvProducerIndex();
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

        soRefElement(buffer, offset, null); // release element null
        soConsumerIndex(cIndex + 2); // release cIndex

        return (E) e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    public E poll(long timeout, TimeUnit unit) throws InterruptedException
    {
        long remainingNanos = unit.toNanos(timeout);
        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;

        final long cIndex = lpConsumerIndex();
        final long offset = modifiedCalcCircularRefElementOffset(cIndex, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null)
        {
            if (remainingNanos <= 0)
            {
                return null;
            }
            final long pIndex = lvProducerIndex();
            if (cIndex == pIndex && casProducerIndex(pIndex, pIndex + 1))
            {
                boolean unblocked = false;
                try
                {
                    // producers only try a wakeup when both the index and the blocked thread are visible
                    soBlocked(Thread.currentThread());
                    final long deadlineNanos = System.nanoTime() + remainingNanos;
                    while (true)
                    {
                        LockSupport.parkNanos(this, remainingNanos);
                        if (Thread.interrupted())
                        {
                            throw new InterruptedException();
                        }
                        if (lvBlocked() == null)
                        {
                            break;
                        }
                        remainingNanos = deadlineNanos - System.nanoTime();
                        if (remainingNanos <= 0)
                        {
                            return null;
                        }
                    }
                    unblocked = true;
                }
                finally
                {
                    if (!unblocked)
                    {
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

        soRefElement(buffer, offset, null); // release element null
        soConsumerIndex(cIndex + 2); // release cIndex

        return (E) e;
    }

    @Override
    public int remainingCapacity()
    {
        return capacity() - size();
    }

    @Override
    public int drainTo(Collection<? super E> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements)
    {
        throw new UnsupportedOperationException();
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
        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
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

        soRefElement(buffer, offset, null); // release element null
        soConsumerIndex(index + 2); // release cIndex
        return (E) e;
    }

    private Object spinWaitForElement(E[] buffer, long offset)
    {
        Object e;
        do
        {
            e = lvRefElement(buffer, offset);
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
        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
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

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        E e = lvRefElement(buffer, offset);
        if (e == null)
        {
            return null;
        }
        soRefElement(buffer, offset, null);
        soConsumerIndex(index + 2);
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        E e = lvRefElement(buffer, offset);
        return e;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        if (null == s)
            throw new IllegalArgumentException("supplier is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative:" + limit);
        if (limit == 0)
            return 0;

        final long mask = this.producerMask;
        final E[] buffer = this.producerBuffer;

        long pIndex;
        int claimedSlots;
        boolean wakeup = false;
        long batchIndex = 0;
        final long shiftedBatchSize = 2l * limit;

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
            batchIndex = Math.min(producerLimit, pIndex + shiftedBatchSize); //  -> producerLimit >= batchIndex

            // Use producer limit to save a read of the more rapidly mutated consumer index.
            // Assumption: queue is usually empty or near empty
            if (pIndex >= producerLimit)
            {
                if (!recalculateProducerLimit(mask, pIndex, producerLimit))
                {
                    return 0;
                }
                batchIndex = Math.min(lvProducerLimit(), pIndex + shiftedBatchSize);
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
            long offset = modifiedCalcCircularRefElementOffset(pIndex + 2l * i, mask);
            soRefElement(buffer, offset, s.get());
        }

        if (wakeup)
        {
            wakeupConsumer();
        }

        return claimedSlots;
    }

    @Override
    public int fill(Supplier<E> s)
    {
        return MessagePassingQueueUtil.fillBounded(this, s);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return drain(c, capacity());
    }

    @Override
    public int drain(final Consumer<E> c, final int limit)
    {
        return MessagePassingQueueUtil.drain(this, c, limit);
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy w, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, w, exit);
    }
}
