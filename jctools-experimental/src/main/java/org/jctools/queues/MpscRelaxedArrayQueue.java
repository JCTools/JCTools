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
 *
 * This is a port of the algorithm used here:
 * https://github.com/real-logic/aeron/blob/c715c19852c8455c92e73c3167e7d43021d9a384/aeron-client/src/main/java/io/aeron/Publication.java
 */

package org.jctools.queues;

import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;
import org.jctools.util.UnsafeRefArrayAccess;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class MpscRelaxedArrayQueueL0Pad<E> extends AbstractQueue<E>
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

abstract class MpscRelaxedArrayQueueActiveCycleIdField<E> extends MpscRelaxedArrayQueueL0Pad<E>
{
    private static final long ACTIVE_CYCLE_ID_OFFSET = fieldOffset(MpscRelaxedArrayQueueActiveCycleIdField.class, "activeCycleId");

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

abstract class MpscRelaxedArrayQueueProducerLimitField<E> extends MpscRelaxedArrayQueueMidPad<E>
{
    private static final long P_LIMIT_OFFSET = fieldOffset(MpscRelaxedArrayQueueProducerLimitField.class, "producerLimit");

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

abstract class MpscRelaxedArrayQueueConsumerPositionField<E> extends MpscRelaxedArrayQueueL2Pad<E>
{
    private static final long C_POS_OFFSET = fieldOffset(MpscRelaxedArrayQueueConsumerPositionField.class, "consumerPosition");

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

abstract class MpscRelaxedArrayQueueProducerCycleClaimFields<E> extends MpscRelaxedArrayQueueL3Pad<E>
{
    private static final long P_CYCLE_CLAIM_BASE;
    private static final long ELEMENT_SHIFT;
    static
    {
        final long pClaim1 = fieldOffset(MpscRelaxedArrayQueueProducerCycleClaimFields.class, "producerFirstCycleClaim");
        final long pClaim2 = fieldOffset(MpscRelaxedArrayQueueProducerCycleClaimFields.class, "producerSecondCycleClaim");

        P_CYCLE_CLAIM_BASE = Math.min(pClaim1, pClaim2);
        ELEMENT_SHIFT = Integer.numberOfTrailingZeros(Long.SIZE / Byte.SIZE);
        if (Math.max(pClaim1, pClaim2) != calcProducerCycleClaimOffset(1))
        {
            throw new RuntimeException("The JVM is not packing long fields as expected!");
        }
    }
    // these are treated as an array, just inlined
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

    protected final boolean casProducerCycleClaim(int cycleIndex, long expectedValue, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, calcProducerCycleClaimOffset(cycleIndex), expectedValue, newValue);
    }
}

abstract class MpscRelaxedArrayQueueL4Pad<E> extends MpscRelaxedArrayQueueProducerCycleClaimFields<E>
{
    long p01, p02, p03, p04, p05, p06;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

/**
 * This class is still work in progress, please do not pick up for production use just yet.
 */
public class MpscRelaxedArrayQueue<E> extends MpscRelaxedArrayQueueL4Pad<E> implements MessagePassingQueue<E>
{
    /**
     * Note on terminology:
     *  - position/id: overall progress indicator, not an array index or offset at which to lookup/write.
     *  - index: for looking up within an array (including the inlined producerCycleClaim array)
     *  - offset: for pointer like access using Unsafe
     *
     * The producer in this queue operates on cycleId and the producerCycleClaim array:
     *  - The cycleId grow monotonically, and the parity bit (cycleIndex) indicated which claim to use
     *  - The producerCycleClaim indicate position in a cycle as well as the originating cycleId. From a claim we can
     *    calculate the producer overall position as well as the position within a cycle.
     *
     * The buffer is split into 2 cycles (matching cycleIndex 0 and 1), allowing the above indicators to control
     * producer progress on separate counters while maintaining the appearance of a contiguous buffer to the consumer.
     *
     */

    private final long mask;
    private final int cycleLength;
    private final int cycleLengthLog2;
    private final E[] buffer;
    private final int positionWithinCycleMask;
    private final int cycleIdBitShift;
    private final long maxCycleId;

