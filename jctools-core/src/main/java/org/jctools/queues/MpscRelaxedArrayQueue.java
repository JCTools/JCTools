/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jctools.queues;

import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeRefArrayAccess;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class MpscRelaxedArrayQueueL0Pad<E> extends AbstractQueue<E>
{

    long p00, p01, p02, p03, p04, p05, p06, p07;

    long p10, p11, p12, p13, p14, p15, p16;

}

abstract class MpscRelaxedArrayQueueActiveCycleIdField<E> extends MpscRelaxedArrayQueueL0Pad<E>
{

    private static final long ACTIVE_CYCLE_ID_OFFSET;


    static
    {
        try
        {
            ACTIVE_CYCLE_ID_OFFSET = UNSAFE
                .objectFieldOffset(MpscRelaxedArrayQueueActiveCycleIdField.class.getDeclaredField("activeCycleId"));
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
    }

    private volatile long activeCycleId;

    public static int activeCycleIndex(long activeCycleId)
    {
        return (int) (activeCycleId & 1);
    }

    public final long lvActiveCycleId()
    {
        return UNSAFE.getLongVolatile(this, ACTIVE_CYCLE_ID_OFFSET);
    }

    public final boolean casActiveCycleId(long expected, long value)
    {
        return UNSAFE.compareAndSwapLong(this, ACTIVE_CYCLE_ID_OFFSET, expected, value);
    }

    public final void soActiveCycleId(long value)
    {
        UNSAFE.putOrderedLong(this, ACTIVE_CYCLE_ID_OFFSET, value);
    }

}

abstract class MpscRelaxedArrayQueueMidPad<E> extends MpscRelaxedArrayQueueActiveCycleIdField<E>
{

    long p01, p02, p03, p04, p05, p06, p07;

    long p10, p11, p12, p13, p14, p15, p16, p17;

}

abstract class MpscRelaxedArrayQueueProducerLimitField<E> extends MpscRelaxedArrayQueueMidPad<E>
{

    private static final long P_LIMIT_OFFSET;

    static
    {
        try
        {
            P_LIMIT_OFFSET = UNSAFE
                .objectFieldOffset(MpscRelaxedArrayQueueProducerLimitField.class.getDeclaredField("producerLimit"));
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
    }

    private volatile long producerLimit;

    protected final long lvProducerLimit()
    {
        return UNSAFE.getLongVolatile(this, P_LIMIT_OFFSET);
    }

    protected final void soProducerLimit(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, newValue);
    }
}

abstract class MpscRelaxedArrayQueueL2Pad<E> extends MpscRelaxedArrayQueueProducerLimitField<E>
{

    long p00, p01, p02, p03, p04, p05, p06, p07;

    long p10, p11, p12, p13, p14, p15, p16;

}

abstract class MpscRelaxedArrayQueueConsumerPositionField<E> extends MpscRelaxedArrayQueueL2Pad<E>
{

    private static final long C_POS_OFFSET;


    static
    {
        try
        {
            C_POS_OFFSET = UNSAFE
                .objectFieldOffset(MpscRelaxedArrayQueueConsumerPositionField.class.getDeclaredField("consumerPosition"));
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected volatile long consumerPosition;

    protected final long lvConsumerPosition()
    {
        return UNSAFE.getLongVolatile(this, C_POS_OFFSET);
    }

    protected final long lpConsumerPosition()
    {
        return UNSAFE.getLong(this, C_POS_OFFSET);
    }


    protected void soConsumerPosition(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_POS_OFFSET, newValue);
    }
}

abstract class MpscRelaxedArrayQueueL3Pad<E> extends MpscRelaxedArrayQueueConsumerPositionField<E>
{

    long p01, p02, p03, p04, p05, p06, p07;

    long p10, p11, p12, p13, p14, p15, p16, p17;

}

abstract class MpscRelaxedArrayQueueProducerCycleClaimFields<E> extends MpscRelaxedArrayQueueL3Pad<E>
{

    private static final long P_CYCLE_CLAIM_BASE;
    private static final long ELEMENT_SHIFT;

