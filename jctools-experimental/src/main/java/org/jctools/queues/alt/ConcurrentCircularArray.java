package org.jctools.queues.alt;

import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

import static org.jctools.util.UnsafeAccess.UNSAFE;

abstract class ConcurrentCircularArrayL0Pad<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

public abstract class ConcurrentCircularArray<E> extends ConcurrentCircularArrayL0Pad<E> {
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 0);
    protected static final int BUFFER_PAD = 32;
    private static final long REF_ARRAY_BASE;
    private static final int REF_ELEMENT_SHIFT;
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
        } else if (8 == scale) {
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
    // @Stable :(
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public ConcurrentCircularArray(int capacity) {
        this.capacity = Pow2.roundToPowerOfTwo(capacity);
        mask = this.capacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }

    public ConcurrentCircularArray(ConcurrentCircularArray<E> c) {
        this.capacity = c.capacity;
        this.mask = c.mask;
        // pad data on either end with some empty slots.
        this.buffer = c.buffer;
    }

    protected final long calcOffset(long index) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }

    protected final void spElement(long offset, E e) {
        UNSAFE.putObject(buffer, offset, e);
    }

    protected final void soElement(long offset, E e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    protected final void svElement(long offset, E e) {
        UNSAFE.putObjectVolatile(buffer, offset, e);
    }

    @SuppressWarnings("unchecked")
    protected final E lpElement(long offset) {
        return (E) UNSAFE.getObject(buffer, offset);
    }

    @SuppressWarnings("unchecked")
    protected final E lvElement(long offset) {
        return (E) UNSAFE.getObjectVolatile(buffer, offset);
    }

    protected final void spElement(E[] buffer, long offset, E e) {
        UNSAFE.putObject(buffer, offset, e);
    }

    protected final void soElement(E[] buffer, long offset, E e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    protected final void svElement(E[] buffer, long offset, E e) {
        UNSAFE.putObjectVolatile(buffer, offset, e);
    }

    @SuppressWarnings("unchecked")
    protected final E lpElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObject(buffer, offset);
    }

    @SuppressWarnings("unchecked")
    protected final E lvElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObjectVolatile(buffer, offset);
    }
}