    public MpscRelaxedArrayQueue(int capacity)
    {
        RangeUtil.checkGreaterThanOrEqual(capacity, 2, "capacity");
        capacity = Pow2.roundToPowerOfTwo(capacity * 2);
        this.buffer = (E[]) new Object[capacity];
        this.soConsumerPosition(0);
        this.soActiveCycleId(0);
        this.mask = capacity - 1;
        this.cycleLength = capacity / 2;
        this.soProducerLimit(this.cycleLength);
        this.cycleLengthLog2 = Integer.numberOfTrailingZeros(this.cycleLength);
        // it allows at least 1L << 28 = 268435456 overclaims of the position within a cycle while waiting a rotation
        // to complete: this would help to increase the cycleId domain with small capacity
        this.cycleIdBitShift =
            Math.min(32, Integer.numberOfTrailingZeros(Pow2.roundToPowerOfTwo(this.cycleLength + (1 << 28))));
        // it is the max position on cycle too
        this.positionWithinCycleMask = (int) ((1L << this.cycleIdBitShift) - 1);
        this.maxCycleId = (1L << (Long.SIZE - this.cycleIdBitShift)) - 1;
        this.soProducerCycleClaim(0, 0);
        this.soProducerCycleClaim(1, this.cycleLength + 1);
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        // offer can fail only when queue is full, otherwise it retries
        final int positionOnCycleMask = this.positionWithinCycleMask;
        final int cycleLengthLog2 = this.cycleLengthLog2;
        final int cycleLength = this.cycleLength;
        final int cycleIdBitShift = this.cycleIdBitShift;

        while (true)
        {
            final long activeCycleId = lvActiveCycleId(); // acquire activeCycleId
            final int activeCycleIndex = activeCycleIndex(activeCycleId);

            // this is a non-committed view of the producer position, but may be out of date when we XADD
            final long tempCycleClaim = lvProducerCycleClaim(activeCycleIndex);

            final int tempPositionWithinCycle = positionWithinCycle(tempCycleClaim, positionOnCycleMask);
            final long tempCycleId = producerClaimCycleId(tempCycleClaim, cycleIdBitShift);

            if (activeCycleId != tempCycleId || tempPositionWithinCycle > cycleLength)
            {
                // this covers the case of either being mid rotation or by some freak scheduling accident missing 2
                // rotations between activeCycleId load and lvProducerCycleClaim
                continue;
            }

            final long tempPosition = producerPosition(tempPositionWithinCycle, tempCycleId, cycleLengthLog2);

            // pre-checks are only valid for the temp values, so best effort...
            if (tempPosition >= lvProducerLimit())
            {
                if (isFull(tempPosition))
                {
                    return false;
                }
            }

            // try to claim on the active cycle (though the activeCycleIndex might be outdated)
            final long producerCycleClaim = getAndIncrementProducerCycleClaim(activeCycleIndex); // release producerCycleClaim[activeCycleIndex]

            final int positionWithinCycle = positionWithinCycle(producerCycleClaim, positionOnCycleMask);

            if (positionWithinCycle == positionOnCycleMask)
            {
                // This is an extreme rare case which requires very large numbers of getAndAdd operations to occur while
                // waiting for rotation, this is also mitigated by the full queue check above and the mid rotation guard
                // above it.
                throw new IllegalStateException(
                    "Too many over-claims: please enlarge the capacity or reduce the number of producers!\n" +
                        " positionWithinCycle=" + positionWithinCycle);
            }
            if (positionWithinCycle < cycleLength)
            {
                final long cycleId = producerClaimCycleId(producerCycleClaim, cycleIdBitShift);
                final boolean slowProducer = cycleId != activeCycleId;
                //it should fail with a slow producer
                if (!validateProducerClaim(
                    activeCycleIndex,
                    producerCycleClaim,
                    cycleId,
                    positionWithinCycle,
                    cycleLengthLog2, slowProducer))
                {
                    //the claim has been rollbacked and can be retried
                    continue;
                }
                soCycleElement(buffer, e, activeCycleIndex, positionWithinCycle, cycleLengthLog2);
                return true;
            }
            else if (positionWithinCycle == cycleLength)
            {
                final long cycleId = producerClaimCycleId(producerCycleClaim, cycleIdBitShift);
                rotateCycle(cycleId, cycleIdBitShift, maxCycleId);
            }
        }
    }

    /**
     * Given the nature of getAndAdd progress on producerPosition and given the potential risk for over claiming it is
     * quite possible for this method to report a queue which is not full as full.
     */
    private boolean isFull(final long producerPosition)
    {
        final long consumerPosition = lvConsumerPosition();
        final long producerLimit = consumerPosition + this.cycleLength;
        if (producerPosition < producerLimit)
        {
            soProducerLimit(producerLimit);
            return false;
        }
        else
        {
            return true;
        }
    }

