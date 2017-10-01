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

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.Pow2;

import java.util.AbstractQueue;
import java.util.Iterator;

abstract class ConcurrentCircularArrayQueueL0Pad<E> extends AbstractQueue<E>
    implements MessagePassingQueue<E>, IndexedQueue, QueueProgressIndicators
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

/**
 * Common functionality for array backed queues. The class is pre-padded and the array is padded on either side to help
 * with False Sharing prevention. It is expected that subclasses handle post padding.
 *
 * @param <E>
 * @author nitsanw
 */
public abstract class ConcurrentCircularArrayQueue<E> extends ConcurrentCircularArrayQueueL0Pad<E>
{
    protected final long mask;
    protected final E[] buffer;

    public ConcurrentCircularArrayQueue(int capacity)
    {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        mask = actualCapacity - 1;
        buffer = CircularArrayOffsetCalculator.allocate(actualCapacity);
    }

    /**
     * @param index desirable element index
     * @param mask (length - 1)
     * @return the offset in bytes within the array for a given index.
     */
    protected static long calcElementOffset(long index, long mask)
    {
        return CircularArrayOffsetCalculator.calcElementOffset(index, mask);
    }

    /**
     * @param index desirable element index
     * @return the offset in bytes within the array for a given index.
     */
    protected final long calcElementOffset(long index)
    {
        return calcElementOffset(index, mask);
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int size()
    {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public final boolean isEmpty()
    {
        return IndexedQueueSizeUtil.isEmpty(this);
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    @Override
    public void clear()
    {
        while (poll() != null)
        {
            // if you stare into the void
        }
    }

    @Override
    public int capacity()
    {
        return (int) (mask + 1);
    }

    @Override
    public final long currentProducerIndex()
    {
        return lvProducerIndex();
    }

    @Override
    public final long currentConsumerIndex()
    {
        return lvConsumerIndex();
    }

}
