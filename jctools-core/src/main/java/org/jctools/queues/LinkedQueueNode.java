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
    private volatile E value;
    private volatile LinkedQueueNode<E> next;

    LinkedQueueNode(){
        this(null);
    }
    LinkedQueueNode(E val) {
        soValue(val);
    }

    /**
     * Gets the current value and nulls out the reference to it from this node.
     * @return value
     */
    public E evacuateValue() {
        E temp = value;
        soValue(null);
        return temp;
    }

    public E lvValue() {
        return value;
    }
    public void soValue(E newValue) {
        UNSAFE.putOrderedObject(this, VALUE_OFFSET, newValue);
    }
    public void soNext(LinkedQueueNode<E> n) {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, n);
    }

    public LinkedQueueNode<E> lvNext() {
        return next;
    }
}
