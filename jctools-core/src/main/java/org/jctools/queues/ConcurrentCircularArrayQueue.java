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

import java.util.AbstractQueue;
import java.util.Iterator;

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.Pow2;

abstract class ConcurrentCircularArrayQueueL0Pad<E> extends AbstractQueue<E> implements MessagePassingQueue<E>, IndexedQueue, QueueProgressIndicators {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

/**
 * A concurrent access enabling class used by circular array based queues this class exposes an offset computation
 * method along with differently memory fenced load/store methods into the underlying array. The class is pre-padded and
 * the array is padded on either side to help with False sharing prvention. It is expected theat subclasses handle post
 * padding.
 * <p>
 * Offset calculation is separate from access to enable the reuse of a give compute offset.
 * <p>
 * Load/Store methods using a <i>buffer</i> parameter are provided to allow the prevention of final field reload after a
 * LoadLoad barrier.
 * <p>
 *
 * @author nitsanw
 *
 * @param <E>
 */
public abstract class ConcurrentCircularArrayQueue<E> extends ConcurrentCircularArrayQueueL0Pad<E> {
    protected final long mask;
    // @Stable :(
    protected final E[] buffer;

    public ConcurrentCircularArrayQueue(int capacity) {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        mask = actualCapacity - 1;
        buffer = CircularArrayOffsetCalculator.allocate(actualCapacity);
    }

    /**
     * @param index desirable element index
     * @return the offset in bytes within the array for a given index.
     */
    protected final long calcElementOffset(long index) {
        return calcElementOffset(index, mask);
    }

    /**
     * @param index desirable element index
     * @param mask
     * @return the offset in bytes within the array for a given index.
     */
    protected static long calcElementOffset(long index, long mask) {
        return CircularArrayOffsetCalculator.calcElementOffset(index, mask);
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }

    @Override
    public void clear() {
        while (poll() != null || !isEmpty())
            ;
    }

    @Override
    public int capacity() {
        return (int) (mask + 1);
    }

    @Override
    public final int size() {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public final boolean isEmpty() {
        return IndexedQueueSizeUtil.isEmpty(this);
    }

    @Override
    public final long currentProducerIndex() {
        return lvProducerIndex();
    }

    @Override
    public final long currentConsumerIndex() {
        return lvConsumerIndex();
    }

}
