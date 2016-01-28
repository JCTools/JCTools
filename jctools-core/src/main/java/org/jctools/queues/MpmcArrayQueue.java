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

import static org.jctools.util.JvmInfo.CPUs;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeRefArrayAccess.lpElement;
import static org.jctools.util.UnsafeRefArrayAccess.spElement;

abstract class MpmcArrayQueueL1Pad<E> extends ConcurrentSequencedCircularArrayQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;
    public MpmcArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcArrayQueueProducerField<E> extends MpmcArrayQueueL1Pad<E> {
    private final static long P_INDEX_OFFSET;
    static {
        try {
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(MpmcArrayQueueProducerField.class
                    .getDeclaredField("producerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long producerIndex;

    public MpmcArrayQueueProducerField(int capacity) {
        super(capacity);
    }

    protected final long lvProducerIndex() {
        return producerIndex;
    }

    protected final boolean casProducerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpmcArrayQueueL2Pad<E> extends MpmcArrayQueueProducerField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    public MpmcArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcArrayQueueConsumerField<E> extends MpmcArrayQueueL2Pad<E> {
    private final static long C_INDEX_OFFSET;
    static {
        try {
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(MpmcArrayQueueConsumerField.class
                    .getDeclaredField("consumerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long consumerIndex;

    public MpmcArrayQueueConsumerField(int capacity) {
        super(capacity);
    }

    protected final long lvConsumerIndex() {
        return consumerIndex;
    }

    protected final boolean casConsumerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }
}

/**
 * A Multi-Producer-Multi-Consumer queue based on a {@link ConcurrentCircularArrayQueue}. This implies that
 * any and all threads may call the offer/poll/peek methods and correctness is maintained. <br>
 * This implementation follows patterns documented on the package level for False Sharing protection.<br>
 * The algorithm for offer/poll is an adaptation of the one put forward by D. Vyukov (See <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/bounded-mpmc-queue">here</a>). The original
 * algorithm uses an array of structs which should offer nice locality properties but is sadly not possible in
 * Java (waiting on Value Types or similar). The alternative explored here utilizes 2 arrays, one for each
 * field of the struct. There is a further alternative in the experimental project which uses iteration phase
 * markers to achieve the same algo and is closer structurally to the original, but sadly does not perform as
 * well as this implementation.<br>
 * 
 * Tradeoffs to keep in mind:
 * <ol>
 * <li>Padding for false sharing: counter fields and queue fields are all padded as well as either side of
 * both arrays. We are trading memory to avoid false sharing(active and passive).
 * <li>2 arrays instead of one: The algorithm requires an extra array of longs matching the size of the
 * elements array. This is doubling/tripling the memory allocated for the buffer.
 * <li>Power of 2 capacity: Actual elements buffer (and sequence buffer) is the closest power of 2 larger or
 * equal to the requested capacity.
 * </ol>
 * 
 * @param <E>
 *            type of the element stored in the {@link java.util.Queue}
 */
public class MpmcArrayQueue<E> extends MpmcArrayQueueConsumerField<E> implements QueueProgressIndicators {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    final static int RECOMENDED_POLL_BATCH = CPUs * 4;
    final static int RECOMENDED_OFFER_BATCH = CPUs * 4;
    public MpmcArrayQueue(final int capacity) {
        super(validateCapacity(capacity));
    }

    private static int validateCapacity(int capacity) {
        if(capacity < 2)
            throw new IllegalArgumentException("Minimum size is 2");
        return capacity;
    }

    @Override
    public boolean offer(final E e) {
        final long mask = this.mask;
        final long capacity = mask + 1;
        final long[] sBuffer = sequenceBuffer;
        
        long pIndex;
        long seqOffset;
        long seq;
        long cIndex = Long.MAX_VALUE;// start with bogus value, hope we don't need it
        do {
            pIndex = lvProducerIndex();
            seqOffset = calcSequenceOffset(pIndex, mask);
            seq = lvSequence(sBuffer, seqOffset);
            if (seq < pIndex) { // consumer has not moved this value forward
                if (pIndex - capacity <= cIndex && // test against cached cIndex
                        pIndex - capacity <= (cIndex = lvConsumerIndex())) { // test against latest cIndex
                    // Extra check required to ensure [Queue.offer == false iff queue is full]
                    return false;
                } else {
                    seq = pIndex + 1; // hack to make it go around again
                }
            }
        } while (seq > pIndex || // another producer has moved the sequence
                !casProducerIndex(pIndex, pIndex + 1)); // failed to increment

        assert null == lpElement(buffer, calcElementOffset(pIndex, mask));
        spElement(buffer, calcElementOffset(pIndex, mask), e);
        soSequence(sBuffer, seqOffset, pIndex + 1); // seq++;
        return true;

    }

    /**
     * {@inheritDoc}
     * <p>
     * Because return null indicates queue is empty we cannot simply rely on next element visibility for poll
     * and must test producer index when next element is not visible.
     */
    @Override
    public E poll() {
        // local load of field to avoid repeated loads after volatile reads
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        
        long cIndex;
        long seq;
        long seqOffset;
        long expectedSeq;
        long pIndex = -1; // start with bogus value, hope we don't need it
        do {
            cIndex = lvConsumerIndex();
            seqOffset = calcSequenceOffset(cIndex, mask);
            seq = lvSequence(sBuffer, seqOffset);
            expectedSeq = cIndex + 1;
            if (seq < expectedSeq) { // slot has not been moved by producer
                if (cIndex >= pIndex && // test against cached pIndex
                    cIndex == (pIndex = lvProducerIndex())) { // update pIndex if we must
                    // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                    return null;
                } else {
                    seq = expectedSeq + 1; // trip another go around
                }
            }
        } while (seq > expectedSeq || // another consumer beat us to it
                !casConsumerIndex(cIndex, cIndex + 1)); // failed the CAS
        
        final long offset = calcElementOffset(cIndex, mask);
        final E e = lpElement(buffer, offset);
        assert e != null;
        spElement(buffer, offset, null);
        soSequence(sBuffer, seqOffset, cIndex + mask + 1);// i.e. seq += capacity
        return e;
    }

    @Override
    public E peek() {
        long cIndex;
        E e;
        do {
            cIndex = lvConsumerIndex();
            // other consumers may have grabbed the element, or queue might be empty
            e = lpElement(buffer, calcElementOffset(cIndex));
            // only return null if queue is empty
        } while (e == null && cIndex != lvProducerIndex());
        return e;
    }

    @Override
    public int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        while (true) {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after) {
                return (int) (currentProducerIndex - after);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return (lvConsumerIndex() == lvProducerIndex());
    }
    
    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }
    
    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }
    
	@Override
	public boolean relaxedOffer(E e) {
		if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final long mask = this.mask;
        final long[] sBuffer = sequenceBuffer;
        
        long pIndex;
        long seqOffset;
        long seq;
        do {
            pIndex = lvProducerIndex();
            seqOffset = calcSequenceOffset(pIndex, mask);
            seq = lvSequence(sBuffer, seqOffset);
            if (seq < pIndex) { // slot not cleared by consumer yet
                return false;
            }
        } while (seq > pIndex || // another producer has moved the sequence
                !casProducerIndex(pIndex, pIndex + 1)); // failed to increment

        spElement(buffer, calcElementOffset(pIndex, mask), e);
        soSequence(sBuffer, seqOffset, pIndex + 1);
        return true;
	}

	@Override
	public E relaxedPoll() {
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        
        long cIndex;
        long seqOffset;
        long seq;
        long expectedSeq;
        do {
            cIndex = lvConsumerIndex();
            seqOffset = calcSequenceOffset(cIndex, mask);
            seq = lvSequence(sBuffer, seqOffset);
            expectedSeq = cIndex + 1;
            if (seq < expectedSeq) {
                return null;
            }
        } while (seq > expectedSeq || // another consumer beat us to it
                !casConsumerIndex(cIndex, cIndex + 1)); // failed the CAS
        
        final long offset = calcElementOffset(cIndex, mask);
        final E e = lpElement(buffer, offset);
        spElement(buffer, offset, null);
        soSequence(sBuffer, seqOffset, cIndex + mask + 1);
        return e;
	}

	@Override
	public E relaxedPeek() {
        long currConsumerIndex = lvConsumerIndex();
        return lpElement(buffer, calcElementOffset(currConsumerIndex));
	}

    @Override
    public int drain(Consumer<E> c) {
        final int capacity = capacity();
        int sum = 0;
        while (sum < capacity) {
            int drained = 0;
            if((drained = drain(c, MpmcArrayQueue.RECOMENDED_POLL_BATCH)) == 0) {
                break;
            }
            sum+=drained;
        }
        return sum;
    }

    @Override
    public int fill(Supplier<E> s) {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = capacity();
        do {
            final int filled = fill(s, RECOMENDED_OFFER_BATCH);
            if (filled == 0) {
                return (int) result;
            }
            result += filled;
        } while (result <= capacity);
        return (int) result;
    }

    @Override
    public int drain(Consumer<E> c, int limit) {
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        final E[] buffer = this.buffer;

        long cIndex;
        long seqOffset;
        long seq;
        long expectedSeq;
        for (int i = 0; i < limit; i++) {
            do {
                cIndex = lvConsumerIndex();
                seqOffset = calcSequenceOffset(cIndex, mask);
                seq = lvSequence(sBuffer, seqOffset);
                expectedSeq = cIndex + 1;
                if (seq < expectedSeq) {
                    return i;
                }
            } while (seq > expectedSeq || // another consumer beat us to it
                    !casConsumerIndex(cIndex, cIndex + 1)); // failed the CAS

            final long offset = calcElementOffset(cIndex, mask);
            final E e = lpElement(buffer, offset);
            spElement(buffer, offset, null);
            soSequence(sBuffer, seqOffset, cIndex + mask + 1);
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit) {
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        final E[] buffer = this.buffer;

        long pIndex;
        long seqOffset;
        long seq;
        for (int i = 0; i < limit; i++) {
            do {
                pIndex = lvProducerIndex();
                seqOffset = calcSequenceOffset(pIndex, mask);
                seq = lvSequence(sBuffer, seqOffset);
                if (seq < pIndex) { // slot not cleared by consumer yet
                    return i;
                }
            } while (seq > pIndex || // another producer has moved the sequence
                    !casProducerIndex(pIndex, pIndex + 1)); // failed to increment

            spElement(buffer, calcElementOffset(pIndex, mask), s.get());
            soSequence(sBuffer, seqOffset, pIndex + 1);
        }
        return limit;
    }

    @Override
    public void drain(Consumer<E> c,
            WaitStrategy w,
            ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            if(drain(c, MpmcArrayQueue.RECOMENDED_POLL_BATCH) == 0) {
                idleCounter = w.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
        }
    }

    @Override
    public void fill(Supplier<E> s,
            WaitStrategy w,
            ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            if (fill(s, MpmcArrayQueue.RECOMENDED_OFFER_BATCH) == 0) {
                idleCounter = w.idle(idleCounter);
                continue;
            }
            idleCounter = 0;   
        }
    }
}
