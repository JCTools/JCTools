package org.jctools.queues;

import static org.jctools.util.UnsafeAccess.UNSAFE;

public final class MpscLinkedQueue8<E> extends MpscLinkedQueue<E> {
    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal) {
        return (LinkedQueueNode<E>) UNSAFE.getAndSetObject(this, P_NODE_OFFSET, newVal);
    }
}
