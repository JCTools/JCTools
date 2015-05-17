package org.jctools.queues.alt;

import org.jctools.util.UnsafeAccess;

import static org.jctools.util.UnsafeAccess.UNSAFE;

public abstract class ConcurrentSequencedCircularArray<E> extends ConcurrentCircularArray<E> {
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(long[].class);
        if (8 == scale) {
            ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
        } else {
            throw new IllegalStateException("Unexpected long[] element size");
        }
        // Including the buffer pad in the array base offset
        ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(long[].class) + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
    }
    protected final long[] sequenceBuffer;

    public ConcurrentSequencedCircularArray(int capacity) {
        super(capacity);
        // pad data on either end with some empty slots.
        int actualCapacity = (int)this.mask + 1;
        sequenceBuffer = new long[(actualCapacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
        for (long i = 0; i < actualCapacity; i++) {
            soSequenceElement(calcSequenceOffset(i), i);
        }
    }

    public ConcurrentSequencedCircularArray(ConcurrentSequencedCircularArray<E> c) {
        super(c);
        this.sequenceBuffer = c.sequenceBuffer;
    }

    protected final long calcSequenceOffset(long index) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }
    protected final long calcSequenceOffset(long index, long mask) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }

    protected final void spSequenceElement(long offset, long e) {
        UNSAFE.putLong(sequenceBuffer, offset, e);
    }

    protected final void soSequenceElement(long offset, long e) {
        UNSAFE.putOrderedLong(sequenceBuffer, offset, e);
    }

    protected final void svSequenceElement(long offset, long e) {
        UNSAFE.putLongVolatile(sequenceBuffer, offset, e);
    }

    protected final long lpSequence(long offset) {
        return UNSAFE.getLong(sequenceBuffer, offset);
    }

    protected final long lvSequence(long offset) {
        return UNSAFE.getLongVolatile(sequenceBuffer, offset);
    }

    protected final void spSequenceElement(long[] buffer, long offset, long e) {
        UNSAFE.putLong(buffer, offset, e);
    }

    protected final void soSequence(long[] buffer, long offset, long e) {
        UNSAFE.putOrderedLong(buffer, offset, e);
    }

    protected final void svSequenceElement(long[] buffer, long offset, long e) {
        UNSAFE.putLongVolatile(buffer, offset, e);
    }

    protected final long lpSequenceElement(long[] buffer, long offset) {
        return UNSAFE.getLong(buffer, offset);
    }

    protected final long lvSequence(long[] buffer, long offset) {
        return UNSAFE.getLongVolatile(buffer, offset);
    }

}
