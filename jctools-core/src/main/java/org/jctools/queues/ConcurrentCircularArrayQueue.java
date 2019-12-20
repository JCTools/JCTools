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
import java.util.NoSuchElementException;

import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class ConcurrentCircularArrayQueueL0Pad<E> extends AbstractQueue<E>
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
abstract class ConcurrentCircularArrayQueue<E> extends ConcurrentCircularArrayQueueL0Pad<E>
    implements MessagePassingQueue<E>, IndexedQueue, QueueProgressIndicators, SupportsIterator
{
    protected final long mask;
    protected final E[] buffer;

    ConcurrentCircularArrayQueue(int capacity)
    {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        mask = actualCapacity - 1;
        buffer = allocate(actualCapacity);
    }

    @Override
    public int size()
    {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public boolean isEmpty()
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
    public long currentProducerIndex()
    {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex();
    }

    /**
     * Get an iterator for this queue. This method is thread safe.
     * <p>
     * The iterator provides a best-effort snapshot of the elements in the queue.
     * The returned iterator is not guaranteed to return elements in queue order,
     * and races with the consumer thread may cause gaps in the sequence of returned elements.
     * Like {link #relaxedPoll}, the iterator may not immediately return newly inserted elements.
     *
     * @return The iterator.
     */
    @Override
    public Iterator<E> iterator() {
        final long cIndex = lvConsumerIndex();
        final long pIndex = lvProducerIndex();

        return new WeakIterator(cIndex, pIndex, mask, buffer);
    }

    private static class WeakIterator<E> implements Iterator<E> {
        private final long pIndex;
        private final long mask;
        private final E[] buffer;
        private long nextIndex;
        private E nextElement;

        WeakIterator(long cIndex, long pIndex, long mask, E[] buffer) {
            this.nextIndex = cIndex;
            this.pIndex = pIndex;
            this.mask = mask;
            this.buffer = buffer;
            nextElement = getNext();
        }

        @Override
        public boolean hasNext() {
            return nextElement != null;
        }

        @Override
        public E next() {
            final E e = nextElement;
            if (e == null)
                throw new NoSuchElementException();
            nextElement = getNext();
            return e;
        }

        private E getNext() {
            while (nextIndex < pIndex) {
                long offset = calcCircularElementOffset(nextIndex++, mask);
                E e = lvElement(buffer, offset);
                if (e != null) {
                    return e;
                }
            }
            return null;
        }
    }
}
