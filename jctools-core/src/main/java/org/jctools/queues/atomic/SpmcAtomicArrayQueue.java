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

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.queues.IndexedQueueSizeUtil;
import org.jctools.queues.QueueProgressIndicators;

/**
 * A single-producer multiple-consumer AtomicReferenceArray-backed queue.
 * @author akarnokd
 * @param <E>
 */
public final class SpmcAtomicArrayQueue<E> extends AtomicReferenceArrayQueue<E> implements IndexedQueue, QueueProgressIndicators{
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
    
    protected final long lvProducerIndexCache() {
        return producerIndexCache.get();
    }

    protected final void svProducerIndexCache(long v) {
        producerIndexCache.set(v);
    }
    
    @Override
    public final long lvConsumerIndex() {
        return consumerIndex.get();
    }

    protected final boolean casHead(long expect, long newValue) {
        return consumerIndex.compareAndSet(expect, newValue);
    }

    @Override
    public final long lvProducerIndex() {
        return producerIndex.get();
    }

    protected final void soProducerIndex(long v) {
        producerIndex.lazySet(v);
    }

    @Override
    public int size() {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public boolean isEmpty() {
        return IndexedQueueSizeUtil.isEmpty(this);
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
