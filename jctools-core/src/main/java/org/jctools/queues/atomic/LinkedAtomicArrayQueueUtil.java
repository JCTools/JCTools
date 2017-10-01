package org.jctools.queues.atomic;

import java.util.concurrent.atomic.AtomicReferenceArray;

final class LinkedAtomicArrayQueueUtil
{
    private LinkedAtomicArrayQueueUtil()
    {
    }

    public static <E> E lvElement(AtomicReferenceArray<E> buffer, int offset)
    {
        return AtomicReferenceArrayQueue.lvElement(buffer, offset);
    }

    public static <E> E lpElement(AtomicReferenceArray<E> buffer, int offset)
    {
        return AtomicReferenceArrayQueue.lpElement(buffer, offset);
    }

    public static <E> void spElement(AtomicReferenceArray<E> buffer, int offset, E value)
    {
        AtomicReferenceArrayQueue.spElement(buffer, offset, value);
    }

    public static <E> void svElement(AtomicReferenceArray<E> buffer, int offset, E value)
    {
        AtomicReferenceArrayQueue.svElement(buffer, offset, value);
    }

    static <E> void soElement(AtomicReferenceArray buffer, int offset, Object value)
    {
        buffer.lazySet(offset, value);
    }

    static int calcElementOffset(long index, long mask)
    {
        return (int) (index & mask);
    }

    static <E> AtomicReferenceArray<E> allocate(int capacity)
    {
        return new AtomicReferenceArray<E>(capacity);
    }

    static int length(AtomicReferenceArray<?> buf)
    {
        return buf.length();
    }

    /**
     * This method assumes index is actually (index << 1) because lower bit is used for resize hence the >> 1
     */
    static int modifiedCalcElementOffset(long index, long mask)
    {
        return (int) (index & mask) >> 1;
    }

    static int nextArrayOffset(AtomicReferenceArray<?> curr)
    {
        return length(curr) - 1;
    }

}
