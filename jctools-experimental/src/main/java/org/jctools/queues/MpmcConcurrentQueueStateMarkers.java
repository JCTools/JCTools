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

import static org.jctools.util.UnsafeAccess.UNSAFE;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueConsumer;
import org.jctools.queues.alt.ConcurrentQueueProducer;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

abstract class MpmcConcurrentQueueSMBufferL0Pad {
    public long p00, p01, p02, p03, p04, p05, p06, p07;
    public long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpmcConcurrentQueueSMBuffer<E> extends MpmcConcurrentQueueSMBufferL0Pad {
    private static final int SPARSE_SHIFT = Math.max(1, Integer.getInteger("sparse.shift", 2));// data is sparse to allow the wrap tokens
    private static final int BUFFER_PAD = 32;
    private static final long REF_ARRAY_BASE;
    private static final int REF_ELEMENT_SHIFT;
    protected static final int SIZE_OF_ELEMENT;
    static {
        SIZE_OF_ELEMENT = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
        if (4 == SIZE_OF_ELEMENT) {
            REF_ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
        } else if (8 == SIZE_OF_ELEMENT) {
            REF_ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        // Including the buffer pad in the array base offset
        REF_ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class)
                + (BUFFER_PAD << (REF_ELEMENT_SHIFT - SPARSE_SHIFT));
    }
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public MpmcConcurrentQueueSMBuffer(int capacity) {
        if (Pow2.isPowerOfTwo(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.roundToPowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }

    public MpmcConcurrentQueueSMBuffer(MpmcConcurrentQueueSMBuffer<E> c) {
        this.capacity = c.capacity;
        this.mask = c.mask;
        // pad data on either end with some empty slots.
        this.buffer = c.buffer;
    }

    protected final long calcOffset(long index) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }

    protected final void spElement(E[] buffer, long offset, E e) {
        UNSAFE.putObject(buffer, offset, e);
    }
    protected final void soElement(E[] buffer, long offset, Object e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    protected final void svElement(E[] buffer, long offset, Object e) {
        UNSAFE.putObjectVolatile(buffer, offset, e);
    }

    @SuppressWarnings("unchecked")
    protected final Object lvElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObjectVolatile(buffer, offset);
    }
}

abstract class MpmcConcurrentQueueSML1Pad<E> extends MpmcConcurrentQueueSMBuffer<E> {
    public long p10, p11, p12, p13, p14, p15, p16;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueSML1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueSMTailField<E> extends MpmcConcurrentQueueSML1Pad<E> {
    private final static long TAIL_OFFSET;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueSMTailField.class
                    .getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long tail;

    public MpmcConcurrentQueueSMTailField(int capacity) {
        super(capacity);
    }

    protected final long lvTail() {
        return tail;
    }

    protected final boolean casTail(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
    }
}

abstract class MpmcConcurrentQueueSML2Pad<E> extends MpmcConcurrentQueueSMTailField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueSML2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueSMHeadField<E> extends MpmcConcurrentQueueSML2Pad<E> {
    private final static long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueSMHeadField.class
                    .getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long head;

    public MpmcConcurrentQueueSMHeadField(int capacity) {
        super(capacity);
    }

    protected final long lvHead() {
        return head;
    }

    protected final boolean casHead(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
    }
}

public final class MpmcConcurrentQueueStateMarkers<E> extends MpmcConcurrentQueueSMHeadField<E> implements
        Queue<E>, ConcurrentQueue<E>, ConcurrentQueueProducer<E>, ConcurrentQueueConsumer<E> {
    private static final Object P_OFFER = new Object();
    private static final Object N_OFFER = null;
    private static final Object P_POLL = new Object();
    private static final Object N_POLL = new Object();
    public long p40, p41, p42, p43, p44, p45, p46;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueStateMarkers(final int capacity) {
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
        long currentTail;
        long offsetWrapSign;
        Object wrapSign;
        long currentTailWrapSign;
        Object pollSign;
        final E[] lb = buffer;
        for (;;) {
            currentTail = lvTail();
            offsetWrapSign = calcOffset(currentTail);
            wrapSign = lvElement(lb, offsetWrapSign);
            currentTailWrapSign = capacity & currentTail;
            if (currentTailWrapSign == 0 && wrapSign == N_OFFER) {
                if (casTail(currentTail, currentTail + 1)) {
                    pollSign = N_POLL;
                    break;
                }
            }
            else if (currentTailWrapSign != 0 && wrapSign == P_OFFER) {
                if (casTail(currentTail, currentTail + 1)) {
                    pollSign = P_POLL;
                    break;
                }
            }
            else if(null != lvElement(lb,  offsetWrapSign + SIZE_OF_ELEMENT)) {
                return false;
            }
        }
        spElement(lb, offsetWrapSign + SIZE_OF_ELEMENT, e);
        soElement(lb, offsetWrapSign, pollSign);
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public E poll() {
        E e;
        long currentHead;
        long offsetWrapSign;
        Object wrapSign;
        long currentHeadWrapSign;
        Object offerSign;
        final E[] lb = buffer;
        for (;;) {
            currentHead = lvHead();
            offsetWrapSign =  calcOffset(currentHead);
            wrapSign = lvElement(lb, offsetWrapSign);
            currentHeadWrapSign = capacity & currentHead;

            if (currentHeadWrapSign == 0 && wrapSign == N_POLL) {
                if (casHead(currentHead, currentHead + 1)) {
                    offerSign = P_OFFER;
                    break;
                }
            }
            else if (currentHeadWrapSign != 0 && wrapSign == P_POLL) {
                if (casHead(currentHead, currentHead + 1)) {
                    offerSign = N_OFFER;
                    break;
                }
            }
            else if(null == lvElement(lb, offsetWrapSign + SIZE_OF_ELEMENT)) {
                return null;
            }
        }
        final long offsetE = offsetWrapSign + SIZE_OF_ELEMENT;
        e = (E) lvElement(lb, offsetE);
        spElement(lb,offsetE, null);
        soElement(lb, offsetWrapSign, offerSign);
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
    @SuppressWarnings("unchecked")
    public E peek() {
        return (E) lvElement(buffer, calcOffset(lvHead()) + SIZE_OF_ELEMENT);
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
            @SuppressWarnings("unchecked")
            final E e = (E) lvElement(buffer, calcOffset(i) + SIZE_OF_ELEMENT);
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
