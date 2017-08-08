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
package org.jctools.queues.atomic;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.jctools.queues.QueueProgressIndicators;
import org.jctools.util.RangeUtil;

abstract class MpmcAtomicArrayQueueL1Pad<E> extends SequencedAtomicReferenceArrayQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;
    public MpmcAtomicArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcAtomicArrayQueueProducerIndexField<E> extends MpmcAtomicArrayQueueL1Pad<E> {
    private static final AtomicLongFieldUpdater<MpmcAtomicArrayQueueProducerIndexField> P_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(MpmcAtomicArrayQueueProducerIndexField.class, "producerIndex");

    private volatile long producerIndex;

    public MpmcAtomicArrayQueueProducerIndexField(int capacity) {
        super(capacity);
    }

    @Override
    public final long lvProducerIndex() {
        return producerIndex;
    }

    protected final boolean casProducerIndex(long expect, long newValue) {
        return P_INDEX_UPDATER.compareAndSet(this, expect, newValue);
    }
}

abstract class MpmcAtomicArrayQueueL2Pad<E> extends MpmcAtomicArrayQueueProducerIndexField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    public MpmcAtomicArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcAtomicArrayQueueConsumerIndexField<E> extends MpmcAtomicArrayQueueL2Pad<E> {
    private static final AtomicLongFieldUpdater<MpmcAtomicArrayQueueConsumerIndexField> C_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(MpmcAtomicArrayQueueConsumerIndexField.class, "consumerIndex");

    private volatile long consumerIndex;

    public MpmcAtomicArrayQueueConsumerIndexField(int capacity) {
        super(capacity);
    }
    
    @Override
    public final long lvConsumerIndex() {
        return consumerIndex;
    }

    protected final boolean casConsumerIndex(long expect, long newValue) {
        return C_INDEX_UPDATER.compareAndSet(this, expect, newValue);
    }
}

abstract class MpmcAtomicArrayQueueL3Pad<E> extends MpmcAtomicArrayQueueConsumerIndexField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    public MpmcAtomicArrayQueueL3Pad(int capacity) {
        super(capacity);
    }
}

public class MpmcAtomicArrayQueue<E> extends MpmcAtomicArrayQueueL3Pad<E>
        implements QueueProgressIndicators {
    
    public MpmcAtomicArrayQueue(int capacity) {
        super(RangeUtil.checkGreaterThanOrEqual(capacity, 2, "capacity"));
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }
        final int mask = this.mask;
        final int capacity = mask + 1;
        final AtomicLongArray sBuffer = sequenceBuffer;

        long pIndex;
        int seqOffset;
        long seq;
        long cIndex = Long.MIN_VALUE;// start with bogus value, hope we don't need it
        do {
            pIndex = lvProducerIndex();
            seqOffset = calcSequenceOffset(pIndex, mask);
            seq = lvSequence(sBuffer, seqOffset);
            if (seq < pIndex) { // consumer has not moved this seq forward, it's as last producer left
                // Extra check required to ensure [Queue.offer == false iff queue is full]
                if (pIndex - capacity >= cIndex && // test against cached cIndex
                    pIndex - capacity >= (cIndex = lvConsumerIndex())) { // test against latest cIndex
                    return false;
                } else {
                    seq = pIndex + 1; // (+) hack to make it go around again without CAS
                }
            }
        } while (seq > pIndex || // another producer has moved the sequence(or +)
                !casProducerIndex(pIndex, pIndex + 1)); // failed to increment

        soElement(buffer, calcElementOffset(pIndex, mask), e);
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
        final AtomicLongArray sBuffer = sequenceBuffer;
        final int mask = this.mask;

        long cIndex;
        long seq;
        int seqOffset;
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

        final int offset = calcElementOffset(cIndex, mask);
        final E e = lpElement(buffer, offset);
        soElement(buffer, offset, null);
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
            e = lpElement(calcElementOffset(cIndex));
            // only return null if queue is empty
        } while (e == null && cIndex != lvProducerIndex());
        return e;
    }

    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }
    

}