    private void rotateCycle(
        final long claimCycleId,
        final int cycleIdBitShift,
        final long maxCycleId)
    {
        if (claimCycleId >= maxCycleId)
        {
            throw new IllegalStateException("Exhausted cycle id space!");
        }
        final long nextCycleId = claimCycleId + 1;
        final int nextActiveCycleIndex = activeCycleIndex(nextCycleId);
        // it points at the beginning of the next cycle
        soProducerCycleClaim(nextActiveCycleIndex, nextCycleId << cycleIdBitShift);
        //Following this initialisation, a sequence of slow producers claims could trigger several new cycle rotations
        //before having changed the activeCycleId from claimCycleId to nextCycleId:
        //detect (and warn) a slow rotation, but enabling the faster ones to make progress, allows the q to not being blocked
        long cycleId = claimCycleId;
        //the rotation claimCycleId -> nextCycleId is unique between producers
        while (!casActiveCycleId(cycleId, nextCycleId))
        {
            cycleId = detectSlowRotation(claimCycleId, nextCycleId);
        }
    }

    private long detectSlowRotation(final long claimCycleId, final long nextCycleId)
    {
        final long cycleId = lvActiveCycleId();
        //Another producer has managed to perform a rotation to an higher cycleId?
        assert cycleId != nextCycleId : "Duplicate rotation!";
        if (cycleId > nextCycleId)
        {
            throw new IllegalStateException(
                "Slow rotation due to producer thread starvation detected: please enlarge the capacity or reduce the number of producers!\n" +
                    "found activeCycleId=" + cycleId + "\n" +
                    "expected activeCycleId=" + claimCycleId + "\n");
        }
        return cycleId;
    }

    /**
     * Validate a producer claim to find out if is an overclaim (beyond the producer limit).
     *
     * @return {@code true} if the claim is valid, {@code false} otherwise.
     */
    private boolean validateProducerClaim(
        final int activeCycleIndex,
        final long producerCycleClaim,
        final long cycleId,
        final int positionOnCycle,
        final int cycleLengthLog2,
        final boolean slowProducer)
    {
        final long producerPosition = producerPosition(positionOnCycle, cycleId, cycleLengthLog2);
        final long claimLimit = lvProducerLimit();
        if (producerPosition >= claimLimit)
        {
            // it is really full?
            if (isFull(producerPosition))
            {
                return fixProducerOverClaim(activeCycleIndex, producerCycleClaim, slowProducer);
            }
        }
        return true;
    }

    /**
     * It tries to fix a producer overclaim.
     *
     * @return {@code true} if the claim is now safe to be used,{@code false} otherwise and is needed to retry the claim.
     */
    private boolean fixProducerOverClaim(
        final int activeCycleIndex,
        final long producerCycleClaim,
        final boolean slowProducer)
    {
        final long expectedProducerCycleClaim = producerCycleClaim + 1;
        //try to fix the overclaim bringing it back to a lower or a safe position
        if (!casProducerCycleClaim(activeCycleIndex, expectedProducerCycleClaim, producerCycleClaim))
        {
            final long currentProducerCycleClaim = lvProducerCycleClaim(activeCycleIndex);
            //another producer has managed to fix the claim
            if (currentProducerCycleClaim <= producerCycleClaim)
            {
                return false;
            }
            if (slowProducer)
            {
                validateSlowProducerOverClaim(activeCycleIndex, producerCycleClaim);
                return true;
            }
            else
            {
                //the claim cannot be rolled back so It must be used as it is
                return true;
            }
        }
        return false;
    }

    /**
     * Validates a slow producer over-claim throwing {@link IllegalStateException} if the offer on it can't continue.
     */
    private void validateSlowProducerOverClaim(final int activeCycleIndex, final long producerCycleClaim)
    {
        //the cycle claim is now ok?
        final long producerPosition = producerPositionFromClaim(
            producerCycleClaim,
            positionWithinCycleMask,
            cycleIdBitShift,
            cycleLengthLog2);
        if (isFull(producerPosition))
        {
            //a definitive fail could be declared only if the claim is trying to overwrite something not consumed yet:
            //isFull is not considering the real occupation of the slot
            final long consumerPosition = lvConsumerPosition();
            final long effectiveProducerLimit = consumerPosition + (this.cycleLength * 2l);
            if (producerPosition >= effectiveProducerLimit)
            {
                throw new IllegalStateException(
                    "The producer has fallen behind: please enlarge the capacity or reduce the number of producers! \n" +
                        " producerPosition=" + producerPosition + "\n" +
                        " consumerPosition=" + consumerPosition + "\n" +
                        " activeCycleIndex=" + activeCycleIndex + "\n" +
                        " cycleId=" + producerClaimCycleId(producerCycleClaim, cycleIdBitShift) + "\n" +
                        " positionOnCycle=" + positionWithinCycle(producerCycleClaim, positionWithinCycleMask));
            }
            //the slot is not occupied: we can write into it
        }
        //the claim now is ok: consumers have gone forward enough
    }

