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

abstract class MpscSequencedArrayQueueL1Pad<E> extends ConcurrentSequencedCircularArrayQueue<E> {
    long p10, p11, p12, p13, p14, p15, p16;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscSequencedArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscSequencedArrayQueueProducerField<E> extends MpscSequencedArrayQueueL1Pad<E> {
    private final static long P_INDEX_OFFSET;
    static {
        try {
            P_INDEX_OFFSET =
                UNSAFE.objectFieldOffset(MpscSequencedArrayQueueProducerField.class.getDeclaredField("producerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long producerIndex;

    public MpscSequencedArrayQueueProducerField(int capacity) {
        super(capacity);
    }

    protected final long lvProducerIndex() {
        return producerIndex;
    }

    protected final boolean casProducerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscSequencedArrayQueueL2Pad<E> extends MpscSequencedArrayQueueProducerField<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscSequencedArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscSequencedArrayQueueConsumerField<E> extends MpscSequencedArrayQueueL2Pad<E> {
    private final static long C_INDEX_OFFSET;
    static {
        try {
            C_INDEX_OFFSET =
                UNSAFE.objectFieldOffset(MpscSequencedArrayQueueConsumerField.class.getDeclaredField("consumerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected long consumerIndex;

    public MpscSequencedArrayQueueConsumerField(int capacity) {
        super(capacity);
    }

    protected final long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
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
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

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
            seqOffset = calcSequenceOffset(currentProducerIndex);
            final long seq = lvSequence(lSequenceBuffer, seqOffset); // LoadLoad
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
        final long elementOffset = calcElementOffset(currentProducerIndex);
        spElement(elementOffset, e);

        // increment sequence by 1, the value expected by consumer
        // (seeing this value from a producer will lead to retry 2)
        soSequence(lSequenceBuffer, seqOffset, currentProducerIndex + 1); // StoreStore

        return true;
    }

    @Override
    public E poll() {
        // local load of field to avoid repeated loads after volatile reads
        final long[] lSequenceBuffer = sequenceBuffer;

        consumerIndex = lvConsumerIndex();// LoadLoad
        final long seqOffset = calcSequenceOffset(consumerIndex);
        final long seq = lvSequence(lSequenceBuffer, seqOffset);// LoadLoad
        final long delta = seq - (consumerIndex + 1);

        if (delta == 0) {
            consumerIndex++;
        } else if (delta < 0) {
            // queue is empty
            return null;
        }

        // on 64bit(no compressed oops) JVM this is the same as seqOffset
        final long offset = calcElementOffset(consumerIndex);
        final E e = lpElement(offset);
        spElement(offset, null);

        // Move sequence ahead by capacity, preparing it for next offer
        // (seeing this value from a consumer will lead to retry 2)
        soSequence(lSequenceBuffer, seqOffset, consumerIndex + mask + 1);// StoreStore

        return e;
    }

    @Override
    public E peek() {
        return lpElement(calcElementOffset(lvConsumerIndex()));
    }

    @Override
    public int size() {
        int size;
        long capacity = mask + 1;
        do {
            /*
             * It is possible for a thread to be interrupted or reschedule between the read of the producer
             * and consumer indices, therefore protection is required to ensure size is within valid range.
             */
            final long currentConsumerIndex = lvConsumerIndex();
            final long currentProducerIndex = lvProducerIndex();
            size = (int)(currentProducerIndex - currentConsumerIndex);
        } while (size > capacity);

        return size;
    }
}
