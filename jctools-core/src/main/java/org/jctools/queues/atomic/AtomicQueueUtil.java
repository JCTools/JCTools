package org.jctools.queues.atomic;

import org.jctools.util.InternalAPI;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

@InternalAPI
public final class AtomicQueueUtil
{
    public static <E> E lvRefElement(AtomicReferenceArray<E> buffer, int offset)
    {
        return buffer.get(offset);
    }

    public static <E> E lpRefElement(AtomicReferenceArray<E> buffer, int offset)
    {
        return buffer.get(offset); // no weaker form available
    }

    public static <E> void spRefElement(AtomicReferenceArray<E> buffer, int offset, E value)
    {
        buffer.lazySet(offset, value);  // no weaker form available
    }

    public static void soRefElement(AtomicReferenceArray buffer, int offset, Object value)
    {
        buffer.lazySet(offset, value);
    }

    public static <E> void svRefElement(AtomicReferenceArray<E> buffer, int offset, E value)
    {
        buffer.set(offset, value);
    }

    public static int calcRefElementOffset(long index)
    {
        return (int) index;
    }

    public static int calcCircularRefElementOffset(long index, long mask)
    {
        return (int) (index & mask);
    }

    public static <E> AtomicReferenceArray<E> allocateRefArray(int capacity)
    {
        return new AtomicReferenceArray<E>(capacity);
    }

    public static void spLongElement(AtomicLongArray buffer, int offset, long e)
    {
        buffer.lazySet(offset, e);
    }

    public static void soLongElement(AtomicLongArray buffer, int offset, long e)
    {
        buffer.lazySet(offset, e);
    }

    public static long lpLongElement(AtomicLongArray buffer, int offset)
    {
        return buffer.get(offset);
    }

    public static long lvLongElement(AtomicLongArray buffer, int offset)
    {
        return buffer.get(offset);
    }

    public static int calcLongElementOffset(long index)
    {
        return (int) index;
    }

    public static int calcCircularLongElementOffset(long index, int mask)
    {
        return (int) (index & mask);
    }

    public static AtomicLongArray allocateLongArray(int capacity)
    {
        return new AtomicLongArray(capacity);
    }

    public static int length(AtomicReferenceArray<?> buf)
    {
        return buf.length();
    }

    /**
     * This method assumes index is actually (index << 1) because lower bit is used for resize hence the >> 1
     */
    public static int modifiedCalcCircularRefElementOffset(long index, long mask)
    {
        return (int) (index & mask) >> 1;
    }

    public static int nextArrayOffset(AtomicReferenceArray<?> curr)
    {
        return length(curr) - 1;
    }

}
