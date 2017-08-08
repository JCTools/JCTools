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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jctools.queues.QueueProgressIndicators;

abstract class SpscAtomicArrayQueueColdField<E> extends AtomicReferenceArrayQueue<E> {
    public static final int MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);
    protected final int lookAheadStep;
    public SpscAtomicArrayQueueColdField(int capacity) {
        super(capacity);
        lookAheadStep = Math.min(capacity() / 4, MAX_LOOK_AHEAD_STEP);
    }
}
abstract class SpscAtomicArrayQueueL1Pad<E> extends SpscAtomicArrayQueueColdField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpscAtomicArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpscAtomicArrayQueueProducerIndexFields<E> extends SpscAtomicArrayQueueL1Pad<E> {
    protected static final AtomicLongFieldUpdater<SpscAtomicArrayQueueProducerIndexFields> P_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(SpscAtomicArrayQueueProducerIndexFields.class, "producerIndex");
    protected volatile long producerIndex;
    protected long producerLimit;

    public SpscAtomicArrayQueueProducerIndexFields(int capacity) {
        super(capacity);
    }
    
    @Override
    public final long lvProducerIndex() {
        return producerIndex;
    }

    protected final void soProducerIndex(final long newIndex) {
        P_INDEX_UPDATER.lazySet(this, newIndex);
    }
}

abstract class SpscAtomicArrayQueueL2Pad<E> extends SpscAtomicArrayQueueProducerIndexFields<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpscAtomicArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpscAtomicArrayQueueConsumerIndexField<E> extends SpscAtomicArrayQueueL2Pad<E> {
    protected static final AtomicLongFieldUpdater<SpscAtomicArrayQueueConsumerIndexField> C_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(SpscAtomicArrayQueueConsumerIndexField.class, "consumerIndex");
    protected volatile long consumerIndex;
    
    public SpscAtomicArrayQueueConsumerIndexField(int capacity) {
        super(capacity);
    }

    @Override
    public final long lvConsumerIndex() {
        return consumerIndex;
    }

    protected final void soConsumerIndex(final long newIndex) {
        C_INDEX_UPDATER.lazySet(this, newIndex);
    }
}

abstract class SpscAtomicArrayQueueL3Pad<E> extends SpscAtomicArrayQueueConsumerIndexField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpscAtomicArrayQueueL3Pad(int capacity) {
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
 * @author akarnokd
 *
 * @param <E>
 */
public final class SpscAtomicArrayQueue<E> extends SpscAtomicArrayQueueL3Pad<E> implements QueueProgressIndicators {

    public SpscAtomicArrayQueue(final int capacity) {
        super(Math.max(capacity, 4));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }
        // local load of field to avoid repeated loads after volatile reads
        final AtomicReferenceArray<E> buffer = this.buffer;
        final int mask = this.mask;
        final long producerIndex = this.producerIndex;

        if (producerIndex >= producerLimit &&
                !offerSlowPath(buffer, mask, producerIndex)) {
            return false;
        }
        final int offset = calcElementOffset(producerIndex, mask);

        soElement(buffer, offset, e); // StoreStore
        soProducerIndex(producerIndex + 1); // ordered store -> atomic and ordered for size()
        return true;
    }

    private boolean offerSlowPath(final AtomicReferenceArray<E> buffer, final int mask, final long producerIndex) {
        final int lookAheadStep = this.lookAheadStep;
        if (null == lvElement(buffer, calcElementOffset(producerIndex + lookAheadStep, mask))) {// LoadLoad
            producerLimit = producerIndex + lookAheadStep;
        }
        else {
            final int offset = calcElementOffset(producerIndex, mask);
            if (null != lvElement(buffer, offset)){
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
    public E poll() {
        final long consumerIndex = this.consumerIndex;
        final int offset = calcElementOffset(consumerIndex);
        // local load of field to avoid repeated loads after volatile reads
        final AtomicReferenceArray<E> buffer = this.buffer;
        final E e = lvElement(buffer, offset);// LoadLoad
        if (null == e) {
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
    public E peek() {
        return lvElement(buffer, calcElementOffset(consumerIndex));
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
