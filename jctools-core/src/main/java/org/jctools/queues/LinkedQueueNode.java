package org.jctools.queues;

import static org.jctools.util.UnsafeAccess.UNSAFE;

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
    private final E value;
    private LinkedQueueNode<E> next;

    LinkedQueueNode(){
        this(null);
    }
    LinkedQueueNode(E val) {
        value = val;
    }

    public E lpValue() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public E lvValue() {
        return (E) UNSAFE.getObjectVolatile(this, VALUE_OFFSET);
    }

    public void soNext(LinkedQueueNode<E> n) {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, n);
    }

    @SuppressWarnings("unchecked")
    public LinkedQueueNode<E> lvNext() {
        return (LinkedQueueNode<E>) UNSAFE.getObjectVolatile(this, NEXT_OFFSET);
    }
}
