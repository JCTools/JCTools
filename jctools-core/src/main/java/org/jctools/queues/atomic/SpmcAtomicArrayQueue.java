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

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.queues.QueueProgressIndicators;

abstract class SpmcAtomicArrayQueueL1Pad<E> extends AtomicReferenceArrayQueue<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcAtomicArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcAtomicArrayQueueProducerIndexField<E> extends SpmcAtomicArrayQueueL1Pad<E> {
    private static final AtomicLongFieldUpdater<SpmcAtomicArrayQueueProducerIndexField> P_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(SpmcAtomicArrayQueueProducerIndexField.class, "producerIndex");
    private volatile long producerIndex;

    @Override
    public final long lvProducerIndex() {
        return producerIndex;
    }

    protected final void soProducerIndex(long v) {
        P_INDEX_UPDATER.lazySet(this, v);
    }

    public SpmcAtomicArrayQueueProducerIndexField(int capacity) {
        super(capacity);
    }
}
abstract class SpmcAtomicArrayQueueL2Pad<E> extends SpmcAtomicArrayQueueProducerIndexField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcAtomicArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}
abstract class SpmcAtomicArrayQueueConsumerIndexField<E> extends SpmcAtomicArrayQueueL2Pad<E> {
    protected static final AtomicLongFieldUpdater<SpmcAtomicArrayQueueConsumerIndexField> C_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(SpmcAtomicArrayQueueConsumerIndexField.class, "consumerIndex");

    private volatile long consumerIndex;

    public SpmcAtomicArrayQueueConsumerIndexField(int capacity) {
        super(capacity);
    }

    @Override
    public final long lvConsumerIndex() {
        return consumerIndex;
    }

    protected final boolean casHead(long expect, long newValue) {
        return C_INDEX_UPDATER.compareAndSet(this, expect, newValue);
    }
}

abstract class SpmcAtomicArrayQueueMidPad<E> extends SpmcAtomicArrayQueueConsumerIndexField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcAtomicArrayQueueMidPad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcAtomicArrayQueueProducerIndexCacheField<E> extends SpmcAtomicArrayQueueMidPad<E> {
    // This is separated from the consumerIndex which will be highly contended in the hope that this value spends most
    // of it's time in a cache line that is Shared(and rarely invalidated)
    private volatile long producerIndexCache;

    public SpmcAtomicArrayQueueProducerIndexCacheField(int capacity) {
        super(capacity);
    }

    protected final long lvProducerIndexCache() {
        return producerIndexCache;
    }

    protected final void svProducerIndexCache(long v) {
        producerIndexCache = v;
    }
}

abstract class SpmcAtomicArrayQueueL3Pad<E> extends SpmcAtomicArrayQueueProducerIndexCacheField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcAtomicArrayQueueL3Pad(int capacity) {
        super(capacity);
    }
}

/**
 * A single-producer multiple-consumer AtomicReferenceArray-backed queue.
 * @author akarnokd
 * @param <E>
 */
public final class SpmcAtomicArrayQueue<E> extends SpmcAtomicArrayQueueL3Pad<E> implements QueueProgressIndicators {
    
    public SpmcAtomicArrayQueue(int capacity) {
        super(capacity);
    }
    
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }
        final AtomicReferenceArray<E> buffer = this.buffer;
        final int mask = this.mask;
        final long currProducerIndex = lvProducerIndex();
        final int offset = calcElementOffset(currProducerIndex, mask);
        if (null != lvElement(buffer, offset)) {
            long size = currProducerIndex - lvConsumerIndex();

            if(size > mask) {
                return false;
            }
            else {
                // spin wait for slot to clear, buggers wait freedom
                while(null != lvElement(buffer, offset));
            }
        }
        spElement(buffer, offset, e);
        // single producer, so store ordered is valid. It is also required to correctly publish the element
        // and for the consumers to pick up the tail value.
        soProducerIndex(currProducerIndex + 1);
        return true;
    }

    @Override
    public E poll() {
        long currentConsumerIndex;
        long currProducerIndexCache = lvProducerIndexCache();
        do {
            currentConsumerIndex = lvConsumerIndex();
            if (currentConsumerIndex >= currProducerIndexCache) {
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex) {
                    return null;
                } else {
                    currProducerIndexCache = currProducerIndex;
                    svProducerIndexCache(currProducerIndex);
                }
            }
        } while (!casHead(currentConsumerIndex, currentConsumerIndex + 1));
        // consumers are gated on latest visible tail, and so can't see a null value in the queue or overtake
        // and wrap to hit same location.
        return removeElement(buffer, currentConsumerIndex, mask);
    }

    private E removeElement(final AtomicReferenceArray<E> buffer, long index, final int mask) {
        final int offset = calcElementOffset(index, mask);
        // load plain, element happens before it's index becomes visible
        final E e = lpElement(buffer, offset);
        // store ordered, make sure nulling out is visible. Producer is waiting for this value.
        soElement(buffer, offset, null);
        return e;
    }

    @Override
    public E peek() {
        final int mask = this.mask;
        final long currProducerIndexCache = lvProducerIndexCache();
        long currentConsumerIndex;
        E e;
        do {
            currentConsumerIndex = lvConsumerIndex();
            if (currentConsumerIndex >= currProducerIndexCache) {
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex) {
                    return null;
                } else {
                    svProducerIndexCache(currProducerIndex);
                }
            }
        } while (null == (e = lvElement(buffer, calcElementOffset(currentConsumerIndex, mask))));
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
