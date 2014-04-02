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
package io.jaq.spmc;

import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.common.ConcurrentRingBuffer;
import io.jaq.util.UnsafeAccess;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

abstract class SpmcConcurrentArrayQueueL1Pad<E> extends ConcurrentRingBuffer<E> {
    public long p10, p11, p12, p13, p14, p15, p16;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpmcConcurrentArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcConcurrentArrayQueueTailField<E> extends SpmcConcurrentArrayQueueL1Pad<E> {
    protected final static long TAIL_OFFSET;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(SpmcConcurrentArrayQueueTailField.class
                    .getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long tail;


    protected final long lvTail() {
        return tail;
    }
    
    protected final void soTail(long v) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, TAIL_OFFSET, v);
    }

    public SpmcConcurrentArrayQueueTailField(int capacity) {
        super(capacity);
    }
}

abstract class SpmcConcurrentArrayQueueL2Pad<E> extends SpmcConcurrentArrayQueueTailField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpmcConcurrentArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcConcurrentArrayQueueHeadField<E> extends SpmcConcurrentArrayQueueL2Pad<E> {
    protected final static long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(SpmcConcurrentArrayQueueHeadField.class
                    .getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long head;

    public SpmcConcurrentArrayQueueHeadField(int capacity) {
        super(capacity);
    }
    protected final long lvHead() {
        return head;
    }
    protected final boolean casHead(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
    }
}
abstract class SpmcConcurrentArrayQueueMidPad<E> extends SpmcConcurrentArrayQueueHeadField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpmcConcurrentArrayQueueMidPad(int capacity) {
        super(capacity);
    }
}
abstract class SpmcConcurrentArrayQueueTailCacheField<E> extends SpmcConcurrentArrayQueueMidPad<E> {
    private volatile long tailCache;

    public SpmcConcurrentArrayQueueTailCacheField(int capacity) {
        super(capacity);
    }
    protected final long lvTailCache() {
        return tailCache;
    }
    protected final void svTailCache(long v) {
        tailCache = v;
    }
}

abstract class SpmcConcurrentArrayQueueL3Pad<E> extends SpmcConcurrentArrayQueueTailCacheField<E> {
    public long p40, p41, p42, p43, p44, p45, p46;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpmcConcurrentArrayQueueL3Pad(int capacity) {
        super(capacity);
    }
}

public final class SpmcConcurrentQueue<E> extends SpmcConcurrentArrayQueueL3Pad<E> implements Queue<E>,
        ConcurrentQueue<E>, ConcurrentQueueConsumer<E>, ConcurrentQueueProducer<E> {

    public SpmcConcurrentQueue(final int capacity) {
        super(capacity);
    }

    public boolean add(final E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue is full");
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final E[] lb = buffer;
        final long currTail = lvTail();
        final long offset = calcOffset(currTail);
        if (null != lvElement(lb, offset)) {
            return false;
        }
        spElement(lb, offset, e);
        // single producer, so store ordered is valid. It is also required to correctly publish the element
        // and for the consumers to pick up the tail value.
        soTail(currTail + 1);
        return true;
    }


    @Override
    public E poll() {
        long currentHead;
        final long currTailCache = lvTailCache();
        do {
            currentHead = lvHead();
            if (currentHead >= currTailCache) {
                long currTail = lvTail();
                if (currentHead >= currTail) {
                    return null;
                }
                else {
                    svTailCache(currTail);
                }
            }
        } while (!casHead(currentHead, currentHead + 1));
        // consumers are gated on latest visible tail, and so can't see a null value in the queue or overtake
        // and wrap to hit same location.
        final long offset = calcOffset(currentHead);
        final E[] lb = buffer;
        // load plain, element happens before it's index becomes visible
        final E e = lpElement(lb, offset);
        // store ordered, make sure nulling out is visible. Producer is waiting for this value.
        soElement(lb, offset, null);
        return e;
    }


    public E remove() {
        final E e = poll();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E element() {
        final E e = peek();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E peek() {
        long currentHead = lvHead();
        return lvElement(calcOffset(currentHead));
    }

    public int size() {
        return (int) (lvTail() - lvHead());
    }

    public boolean isEmpty() {
        return lvTail() == lvHead();
    }

    public boolean contains(final Object o) {
        if (null == o) {
            return false;
        }

        for (long i = lvHead(), limit = lvTail(); i < limit; i++) {
            final E e = lvElement(calcOffset(i));
            if (o.equals(e)) {
                return true;
            }
        }

        return false;
    }

    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    public <T> T[] toArray(final T[] a) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(final Collection<?> c) {
        for (final Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }

        return true;
    }

    public boolean addAll(final Collection<? extends E> c) {
        for (final E e : c) {
            add(e);
        }

        return true;
    }

    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        Object value;
        do {
            value = poll();
        } while (null != value);
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return this;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return this;
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
