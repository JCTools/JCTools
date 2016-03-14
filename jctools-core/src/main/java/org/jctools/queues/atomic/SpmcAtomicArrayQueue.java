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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jctools.queues.QueueProgressIndicators;

/**
 * A single-producer multiple-consumer AtomicReferenceArray-backed queue.
 * @author akarnokd
 * @param <E>
 */
public final class SpmcAtomicArrayQueue<E> extends AtomicReferenceArrayQueue<E> implements QueueProgressIndicators{
    private final AtomicLong consumerIndex;
    private final AtomicLong producerIndex;
    private final AtomicLong producerIndexCache;
    public SpmcAtomicArrayQueue(int capacity) {
        super(capacity);
        this.consumerIndex = new AtomicLong();
        this.producerIndex = new AtomicLong();
        this.producerIndexCache = new AtomicLong();
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
        soTail(currProducerIndex + 1);
        return true;
    }

    @Override
    public E poll() {
        long currentConsumerIndex;
        final long currProducerIndexCache = lvProducerIndexCache();
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
        } while (!casHead(currentConsumerIndex, currentConsumerIndex + 1));
        // consumers are gated on latest visible tail, and so can't see a null value in the queue or overtake
        // and wrap to hit same location.
        final int offset = calcElementOffset(currentConsumerIndex);
        final AtomicReferenceArray<E> lb = buffer;
        // load plain, element happens before it's index becomes visible
        final E e = lpElement(lb, offset);
        // store ordered, make sure nulling out is visible. Producer is waiting for this value.
        soElement(lb, offset, null);
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
        } while (null == (e = lvElement(calcElementOffset(currentConsumerIndex, mask))));
        return e;
    }

    @Override
    public int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and consumer
         * indices, therefore protection is required to ensure size is within valid range. In the event of concurrent
         * polls/offers to this method the size is OVER estimated as we read consumer index BEFORE the producer index.
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
        // This ensures the correctness of this method at least for the consumer thread. Other threads POV is not really
        // something we can fix here.
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
    protected final long lvProducerIndexCache() {
        return producerIndexCache.get();
    }

    protected final void svProducerIndexCache(long v) {
        producerIndexCache.set(v);
    }
    protected final long lvConsumerIndex() {
        return consumerIndex.get();
    }

    protected final boolean casHead(long expect, long newValue) {
        return consumerIndex.compareAndSet(expect, newValue);
    }

    protected final long lvProducerIndex() {
        return producerIndex.get();
    }

    protected final void soTail(long v) {
        producerIndex.lazySet(v);
    }
}
