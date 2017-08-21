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

import org.jctools.queues.IndexedQueueSizeUtil;
import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.QueueProgressIndicators;
import org.jctools.util.Pow2;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReferenceArray;


abstract class AtomicReferenceArrayQueue<E> extends AbstractQueue<E> implements IndexedQueue, QueueProgressIndicators, MessagePassingQueue<E>
{
    protected final AtomicReferenceArray<E> buffer;
    protected final int mask;

    public AtomicReferenceArrayQueue(int capacity)
    {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        this.mask = actualCapacity - 1;
        this.buffer = new AtomicReferenceArray<E>(actualCapacity);
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
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
            // toss it away
        }
    }

    protected final int calcElementOffset(long index, int mask)
    {
        return (int) index & mask;
    }

    protected final int calcElementOffset(long index)
    {
        return (int) index & mask;
    }

    public static <E> E lvElement(AtomicReferenceArray<E> buffer, int offset)
    {
        return buffer.get(offset);
    }

    public static <E> E lpElement(AtomicReferenceArray<E> buffer, int offset)
    {
        return buffer.get(offset); // no weaker form available
    }

    protected final E lpElement(int offset)
    {
        return buffer.get(offset); // no weaker form available
    }

    public static <E> void spElement(AtomicReferenceArray<E> buffer, int offset, E value)
    {
        buffer.lazySet(offset, value);  // no weaker form available
    }

    protected final void spElement(int offset, E value)
    {
        buffer.lazySet(offset, value);  // no weaker form available
    }

    public static <E> void soElement(AtomicReferenceArray<E> buffer, int offset, E value)
    {
        buffer.lazySet(offset, value);
    }

    protected final void soElement(int offset, E value)
    {
        buffer.lazySet(offset, value);
    }

    public static <E> void svElement(AtomicReferenceArray<E> buffer, int offset, E value)
    {
        buffer.set(offset, value);
    }

    protected final E lvElement(int offset)
    {
        return lvElement(buffer, offset);
    }

    @Override
    public final int capacity()
    {
        return (int) (mask + 1);
    }

    /**
     * {@inheritDoc}
     * <p>
     */
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
