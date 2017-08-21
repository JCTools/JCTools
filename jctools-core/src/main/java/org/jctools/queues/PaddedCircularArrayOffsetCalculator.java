package org.jctools.queues;

import org.jctools.util.InternalAPI;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.UnsafeRefArrayAccess;

@InternalAPI
public final class PaddedCircularArrayOffsetCalculator
{
    static final int REF_BUFFER_PAD;
    static final long REF_ARRAY_BASE;

    static
    {
        // 2 cache lines pad
        REF_BUFFER_PAD = (PortableJvmInfo.CACHE_LINE_SIZE * 2) >> UnsafeRefArrayAccess.REF_ELEMENT_SHIFT;
        // Including the buffer pad in the array base offset
        final int paddingOffset = REF_BUFFER_PAD << UnsafeRefArrayAccess.REF_ELEMENT_SHIFT;
        REF_ARRAY_BASE = UnsafeRefArrayAccess.REF_ARRAY_BASE + paddingOffset;
    }

    @SuppressWarnings("unchecked")
    public static <E> E[] allocate(int capacity)
    {
        return (E[]) new Object[capacity + REF_BUFFER_PAD * 2];
    }

    /**
     * @param index desirable element index
     * @param mask (length - 1)
     * @return the offset in bytes within the array for a given index.
     */
    protected static long calcElementOffset(long index, long mask)
    {
        return REF_ARRAY_BASE + ((index & mask) << UnsafeRefArrayAccess.REF_ELEMENT_SHIFT);
    }
}