    private void soCycleElement(E[] buffer, E e, int activeCycleIndex, int positionWithinCycle, int cycleLengthLog2)
    {
        final int indexInBuffer = calcElementIndexInBuffer(positionWithinCycle, activeCycleIndex, cycleLengthLog2);
        final long offset = UnsafeRefArrayAccess.calcRefElementOffset(indexInBuffer);
        soRefElement(buffer, offset, e);
    }

    @Override
    public E poll()
    {
        final long consumerPosition = lpConsumerPosition();
        final long offset = calcCircularRefElementOffset(consumerPosition, this.mask);
        final E[] buffer = this.buffer;
        final E e = lvRefElement(buffer, offset);
        if (null == e)
        {
            return pollSlowPath(buffer, offset, consumerPosition);
        }
        signalConsumerProgress(consumerPosition, buffer, offset);
        return e;
    }

    private void signalConsumerProgress(long consumerPosition, E[] buffer, long offset)
    {
        spRefElement(buffer, offset, null);
        soConsumerPosition(consumerPosition + 1);
    }

    private E pollSlowPath(final E[] buffer, final long offset, final long consumerPosition)
    {
        final int activeCycleIndex = activeCycleIndex(lvActiveCycleId());
        final long producerCycleClaim = lvProducerCycleClaim(activeCycleIndex);
        final long producerPosition = producerPositionFromClaim(
            producerCycleClaim,
            this.positionWithinCycleMask,
            this.cycleIdBitShift,
            this.cycleLengthLog2);

        if (producerPosition == consumerPosition)
        {
            return null;
        }

        final E e = spinForElement(buffer, offset);
        signalConsumerProgress(consumerPosition, buffer, offset);
        return e;
    }

    @Override
    public E peek()
    {
        final E[] buffer = this.buffer;
        final long consumerPosition = lpConsumerPosition();
        final long offset = calcCircularRefElementOffset(consumerPosition, this.mask);
        E e = lvRefElement(buffer, offset);
        if (null == e)
        {
            return peekSlowPath(buffer, consumerPosition, offset);
        }
        return e;
    }

    private E peekSlowPath(final E[] buffer, long consumerPosition, long offset)
    {
        final int activeCycleIndex = activeCycleIndex(lvActiveCycleId());
        final long producerCycleClaim = lvProducerCycleClaim(activeCycleIndex);

        final long producerPosition = producerPositionFromClaim(
            producerCycleClaim,
            this.positionWithinCycleMask,
            this.cycleIdBitShift,
            this.cycleLengthLog2);

        if (producerPosition == consumerPosition)
        {
            return null;
        }

        return spinForElement(buffer, offset);
    }

    private E spinForElement(final E[] buffer, long offset)
    {
        E e;
        do
        {
            e = lvRefElement(buffer, offset);
        }
        while (e == null);
        return e;
    }

