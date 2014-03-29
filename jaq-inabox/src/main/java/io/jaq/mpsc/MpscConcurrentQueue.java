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
package io.jaq.mpsc;

import static io.jaq.util.UnsafeAccess.UNSAFE;
import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.common.ConcurrentRingBuffer;
import io.jaq.util.UnsafeAccess;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

abstract class MpscConcurrentQueueL1Pad<E> extends ConcurrentRingBuffer<E> {
    public long p10, p11, p12, p13, p14, p15, p16;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscConcurrentQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscConcurrentQueueTailField<E> extends MpscConcurrentQueueL1Pad<E> {
    private final static long TAIL_OFFSET;

    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpscConcurrentQueueTailField.class
                    .getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long tail;

    public MpscConcurrentQueueTailField(int capacity) {
        super(capacity);
    }

    protected final long lvTail() {
        return tail;
    }

    protected final boolean casTail(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
    }

}

abstract class MpscConcurrentQueueL2Pad<E> extends MpscConcurrentQueueTailField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscConcurrentQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscConcurrentQueueHeadField<E> extends MpscConcurrentQueueL2Pad<E> {
    private final static long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UNSAFE.objectFieldOffset(MpscConcurrentQueueHeadField.class
                    .getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected long head;

    public MpscConcurrentQueueHeadField(int capacity) {
        super(capacity);
    }

    protected final long lvHead() {
        return UNSAFE.getLongVolatile(this, HEAD_OFFSET);
    }

    protected void soHead(long l) {
        UNSAFE.putOrderedLong(this, HEAD_OFFSET, l);
    }
}

public final class MpscConcurrentQueue<E> extends MpscConcurrentQueueHeadField<E> implements Queue<E>,
        ConcurrentQueue<E>, ConcurrentQueueProducer<E>, ConcurrentQueueConsumer<E> {
    public long p40, p41, p42, p43, p44, p45, p46;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscConcurrentQueue(final int capacity) {
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
        long currentTail;
        long offset;
        E currE;
        for(;;) {
            currentTail = lvTail();
            offset = calcOffset(currentTail);
            currE = lvElement(lb, offset);
            if(currE == null) {
                if (casTail(currentTail, currentTail + 1)) {
                    break;
                }
            }
            else {
                return false;
            }
        }

        soElement(lb, offset, e);
        return true;
    }

    public int offerStatus(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        final E[] lb = buffer;
        long currentTail = lvTail();
        long offset = calcOffset(currentTail);
        E currE = lvElement(lb, offset);
        if(currE == null) {
            if (!casTail(currentTail, currentTail + 1)) {
                return -1;
            }
            // continue to exit, collect 200$
        }
        else {
            return 1;
        }

        soElement(lb, offset, e);
        return 0;
    }

    @Override
    public E poll() {
        final long offset = calcOffset(head);
        final E[] lb = buffer;
        final E e = lvElement(lb, offset);
        if (null == e) {
            return null;
        }
        soElement(lb, offset, null);
        head++;
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