    static
    {
        try
        {
            final long pClaim1 = UNSAFE
                .objectFieldOffset(MpscRelaxedArrayQueueProducerCycleClaimFields.class.getDeclaredField(
                    "producerFirstCycleClaim"));
            final long pClaim2 = UNSAFE
                .objectFieldOffset(MpscRelaxedArrayQueueProducerCycleClaimFields.class.getDeclaredField(
                    "producerSecondCycleClaim"));
            P_CYCLE_CLAIM_BASE = Math.min(pClaim1, pClaim2);
            ELEMENT_SHIFT = Integer.numberOfTrailingZeros(Long.SIZE / Byte.SIZE);
            if (Math.max(pClaim1, pClaim2) != calcProducerCycleClaimOffset(1))
            {
                throw new RuntimeException("The JVM is not packing long fields as expected!");
            }
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected volatile long producerFirstCycleClaim;

    protected volatile long producerSecondCycleClaim;

    private static long calcProducerCycleClaimOffset(int index)
    {
        return P_CYCLE_CLAIM_BASE + (index << ELEMENT_SHIFT);
    }

    protected final long lvProducerCycleClaim(int cycleIndex)
    {
        return UNSAFE.getLongVolatile(this, calcProducerCycleClaimOffset(cycleIndex));
    }

    protected final void soProducerCycleClaim(int cycleIndex, long value)
    {
        UNSAFE.putOrderedLong(this, calcProducerCycleClaimOffset(cycleIndex), value);
    }

    protected final long getAndIncrementProducerCycleClaim(int cycleIndex)
    {
        return UNSAFE.getAndAddLong(this, calcProducerCycleClaimOffset(cycleIndex), 1);
    }

}

abstract class MpscRelaxedArrayQueueL4Pad<E> extends MpscRelaxedArrayQueueProducerCycleClaimFields<E>
{

    long p01, p02, p03, p04, p05, p06;

    long p10, p11, p12, p13, p14, p15, p16, p17;

}

public final class MpscRelaxedArrayQueue<E> extends MpscRelaxedArrayQueueL4Pad<E> implements MessagePassingQueue<E>
{

    private final long mask;
    private final int capacity;
    private final int cycleLength;
    private final int cycleLengthLog2;
    private final E[] buffer;
    private final int positionOnCycleMask;
    private final int cycleIdBitShift;
    private final long maxCycleId;

    public MpscRelaxedArrayQueue(int capacity)
    {
        this.buffer = CircularArrayOffsetCalculator.allocate(Pow2.roundToPowerOfTwo(capacity) * 2);
        this.soConsumerPosition(0);
        this.soActiveCycleId(0);
        this.capacity = this.buffer.length;
        this.mask = this.capacity - 1;
        this.cycleLength = this.capacity / 2;
        this.soProducerLimit(this.cycleLength);
        this.cycleLengthLog2 = Integer.numberOfTrailingZeros(this.cycleLength);
        //it allows at least 1L << 28 = 268435456 overclaims of the position within a cycle while waiting a rotation to complete:
        //this would help to increase the cycleId domain with small capacity
        this.cycleIdBitShift =
            Math.min(32, Integer.numberOfTrailingZeros(Pow2.roundToPowerOfTwo(this.cycleLength + (1 << 28))));
        //it is the max position on cycle too
        this.positionOnCycleMask = (int) ((1L << this.cycleIdBitShift) - 1);
        this.maxCycleId = (1L << (Long.SIZE - this.cycleIdBitShift)) - 1;
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    private static int positionOnCycle(long producerCycleClaim, int positionOnCycleMask)
    {
        return (int) (producerCycleClaim & positionOnCycleMask);
    }

    private static long cycleId(long producerCycleClaim, int cycleIdBitShift)
    {
        return (producerCycleClaim >>> cycleIdBitShift);
    }

    private static long producerPosition(long cycleId, int positionWithinCycle, int cycleLengthLog2)
    {
        return (cycleId << cycleLengthLog2) + positionWithinCycle;
    }

    private static int calcElementOffset(int cycle, int positionWithinCycle, int cycleLengthLog2)
    {
        return (cycle << cycleLengthLog2) + positionWithinCycle;
    }

    private boolean isBackPressured(long producerPosition)
    {
        final long consumerPosition = lvConsumerPosition();
        final long claimLimit = consumerPosition + this.cycleLength;
        if (producerPosition < claimLimit)
        {
            //update the cached value: no backpressure
            soProducerLimit(claimLimit);
            return false;
        }
        else
        {
            return true;
        }
    }

    private void rotateCycle(int activeCycle, long producerCycleClaim, int cycleIdBitShift, long maxCycleId)
    {
        final long cycleId = cycleId(producerCycleClaim, cycleIdBitShift);
        if (cycleId >= maxCycleId)
        {
            throw new IllegalStateException("exhausted cycle id space!");
        }
        final long nextCycleId = cycleId + 1;
        //it points at the beginning of the next cycle
        final long nextProducerCycleClaim = nextCycleId << cycleIdBitShift;
        final int nextActiveCycle = (activeCycle + 1) & 1;
        //it needs to be atomic: a producer fallen behind could claim it concurrently
        soProducerCycleClaim(nextActiveCycle, nextProducerCycleClaim);
        //from now on a slow producer could move the claim and lead to another rotation: a cas is needed to detect it
        if (!casActiveCycleId(cycleId, nextCycleId))
        {
            throw new IllegalStateException("slow rotation due to producer thread starvation!");
        }
    }

    private void validateSlowProducerClaim(final long cycleId, int positionOnCycle, int cycleLengthLog2)
    {
        final long producerPosition = producerPosition(cycleId, positionOnCycle, cycleLengthLog2);
        final long claimLimit = lvProducerLimit();
        if (producerPosition >= claimLimit)
        {
            //it is really backpressured?
            if (isBackPressured(producerPosition))
            {
                throw new IllegalStateException(
                    "the producer has fallen behind: please enlarge the capacity or reduce the number of producers!");
            }
        }
    }

    @Override
    public boolean offer(E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        //offer can fail only when backpressured, otherwise it retries
        final int positionOnCycleMask = this.positionOnCycleMask;
        final int cycleLengthLog2 = this.cycleLengthLog2;
        final int cycleLength = this.cycleLength;
        final int cycleIdBitShift = this.cycleIdBitShift;
        final E[] buffer = this.buffer;
        final long maxCycleId = this.maxCycleId;
        final long maxPositionOnCycle = positionOnCycleMask;

        while (true)
        {
            final int activeCycle = activeCycleIndex(lvActiveCycleId());
            final long producerActiveCycleClaim = lvProducerCycleClaim(activeCycle);
            final int positionOnActiveCycle = positionOnCycle(producerActiveCycleClaim, positionOnCycleMask);
            final long activeCycleId = cycleId(producerActiveCycleClaim, cycleIdBitShift);
            final long producerPosition = producerPosition(activeCycleId, positionOnActiveCycle, cycleLengthLog2);
            final long claimLimit = lvProducerLimit();
            if (producerPosition >= claimLimit)
            {
                //it is really backpressured?
                if (isBackPressured(producerPosition))
                {
                    return false;
                }
            }
            //try to claim on the active cycle
            final long producerCycleClaim = getAndIncrementProducerCycleClaim(activeCycle);
            final int positionOnCycle = positionOnCycle(producerCycleClaim, positionOnCycleMask);
            if (positionOnCycle >= maxPositionOnCycle)
            {
                throw new IllegalStateException(
                    "too many over-claims: please enlarge the capacity or reduce the number of producers!");
            }
            if (positionOnCycle < cycleLength)
            {
                final long cycleId = cycleId(producerCycleClaim, cycleIdBitShift);
                if (cycleId != activeCycleId)
                {
                    validateSlowProducerClaim(cycleId, positionOnCycle, cycleLengthLog2);
                }
                //simplified operation to calculate the offset in the array: activeCycle hasn't changed from the beginning of the offer!
                final int producerOffset = calcElementOffset(activeCycle, positionOnCycle, cycleLengthLog2);
                final long producerElementOffset = UnsafeRefArrayAccess.calcElementOffset(producerOffset);
                soElement(buffer, producerElementOffset, e);
                return true;
            }
            else if (positionOnCycle == cycleLength)
            {
                //is the only one responsible to rotate cycle: the other producers will be forced to wait until rotation got completed to perform a valid offer
                rotateCycle(activeCycle, producerCycleClaim, cycleIdBitShift, maxCycleId);
            }
        }
    }

    @Override
    public E poll()
    {
        final long consumerPosition = lpConsumerPosition();
        final long offset = CircularArrayOffsetCalculator.calcElementOffset(consumerPosition, this.mask);
        final E[] buffer = this.buffer;
        E e;
        if ((e = lvElement(buffer, offset)) != null)
        {
            //can be used a memory_order_relaxed set here, because the consumer position write release the buffer value
            spElement(buffer, offset, null);
            //consumer position allows the producers to move the claim limit (aka reduce backpressure)
            //hence can be set only after the buffer slot release
            soConsumerPosition(consumerPosition + 1);
            return e;
        }
        else
        {
            return pollMaybeEmpty(buffer, offset, consumerPosition);
        }
    }

    private E pollMaybeEmpty(E[] buffer, final long offset, final long consumerPosition)
    {
        final int activeCycleIndex = activeCycleIndex(lvActiveCycleId());
        final long producerCycleClaim = lvProducerCycleClaim(activeCycleIndex);
        final long producerPosition = producerPosition(
            cycleId(producerCycleClaim, this.cycleIdBitShift),
            positionOnCycle(producerCycleClaim, this.positionOnCycleMask),
            this.cycleLengthLog2);
        if (producerPosition == consumerPosition)
        {
            return null;
        }
        else
        {
            E e;
            while ((e = lvElement(buffer, offset)) == null)
            {

            }
            //can be used a memory_order_relaxed set here, because the consumer position write release the buffer value
            spElement(buffer, offset, null);
            //consumer position allows the producers to move the claim limit (aka reduce backpressure)
            //hence can be set only after the buffer slot release
            soConsumerPosition(consumerPosition + 1);
            return e;
        }
    }

    @Override
    public E peek()
    {
        final long consumerPosition = lpConsumerPosition();
        final long offset = CircularArrayOffsetCalculator.calcElementOffset(consumerPosition, this.mask);
        E e = lvElement(this.buffer, offset);
        if (e != null)
        {
            return e;
        }
        final int activeCycleIndex = activeCycleIndex(lvActiveCycleId());
        final long producerCycleClaim = lvProducerCycleClaim(activeCycleIndex);
        final long producerPosition = producerPosition(
            cycleId(producerCycleClaim, this.cycleIdBitShift),
            positionOnCycle(producerCycleClaim, this.positionOnCycleMask),
            this.cycleLengthLog2);
        if (producerPosition == consumerPosition)
        {
            return null;
        }
        else
        {
            while ((e = lvElement(buffer, offset)) == null)
            {

            }
            return e;
        }
    }

    @Override
    public int size()
    {
        long after = lvConsumerPosition();
        int positionOnCycle;
        long producerClaim;
        long before;
        do
        {
            before = after;
            final int activeClaim = activeCycleIndex(lvActiveCycleId());
            producerClaim = lvProducerCycleClaim(activeClaim);
            after = lvConsumerPosition();
            positionOnCycle = positionOnCycle(producerClaim, this.positionOnCycleMask);
        }
        while (positionOnCycle > this.cycleLength || before != after);
        //need to have a stable consumer and a valid claim (into the cycle)
        final long size =
            producerPosition(cycleId(producerClaim, this.cycleIdBitShift), positionOnCycle, this.cycleLengthLog2) -
                after;
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
    public void clear()
    {
        while (poll() != null)
        {
            // if you stare into the void
        }
    }

    @Override
    public boolean isEmpty()
    {
        return size() == 0;
    }

    @Override
    public int capacity()
    {
        return this.cycleLength;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @Override
    public E relaxedPoll()
    {
        final long consumerPosition = lpConsumerPosition();
        final long offset = CircularArrayOffsetCalculator.calcElementOffset(consumerPosition, this.mask);
        final E[] buffer = this.buffer;
        E e;
        if ((e = lvElement(buffer, offset)) != null)
        {
            //can be used a memory_order_relaxed set here, because the consumer position write release the buffer value
            spElement(buffer, offset, null);
            //consumer position allows the producers to move the claim limit (aka reduce backpressure)
            //hence can be set only after the buffer slot release
            soConsumerPosition(consumerPosition + 1);
        }
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final long consumerPosition = lpConsumerPosition();
        final long offset = CircularArrayOffsetCalculator.calcElementOffset(consumerPosition, this.mask);
        return lvElement(this.buffer, offset);
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
    public int drain(Consumer<E> c, int limit)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        for (int i = 0; i < limit; i++)
        {
            final long consumerPosition = lpConsumerPosition();
            final long offset = CircularArrayOffsetCalculator.calcElementOffset(consumerPosition, mask);

            E e;
            if ((e = lvElement(buffer, offset)) != null)
            {
                //can be used a memory_order_relaxed set here, because the consumer position write release the buffer value
                spElement(buffer, offset, null);
                //consumer position allows the producers to move the claim limit (aka reduce backpressure)
                //hence can be set only after the buffer slot release
                soConsumerPosition(consumerPosition + 1);
                c.accept(e);
            }
            else
            {
                return i;
            }
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        final int positionOnCycleMask = this.positionOnCycleMask;
        final int cycleLengthLog2 = this.cycleLengthLog2;
        final int cycleLength = this.cycleLength;
        final int cycleIdBitShift = this.cycleIdBitShift;
        final E[] buffer = this.buffer;
        final long maxCycleId = this.maxCycleId;
        final long maxPositionOnCycle = positionOnCycleMask;

        int i = 0;
        while (i < limit)
        {
            final int activeCycle = activeCycleIndex(lvActiveCycleId());
            final long producerActiveCycleClaim = lvProducerCycleClaim(activeCycle);
            final int positionOnActiveCycle = positionOnCycle(producerActiveCycleClaim, positionOnCycleMask);
            final long activeCycleId = cycleId(producerActiveCycleClaim, cycleIdBitShift);
            final long producerPosition = producerPosition(activeCycleId, positionOnActiveCycle, cycleLengthLog2);
            final long claimLimit = lvProducerLimit();
            if (producerPosition >= claimLimit)
            {
                //it is really backpressured?
                if (isBackPressured(producerPosition))
                {
                    return i;
                }
            }
            //try to claim on the active cycle
            final long producerCycleClaim = getAndIncrementProducerCycleClaim(activeCycle);
            final int positionOnCycle = positionOnCycle(producerCycleClaim, positionOnCycleMask);
            if (positionOnCycle >= maxPositionOnCycle)
            {
                throw new IllegalStateException(
                    "too many over-claims: please enlarge the capacity or reduce the number of producers!");
            }
            if (positionOnCycle < cycleLength)
            {
                final long cycleId = cycleId(producerCycleClaim, cycleIdBitShift);
                if (cycleId != activeCycleId)
                {
                    validateSlowProducerClaim(cycleId, positionOnCycle, cycleLengthLog2);
                }
                //simplified operation to calculate the offset in the array: activeCycle hasn't changed from the beginning of the offer!
                final int producerOffset = calcElementOffset(activeCycle, positionOnCycle, cycleLengthLog2);
                soElement(buffer, UnsafeRefArrayAccess.calcElementOffset(producerOffset), s.get());
                i++;
            }
            else if (positionOnCycle == cycleLength)
            {
                //is the only one responsible to rotate cycle: the other producers will be forced to wait until rotation got completed to perform a valid offer
                rotateCycle(activeCycle, producerCycleClaim, cycleIdBitShift, maxCycleId);
            }
        }
        return i;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy w, ExitCondition exit)
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long consumerPosition = lvConsumerPosition();

        int counter = 0;
        while (exit.keepRunning())
        {
            for (int i = 0; i < 4096; i++)
            {
                final long offset = CircularArrayOffsetCalculator.calcElementOffset(consumerPosition, mask);
                final E e = lvElement(buffer, offset);// LoadLoad
                if (null == e)
                {
                    counter = w.idle(counter);
                    continue;
                }
                consumerPosition++;
                counter = 0;
                //a plain store would be enough
                spElement(buffer, offset, null);
                soConsumerPosition(consumerPosition); // ordered store -> atomic and ordered for size()
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

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }
}
