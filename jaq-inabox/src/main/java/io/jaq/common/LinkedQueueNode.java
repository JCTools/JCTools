package io.jaq.common;

import static io.jaq.util.UnsafeAccess.UNSAFE;

public final class LinkedQueueNode<E> {
    private final static long VALUE_OFFSET;
    private final static long NEXT_OFFSET;
    static {
        try {
            VALUE_OFFSET = UNSAFE.objectFieldOffset(LinkedQueueNode.class.getDeclaredField("value"));
            NEXT_OFFSET = UNSAFE.objectFieldOffset(LinkedQueueNode.class.getDeclaredField("next"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private E value;
    private LinkedQueueNode<E> next;

    public void spValue(E v) {
        value = v;
    }

    public void soValue(E v) {
        UNSAFE.putOrderedObject(this, VALUE_OFFSET, v);
    }

    public void svValue(E v) {
        UNSAFE.putObjectVolatile(this, VALUE_OFFSET, v);
    }

    public E lpValue() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public E lvValue() {
        return (E) UNSAFE.getObjectVolatile(this, VALUE_OFFSET);
    }

    public void spNext(LinkedQueueNode<E> n) {
        next = n;
    }

    public void soNext(LinkedQueueNode<E> n) {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, n);
    }

    public void svNext(LinkedQueueNode<E> n) {
        UNSAFE.putObjectVolatile(this, NEXT_OFFSET, n);
    }

    public boolean casNext(LinkedQueueNode<E> currV, LinkedQueueNode<E> nextV) {
        return UNSAFE.compareAndSwapObject(this, NEXT_OFFSET, currV, nextV);
    }

    public LinkedQueueNode<E> xchgNext(LinkedQueueNode<E> nextV) {
        LinkedQueueNode<E> old;
        do {
            old = lvNext();
        } while (!casNext(old, nextV));
        return old;
        // for JDK8 replace with getAndSet
        // return UNSAFE.getAndSet(this, NEXT_OFFSET, n);
    }

    public LinkedQueueNode<E> lpNext() {
        return next;
    }

    @SuppressWarnings("unchecked")
    public LinkedQueueNode<E> lvNext() {
        return (LinkedQueueNode<E>) UNSAFE.getObjectVolatile(this, NEXT_OFFSET);
    }
}
