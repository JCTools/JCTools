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

import org.jctools.queues.IndexedQueueSizeUtil;
import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.queues.QueueProgressIndicators;

/**
 * A Multi-Producer-Single-Consumer queue based on a {@link AtomicReferenceArrayQueue}. This implies that
 * any thread may call the offer method, but only a single thread may call poll/peek for correctness to
 * maintained. <br>
 * This implementation follows patterns documented on the package level for False Sharing protection.<br>
 * This implementation is using the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * method for polling from the queue (with minor change to correctly publish the index) and an extension of
 * the Leslie Lamport concurrent queue algorithm (originated by Martin Thompson) on the producer side.<br>
 *
 * @author nitsanw
 *
 * @param <E>
 */
public final class MpscAtomicArrayQueue<E> extends AtomicReferenceArrayQueue<E>
        implements IndexedQueue, QueueProgressIndicators {
    private final AtomicLong consumerIndex;
    private final AtomicLong producerIndex;
    private final AtomicLong producerLimit;
    public MpscAtomicArrayQueue(int capacity) {
        super(capacity);
        this.consumerIndex = new AtomicLong();
        this.producerIndex = new AtomicLong();
        this.producerLimit = new AtomicLong();
    }
    /**
     * {@inheritDoc} <br>
     *
     * IMPLEMENTATION NOTES:<br>
     * Lock free offer using a single CAS. As class name suggests access is permitted to many threads
     * concurrently.
     *
     * @see java.util.Queue#offer(java.lang.Object)
     * @see org.jctools.queues.MessagePassingQueue#offer(Object)
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }

        // use a cached view on consumer index (potentially updated in loop)
        final int mask = this.mask;
        long producerLimit = lvProducerLimit(); // LoadLoad
        long pIndex;
        do {
            pIndex = lvProducerIndex(); // LoadLoad
            if (pIndex >= producerLimit) {
                final long cIndex = lvConsumerIndex(); // LoadLoad
                producerLimit = cIndex + mask + 1;

                if (pIndex >= producerLimit) {
                    return false; // FULL :(
                }
                else {
                    // update producer limit to the next index that we must recheck the consumer index
                    // this is racy, but the race is benign
                    soProducerLimit(producerLimit);
                }
            }
        } while (!casProducerIndex(pIndex, pIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to poll() we would need to handle the case where the element is not visible.
         */

        // Won CAS, move on to storing
        final int offset = calcElementOffset(pIndex, mask);
        soElement(buffer, offset, e); // StoreStore
        return true; // AWESOME :)
    }

    /**
     * A wait free alternative to offer which fails on CAS failure.
     *
     * @param e new element, not null
     * @return 1 if next element cannot be filled, -1 if CAS failed, 0 if successful
     * @deprecated This was renamed to {@link #failFastOffer(Object)} please migrate
     */
    @Deprecated
    public final int weakOffer(final E e) {
        return failFastOffer(e);
    }

    /**
     * A wait free alternative to offer which fails on CAS failure.
     *
     * @param e new element, not null
     * @return 1 if next element cannot be filled, -1 if CAS failed, 0 if successful
     */
    public final int failFastOffer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }
        final int mask = this.mask;
        final long capacity = mask + 1;
        final long pIndex = lvProducerIndex(); // LoadLoad
        long producerLimit = lvProducerLimit(); // LoadLoad
        if (pIndex >= producerLimit) {
            final long cIndex = lvConsumerIndex(); // LoadLoad
            producerLimit = cIndex + capacity;
            if (pIndex >= producerLimit) {
                return 1; // FULL :(
            }
            else {
                // update producer limit to the next index that we must recheck the consumer index
                soProducerLimit(producerLimit); // StoreLoad
            }
        }

        // look Ma, no loop!
        if (!casProducerIndex(pIndex, pIndex + 1)) {
            return -1; // CAS FAIL :(
        }

        // Won CAS, move on to storing
        final int offset = calcElementOffset(pIndex, mask);
        soElement(buffer, offset, e);
        return 0; // AWESOME :)
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free poll using ordered loads/stores. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll()
     * @see org.jctools.queues.MessagePassingQueue#poll()
     */
    @Override
    public E poll() {
        final long cIndex = lvConsumerIndex(); // LoadLoad
        final int offset = calcElementOffset(cIndex);
        // Copy field to avoid re-reading after volatile load
        final AtomicReferenceArray<E> buffer = this.buffer;

        // If we can't see the next available element we can't poll
        E e = lvElement(buffer, offset); // LoadLoad
        if (null == e) {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (cIndex != lvProducerIndex()) {
                do {
                    e = lvElement(buffer, offset);
                } while (e == null);
            }
            else {
                return null;
            }
        }

        spElement(buffer, offset, null);
        soConsumerIndex(cIndex + 1); // StoreStore
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free peek using ordered loads. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll()
     * @see org.jctools.queues.MessagePassingQueue#poll()
     */
    @Override
    public E peek() {
        // Copy field to avoid re-reading after volatile load
        final AtomicReferenceArray<E> buffer = this.buffer;

        final long cIndex = lvConsumerIndex(); // LoadLoad
        final int offset = calcElementOffset(cIndex);
        E e = lvElement(buffer, offset);
        if (null == e) {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (cIndex != lvProducerIndex()) {
                do {
                    e = lvElement(buffer, offset);
                } while (e == null);
            }
            else {
                return null;
            }
        }
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     */
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
    @Override
    public final long lvConsumerIndex() {
        return consumerIndex.get();
    }
    @Override
    public final long lvProducerIndex() {
        return producerIndex.get();
    }

    protected final long lvProducerLimit() {
        return producerLimit.get();
    }

    protected final void soProducerLimit(long v) {
        producerLimit.lazySet(v);
    }
    
    protected final boolean casProducerIndex(long expect, long newValue) {
        return producerIndex.compareAndSet(expect, newValue);
    }
    protected void soConsumerIndex(long l) {
        consumerIndex.lazySet(l);
    }
}
