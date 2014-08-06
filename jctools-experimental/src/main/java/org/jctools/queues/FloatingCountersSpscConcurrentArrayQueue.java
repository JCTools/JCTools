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

import org.jctools.util.LongCell;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;
import org.jctools.util.VolatileLongCell;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

abstract class FloatingCaqL0Pad {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class FloatingCaqColdFields<E> extends InlinedRingBufferL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 2);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;
    protected final VolatileLongCell tail = new VolatileLongCell(0);
    protected final VolatileLongCell head = new VolatileLongCell(0);

    protected final LongCell tailCache = new LongCell();
    protected final LongCell headCache = new LongCell();

    @SuppressWarnings("unchecked")
    FloatingCaqColdFields(int capacity) {
        if (Pow2.isPowerOfTwo(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.roundToPowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }
}

public final class FloatingCountersSpscConcurrentArrayQueue<E> extends FloatingCaqColdFields<E> implements
        Queue<E> {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p50, p51, p52, p53, p54, p55, p56;
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);

        if (4 == scale) {
            ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
        } else if (8 == scale) {
            ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class)
                + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
    }

    public FloatingCountersSpscConcurrentArrayQueue(final int capacity) {
        super(capacity);
    }

    public boolean add(final E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue is full");
    }

    private long offset(long index) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }

    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        final long currTail = tail.get();
        final long wrapPoint = currTail - capacity + 32;
        if (headCache.get() <= wrapPoint) {
            final long currHead = head.get();
            headCache.set(currHead);
            if (currHead <= wrapPoint) {
                return false;
            }
        }
        UnsafeAccess.UNSAFE.putObject(buffer, offset(currTail), e);
        tail.lazySet(currTail + 1);

        return true;
    }

    public E poll() {
        final long currHead = head.get();
        if (currHead >= tailCache.get()) {
            final long currTail = tail.get();
            tailCache.set(currTail);
            if (currHead >= currTail) {
                return null;
            }
        }

        final long offset = offset(currHead);
        @SuppressWarnings("unchecked")
        final E e = (E) UnsafeAccess.UNSAFE.getObject(buffer, offset);
        UnsafeAccess.UNSAFE.putObject(buffer, offset, null);

        head.lazySet(currHead + 1);;

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
        long currentHead = head.get();
        return getElement(currentHead);
    }

    @SuppressWarnings("unchecked")
    private E getElement(long index) {
        final long offset = offset(index);
        return (E) UnsafeAccess.UNSAFE.getObject(buffer, offset);
    }

    public int size() {
        return (int) (tail.get() - head.get());
    }

    public boolean isEmpty() {
        return tail.get() == head.get();
    }

    public boolean contains(final Object o) {
        if (null == o) {
            return false;
        }

        for (long i = head.get(), limit = tail.get(); i < limit; i++) {
            final E e = getElement(i);
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
}
