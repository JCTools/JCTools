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
package io.jaq.mpmc;

import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.common.ConcurrentRingBuffer;
import io.jaq.util.UnsafeAccess;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

abstract class MpmcConcurrentQueueL1Pad<E> extends ConcurrentRingBuffer<E> {
    public long p10, p11, p12, p13, p14, p15, p16;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueTailField<E> extends MpmcConcurrentQueueL1Pad<E> {
    private final static long TAIL_OFFSET;

    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueTailField.class
                    .getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long tail;
    private volatile long headCache;

    public MpmcConcurrentQueueTailField(int capacity) {
        super(capacity);
    }

    protected final long lvTail() {
        return tail;
    }

    protected final boolean casTail(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
    }

    protected long lvHeadCache() {
        return headCache;
    }

    protected void svHeadCache(long headCache) {
        this.headCache = headCache;
    }
}

abstract class MpmcConcurrentQueueL2Pad<E> extends MpmcConcurrentQueueTailField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueHeadField<E> extends MpmcConcurrentQueueL2Pad<E> {
    private final static long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueHeadField.class
                    .getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long head;
    private volatile long tailCache;

    public MpmcConcurrentQueueHeadField(int capacity) {
        super(capacity);
    }

    protected final long lvHead() {
        return head;
    }

    protected final boolean casHead(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
    }

    protected long lvTailCache() {
        return tailCache;
    }

    protected void svTailCache(long tailCache) {
        this.tailCache = tailCache;
    }
}

public final class MpmcConcurrentQueue<E> extends MpmcConcurrentQueueHeadField<E> implements Queue<E>,
        ConcurrentQueue<E>, ConcurrentQueueProducer<E>, ConcurrentQueueConsumer<E> {
    public long p40, p41, p42, p43, p44, p45, p46;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueue(final int capacity) {
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
        long currentHeadCache = lvHeadCache();
        long currentTail;
        do {
            currentTail = lvTail();
            final long wrapPoint = currentTail - capacity;
            if (currentHeadCache <= wrapPoint) {
                currentHeadCache = lvHead();
                if (currentHeadCache <= wrapPoint) {
                    return false;
                } else {
                    // only store if value is useful
                    svHeadCache(currentHeadCache);
                }
            }
        } while (!casTail(currentTail, currentTail + 1));

        final long offset = calcOffset(currentTail);
        // head may become visible before element is taken, verify slot is empty before storing
        while (lvElement(lb, offset) != null)
            ;
        soElement(lb, offset, e);
        return true;
    }

    @Override
    public E poll() {
        final E[] lb = buffer;
        long currentTailCache = lvTailCache();
        long currentHead;
        do {
            currentHead = lvHead();
            if (currentHead >= currentTailCache) {
                currentTailCache = lvTail();
                if (currentHead >= currentTailCache) {
                    return null;
                } else {
                    // only store if value is useful
                    svTailCache(currentTailCache);
                }
            }
        } while (!casHead(currentHead, currentHead + 1));
        final long offset = calcOffset(currentHead);
        E e;
        do {
            e = lvElement(lb, offset);
            // tail may become visible before element, verify slot is full before reading and nulling
        } while (e == null);
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

    @Override
    public E peek() {
        return lpElement(calcOffset(lvHead()));
    }

    public int size() {
        return (int) (lvTail() - lvHead());
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean contains(final Object o) {
        if (null == o) {
            return false;
        }

        for (long i = lvHead(), limit = lvTail(); i < limit; i++) {
            final E e = lpElement(calcOffset(i));
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
