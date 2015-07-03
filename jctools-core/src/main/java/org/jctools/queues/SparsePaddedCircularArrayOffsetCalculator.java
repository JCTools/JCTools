package org.jctools.queues;


public final class SparsePaddedCircularArrayOffsetCalculator {
    static final int SPARSE_SHIFT = Integer.getInteger("org.jctools.sparse.shift", 0);
    private static final long REF_ARRAY_BASE;
    private static final int REF_ELEMENT_SHIFT;
    static {
        REF_ELEMENT_SHIFT = CircularArrayOffsetCalculator.REF_ELEMENT_SHIFT + SPARSE_SHIFT;
        REF_ARRAY_BASE = PaddedCircularArrayOffsetCalculator.REF_ARRAY_BASE;
    }

    private SparsePaddedCircularArrayOffsetCalculator() {
    }

    @SuppressWarnings("unchecked")
    public static <E> E[] allocate(int capacity) {
        return (E[]) new Object[(capacity << SPARSE_SHIFT) +
                                PaddedCircularArrayOffsetCalculator.REF_BUFFER_PAD * 2];
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
