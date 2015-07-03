package org.jctools.queues;

import org.jctools.util.UnsafeAccess;

public final class CircularArrayOffsetCalculator {
    static final long REF_ARRAY_BASE;
    static final int REF_ELEMENT_SHIFT;
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2;
        } else if (8 == scale) {
            REF_ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        REF_ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class);
    }

    private CircularArrayOffsetCalculator() {
    }
    @SuppressWarnings("unchecked")
    public static <E> E[] allocate(int capacity) {
        return (E[]) new Object[capacity];
    }
    /**
     * @param index desirable element index
     * @param mask 
     * @return the offset in bytes within the array for a given index.
     */
    public static long calcElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }
}
