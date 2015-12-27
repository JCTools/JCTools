package org.jctools.queues;

import static org.jctools.util.UnsafeRefArrayAccess.*;

public final class CircularArrayOffsetCalculator {

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