    @Override
    public int size()
    {
        final int cycleIdBitShift = this.cycleIdBitShift;

        long after = lvConsumerPosition();
        long producerClaimCycleId;
        long before;
        long activeCycleId;

        int positionWithinCycle;
        long producerClaim;

        do
        {
            before = after;
            activeCycleId = lvActiveCycleId();
            producerClaim = lvProducerCycleClaim(activeCycleIndex(activeCycleId));
            after = lvConsumerPosition();
            producerClaimCycleId = producerClaimCycleId(producerClaim, cycleIdBitShift);
            positionWithinCycle = positionWithinCycle(producerClaim, this.positionWithinCycleMask);
        }
        while (positionWithinCycle > this.cycleLength || before != after || activeCycleId != producerClaimCycleId);

        // need to have a stable consumer and a valid claim
        final long size = producerPosition(positionWithinCycle, producerClaimCycleId, this.cycleLengthLog2) - after;
        if (size > mask + 1)
        {
            return (int) (mask + 1);
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
        return cycleLength;
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
        final long offset = calcCircularRefElementOffset(consumerPosition, this.mask);
        final E[] buffer = this.buffer;
        final E e = lvRefElement(buffer, offset);
        if (e != null)
        {
            signalConsumerProgress(consumerPosition, buffer, offset);
        }
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final long consumerPosition = lpConsumerPosition();
        final long mask = this.mask;
        final long offset = calcCircularRefElementOffset(consumerPosition, mask);
        return lvRefElement(this.buffer, offset);
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
            final long offset = calcCircularRefElementOffset(consumerPosition, mask);

            E e;
            if ((e = lvRefElement(buffer, offset)) != null)
            {
                signalConsumerProgress(consumerPosition, buffer, offset);
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
        final int positionOnCycleMask = this.positionWithinCycleMask;
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
            final int positionOnActiveCycle = positionWithinCycle(producerActiveCycleClaim, positionOnCycleMask);
            final long activeCycleId = producerClaimCycleId(producerActiveCycleClaim, cycleIdBitShift);
            final long producerPosition = producerPosition(positionOnActiveCycle, activeCycleId, cycleLengthLog2);
            final long claimLimit = lvProducerLimit();
            if (producerPosition >= claimLimit)
            {
                //it is really full?
                if (isFull(producerPosition))
                {
                    return i;
                }
            }
            //try to claim on the active cycle
            final long producerCycleClaim = getAndIncrementProducerCycleClaim(activeCycle);
            final int positionOnCycle = positionWithinCycle(producerCycleClaim, positionOnCycleMask);
            if (positionOnCycle >= maxPositionOnCycle)
            {
                throw new IllegalStateException(
                    "too many over-claims: please enlarge the capacity or reduce the number of producers!");
            }
            if (positionOnCycle < cycleLength)
            {
                final long cycleId = producerClaimCycleId(producerCycleClaim, cycleIdBitShift);
                //it is a slow producer?
                final boolean slowProducer = cycleId != activeCycleId;
                if (!validateProducerClaim(
                    activeCycle,
                    producerCycleClaim,
                    cycleId,
                    positionOnCycle,
                    cycleLengthLog2,
                    slowProducer))
                {
                    continue;
                }
                soCycleElement(buffer, s.get(), activeCycle, positionOnCycle, cycleLengthLog2);
                i++;
            }
            else if (positionOnCycle == cycleLength)
            {
                final long cycleId = producerClaimCycleId(producerCycleClaim, cycleIdBitShift);
                rotateCycle(cycleId, cycleIdBitShift, maxCycleId);
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
                final long offset = calcCircularRefElementOffset(consumerPosition, mask);
                final E e = lvRefElement(buffer, offset);// LoadLoad
                if (null == e)
                {
                    counter = w.idle(counter);
                    continue;
                }
                counter = 0;
                signalConsumerProgress(consumerPosition, buffer, offset);
                consumerPosition++;
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

    private static int positionWithinCycle(long producerCycleClaim, int positionOnCycleMask)
    {
        return (int) (producerCycleClaim & positionOnCycleMask);
    }

    private static long producerClaimCycleId(long producerCycleClaim, int cycleIdBitShift)
    {
        return (producerCycleClaim >>> cycleIdBitShift);
    }

    private static long producerPositionFromClaim(
        long producerCycleClaim,
        int positionOnCycleMask,
        int cycleIdBitShift,
        int cycleLengthLog2)
    {
        final int positionWithinCycle = positionWithinCycle(producerCycleClaim, positionOnCycleMask);
        final long producerClaimCycleId = producerClaimCycleId(producerCycleClaim, cycleIdBitShift);
        return producerPosition(
            positionWithinCycle,
            producerClaimCycleId,
            cycleLengthLog2);
    }

    /**
     * Convert position in cycle and cycleId into a producer position (monotonically increasing reflection of offers
     * that is comparable with the consumerPosition to determine size/empty/full)
     */
    private static long producerPosition(
        int positionWithinCycle,
        long cycleId,
        int cycleLengthLog2)
    {
        return (cycleId << cycleLengthLog2) + positionWithinCycle;
    }

    /**
     * Convert [position within cycle, cycleIndex] to index in buffer.
     */
    private static int calcElementIndexInBuffer(
        int positionWithinCycle,
        int cycleIndex,
        int cycleLengthLog2)
    {
        return (cycleIndex << cycleLengthLog2) + positionWithinCycle;
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }
}
