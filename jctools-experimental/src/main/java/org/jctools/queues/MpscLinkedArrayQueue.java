package org.jctools.queues;


import java.util.*;
import java.util.concurrent.atomic.*;

public final class MpscLinkedArrayQueue<T> extends AbstractQueue<T> {
    final AtomicLong producerIndex;
    final AtomicReference<ARA2> producerArray;

    final AtomicLong consumerIndex;
    ARA2 consumerArray;
    int consumerOffset;

    final int maxOffset;

    static final Object ALLOCATING = new Object();

    public MpscLinkedArrayQueue(int arrayCapacity) {
        int c = arrayCapacity;
        this.maxOffset = c - 1;
        ARA2 array = new ARA2(c + 1, 0);
        this.producerIndex = new AtomicLong();
        this.consumerIndex = new AtomicLong();
        this.producerArray = new AtomicReference<ARA2>();
        this.consumerArray = array;
        this.producerArray.lazySet(array);
    }

    @Override
    public boolean offer(T value) {
        Objects.requireNonNull(value);

        final int m = maxOffset;

        ARA2 array = producerArray.get();
        final long index = producerIndex.getAndIncrement();

        long start = array.start;
        long end = array.end;

        if (start - index > 0L) {
            throw new IllegalStateException(index + " vs. " + start);
        } else
        if (index < end) {
            int offset = (int)(index - start);
            array.lazySet(offset, value);
        }
        else { //(index >= end)
            for (;;) {
                Object nextArray = array.next();
                if (nextArray == null) {
                    if (array.casNext(null, ALLOCATING)) {
                        nextArray = new ARA2(m + 2, end);
                        array.svNext(nextArray);
                    } else {
                        while ((nextArray = array.next()) == ALLOCATING);
                    }
                } else {
                    while ((nextArray = array.next()) == ALLOCATING);
                }

                ARA2 nextArray2 = (ARA2)nextArray;
                if (array.end < index) {
                    producerArray.compareAndSet(array, nextArray2);
                }
                array = nextArray2;

                start = end;
                end = array.end;
                if (index < end) {
                    break;
                }
            }
            int offset = (int)(index - start);
            array.lazySet(offset, value);
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T poll() {
        final long index = consumerIndex.get();
        ARA2 array = consumerArray;
        int offset = consumerOffset;
        final int m = maxOffset;
        if (offset > m) {
            for (;;) {
                Object next = array.next();
                if (next == ALLOCATING) {
                    continue;
                }
                if (next != null) {
                    offset = 0;
                    array = (ARA2)next;
                    consumerArray = array;
                    break;
                }
                if (producerIndex.get() <= index) {
                    return null;
                }
            }
        }
        for (;;) {
            Object o = array.get(offset);
            if (o != null) {
                consumerOffset = offset + 1;
                consumerIndex.lazySet(index + 1);
                array.lazySet(offset, null);
                return (T)o;
            }
            if (producerIndex.get() <= index) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public T weakPoll() {
        final long index = consumerIndex.get();
        ARA2 array = consumerArray;
        int offset = consumerOffset;
        final int m = maxOffset;
        if (offset > m) {
            Object next = array.next();
            if (next == null || next == ALLOCATING) {
                return null;
            }
            offset = 0;
            array = (ARA2)next;
            consumerOffset = 0;
            consumerArray = array;
        }
        Object o = array.get(offset);
        if (o != null) {
            consumerOffset = offset + 1;
            consumerIndex.lazySet(index + 1);
            array.lazySet(offset, null);
            return (T)o;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T peek() {
        final long index = consumerIndex.get();
        ARA2 array = consumerArray;
        int offset = consumerOffset;
        final int m = maxOffset;
        if (offset > m) {
            for (;;) {
                Object next = array.next();
                if (next == ALLOCATING) {
                    continue;
                }
                if (next != null) {
                    offset = 0;
                    array = (ARA2)next;
                    break;
                }
                if (producerIndex.get() <= index) {
                    return null;
                }
            }
        }
        for (;;) {
            Object o = array.get(offset);
            if (o != null) {
                return (T)o;
            }
            if (producerIndex.get() <= index) {
                return null;
            }
        }
    }
    @SuppressWarnings("unchecked")
    public T weakPeek() {
        ARA2 array = consumerArray;
        int offset = consumerOffset;
        final int m = maxOffset;
        if (offset > m) {
            Object next = array.next();
            if (next == null || next == ALLOCATING) {
                return null;
            }
            offset = 0;
            array = (ARA2)next;
        }
        Object o = array.get(offset);
        return (T)o;
    }

    @Override
    public boolean isEmpty() {
        return consumerIndex.get() == producerIndex.get();
    }

    @Override
    public int size() {
        long after = consumerIndex.get();
        for (;;) {
            final long before = after;
            final long pidx = producerIndex.get();
            after = consumerIndex.get();
            if (before == after) {
                return (int)(pidx - before);
            }
        }
    }

    static final class ARA2 extends AtomicReferenceArray<Object> {
        /** */
        private static final long serialVersionUID = -2977670280800260365L;
        public final long start;
        public final long end;
        final int nextOffset;
        public ARA2(int capacity, long start) {
            super(capacity);
            this.start = start;
            this.end = start + capacity - 1;
            this.nextOffset = capacity - 1;
        }
        public Object next() {
            return get(nextOffset);
        }
        public boolean casNext(Object expected, Object newValue) {
            return compareAndSet(nextOffset, expected, newValue);
        }
        public void svNext(Object newNext) {
            set(nextOffset, newNext);
        }
    }

    @Override
    public Iterator<T> iterator() {
        throw new UnsupportedOperationException();
    }
}