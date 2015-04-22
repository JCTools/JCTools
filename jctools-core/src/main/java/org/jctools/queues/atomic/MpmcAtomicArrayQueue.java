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
import java.util.concurrent.atomic.AtomicLongArray;

import org.jctools.queues.QueueProgressIndicators;

public class MpmcAtomicArrayQueue<E> extends SequencedAtomicReferenceArrayQueue<E>
        implements QueueProgressIndicators {
    private final AtomicLong producerIndex;
    private final AtomicLong consumerIndex;

    public MpmcAtomicArrayQueue(int capacity) {
        super(validateCapacity(capacity));
        this.producerIndex = new AtomicLong();
        this.consumerIndex = new AtomicLong();
    }

    private static int validateCapacity(int capacity) {
        if(capacity < 2)
            throw new IllegalArgumentException("Minimum size is 2");
        return capacity;
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        // local load of field to avoid repeated loads after volatile reads
        final int mask = this.mask;
        final int capacity = mask + 1;
        final AtomicLongArray sBuffer = sequenceBuffer;
        long currentProducerIndex;
        int seqOffset;
        long cIndex = Long.MAX_VALUE;// start with bogus value, hope we don't need it
        while (true) {
            currentProducerIndex = lvProducerIndex(); // LoadLoad
            seqOffset = calcSequenceOffset(currentProducerIndex, mask);
            final long seq = lvSequence(sBuffer, seqOffset); // LoadLoad
            final long delta = seq - currentProducerIndex;

            if (delta == 0) {
                // this is expected if we see this first time around
                if (casProducerIndex(currentProducerIndex, currentProducerIndex + 1)) {
                    // Successful CAS: full barrier
                    break;
                }
                // failed cas, retry 1
            } else if (delta < 0 && // poll has not moved this value forward
                    currentProducerIndex - capacity <= cIndex && // test against cached cIndex
                    currentProducerIndex - capacity <= (cIndex = lvConsumerIndex())) { // test against latest cIndex
                // Extra check required to ensure [Queue.offer == false iff queue is full]
                return false;
            }

            // another producer has moved the sequence by one, retry 2
        }

        // on 64bit(no compressed oops) JVM this is the same as seqOffset
        final int elementOffset = calcElementOffset(currentProducerIndex, mask);
        spElement(elementOffset, e);

        // increment sequence by 1, the value expected by consumer
        // (seeing this value from a producer will lead to retry 2)
        soSequence(sBuffer, seqOffset, currentProducerIndex + 1); // StoreStore

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
        final AtomicLongArray lSequenceBuffer = sequenceBuffer;
        final int mask = this.mask;
        long currentConsumerIndex;
        int seqOffset;
        long pIndex = -1; // start with bogus value, hope we don't need it
        while (true) {
            currentConsumerIndex = lvConsumerIndex();// LoadLoad
            seqOffset = calcSequenceOffset(currentConsumerIndex, mask);
            final long seq = lvSequence(lSequenceBuffer, seqOffset);// LoadLoad
            final long delta = seq - (currentConsumerIndex + 1);

            if (delta == 0) {
                if (casConsumerIndex(currentConsumerIndex, currentConsumerIndex + 1)) {
                    // Successful CAS: full barrier
                    break;
                }
                // failed cas, retry 1
            } else if (delta < 0 && // slot has not been moved by producer
                    currentConsumerIndex >= pIndex && // test against cached pIndex
                    currentConsumerIndex == (pIndex = lvProducerIndex())) { // update pIndex if we must
                // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                return null;
            }

            // another consumer beat us and moved sequence ahead, retry 2
        }

        // on 64bit(no compressed oops) JVM this is the same as seqOffset
        final int offset = calcElementOffset(currentConsumerIndex, mask);
        final E e = lpElement(offset);
        spElement(offset, null);

        // Move sequence ahead by capacity, preparing it for next offer
        // (seeing this value from a consumer will lead to retry 2)
        soSequence(lSequenceBuffer, seqOffset, currentConsumerIndex + mask + 1);// StoreStore

        return e;
    }

    @Override
    public E peek() {
        long currConsumerIndex;
        E e;
        do {
            currConsumerIndex = lvConsumerIndex();
            // other consumers may have grabbed the element, or queue might be empty
            e = lpElement(calcElementOffset(currConsumerIndex));
            // only return null if queue is empty
        } while (e == null && currConsumerIndex != lvProducerIndex());
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
    
    protected final long lvProducerIndex() {
        return producerIndex.get();
    }

    protected final boolean casProducerIndex(long expect, long newValue) {
        return producerIndex.compareAndSet(expect, newValue);
    }
    protected final long lvConsumerIndex() {
        return consumerIndex.get();
    }

    protected final boolean casConsumerIndex(long expect, long newValue) {
        return consumerIndex.compareAndSet(expect, newValue);
    }

}
