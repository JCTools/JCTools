package org.jctools.queues;

import static org.jctools.util.UnsafeAccess.UNSAFE;

import org.jctools.util.UnsafeAccess;

public abstract class ConcurrentSequencedCircularArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {
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
        ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(long[].class)
                + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
    }
    protected final long[] sequenceBuffer;

    public ConcurrentSequencedCircularArrayQueue(int capacity) {
        super(capacity);
        // pad data on either end with some empty slots.
        sequenceBuffer = new long[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
        for (long i = 0; i < this.capacity; i++) {
            soSequence(sequenceBuffer, calcSequenceOffset(i), i);
        }
    }

    protected final long calcSequenceOffset(long index) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }

    protected final void soSequence(long[] buffer, long offset, long e) {
        UNSAFE.putOrderedLong(buffer, offset, e);
    }

    protected final long lvSequence(long[] buffer, long offset) {
        return UNSAFE.getLongVolatile(buffer, offset);
    }

}
