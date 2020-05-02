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
import static org.jctools.util.UnsafeLongArrayAccess.*;
import static org.jctools.util.UnsafeRefArrayAccess.calcCircularRefElementOffset;

import org.jctools.util.UnsafeRefArrayAccess;

abstract class MpscSequencedArrayQueueL1Pad<E> extends ConcurrentSequencedCircularArrayQueue<E> {
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
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    public MpscSequencedArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscSequencedArrayQueueProducerField<E> extends MpscSequencedArrayQueueL1Pad<E> {
    private final static long P_INDEX_OFFSET = fieldOffset(MpscSequencedArrayQueueProducerField.class, "producerIndex");

    private volatile long producerIndex;

    public MpscSequencedArrayQueueProducerField(int capacity) {
        super(capacity);
    }

    public final long lvProducerIndex() {
        return producerIndex;
    }

    protected final boolean casProducerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscSequencedArrayQueueL2Pad<E> extends MpscSequencedArrayQueueProducerField<E> {
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
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    public MpscSequencedArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscSequencedArrayQueueConsumerField<E> extends MpscSequencedArrayQueueL2Pad<E> {
    private final static long C_INDEX_OFFSET = fieldOffset(MpscSequencedArrayQueueConsumerField.class, "consumerIndex");

    protected long consumerIndex;

    public MpscSequencedArrayQueueConsumerField(int capacity) {
        super(capacity);
    }

    public final long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }
    protected final long lpConsumerIndex() {
        return consumerIndex;
    }
    protected final void soConsumerIndex(long v) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, v);
    }

}

/**
 * A Multi-Producer-Single-Consumer queue based on same algorithm used for {@link MpmcArrayQueue} but with the
 * appropriate weakening of constraints on offer. The trade off does not seem worth while compared to the simpler
 * {@link MpscArrayQueue}.
 *
 * @param <E> type of the element stored in the {@link java.util.Queue}
 */
public class MpscSequencedArrayQueue<E> extends MpscSequencedArrayQueueConsumerField<E> {
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
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    public MpscSequencedArrayQueue(final int capacity) {
        super(Math.max(2, capacity));
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        // local load of field to avoid repeated loads after volatile reads
        final long[] lSequenceBuffer = sequenceBuffer;
        long currentProducerIndex;
        long seqOffset;

        while (true) {
            currentProducerIndex = lvProducerIndex(); // LoadLoad
            seqOffset = calcCircularLongElementOffset(currentProducerIndex, mask);
            final long seq = lvLongElement(lSequenceBuffer, seqOffset); // LoadLoad
            final long delta = seq - currentProducerIndex;

            if (delta == 0) {
                // this is expected if we see this first time around
                if (casProducerIndex(currentProducerIndex, currentProducerIndex + 1)) {
                    // Successful CAS: full barrier
                    break;
                }
                // failed cas, retry 1
            } else if (delta < 0) {
                // poll has not moved this value forward
                return false;
            }

            // another producer has moved the sequence by one, retry 2
        }

        // on 64bit(no compressed oops) JVM this is the same as seqOffset
        final long elementOffset = calcCircularRefElementOffset(currentProducerIndex, mask);
        UnsafeRefArrayAccess.spRefElement(buffer, elementOffset, e);

        // increment sequence by 1, the value expected by consumer
        // (seeing this value from a producer will lead to retry 2)
        // StoreStore
        soLongElement(lSequenceBuffer, seqOffset, currentProducerIndex + 1);

        return true;
    }

    @Override
    public E poll() {
        // local load of field to avoid repeated loads after volatile reads
        final long[] lSequenceBuffer = sequenceBuffer;

        long consumerIndex = lvConsumerIndex();// LoadLoad
        final long seqOffset = calcCircularLongElementOffset(consumerIndex, mask);
        final long seq = lvLongElement(lSequenceBuffer, seqOffset);// LoadLoad
        final long delta = seq - (consumerIndex + 1);

        if (delta < 0) {
            // queue is empty
            return null;
        }

        // on 64bit(no compressed oops) JVM this is the same as seqOffset
        final long offset = calcCircularRefElementOffset(consumerIndex, mask);
        final E e = UnsafeRefArrayAccess.lpRefElement(buffer, offset);
        UnsafeRefArrayAccess.spRefElement(buffer, offset, null);
        // Move sequence ahead by capacity, preparing it for next offer
        // (seeing this value from a consumer will lead to retry 2)
        // StoreStore
        soLongElement(lSequenceBuffer, seqOffset, consumerIndex + mask + 1);
        soConsumerIndex(consumerIndex+1);
        return e;
    }

    @Override
    public E peek() {
        return UnsafeRefArrayAccess.lpRefElement(buffer, calcCircularRefElementOffset(lvConsumerIndex(), mask));
    }

	@Override
	public boolean relaxedOffer(E message) {
		return offer(message);
	}

	@Override
	public E relaxedPoll() {
		return poll();
	}

	@Override
	public E relaxedPeek() {
		return peek();
	}

    @Override
    public int drain(Consumer<E> c) {
        final int limit = capacity();
        return drain(c,limit);
    }

    @Override
    public int fill(Supplier<E> s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drain(Consumer<E> c, int limit) {
        for (int i=0;i<limit;i++) {
            E e = relaxedPoll();
            if(e==null){
                return i;
            }
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void drain(Consumer<E> c,
            WaitStrategy wait,
            ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            E e = relaxedPoll();
            if(e==null){
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            c.accept(e);
        }
    }

    @Override
    public void fill(Supplier<E> s,
            WaitStrategy wait,
            ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            E e = s.get();
            while (!relaxedOffer(e)) {
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
        }
    }
}
