package org.jctools.queues;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * {@inheritDoc}
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public final class MpscLinkedQueue7<E> extends MpscLinkedQueue<E> {
    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal) {
        Object oldVal;
        do {
            oldVal = producerNode;
        } while(!UNSAFE.compareAndSwapObject(this, P_NODE_OFFSET, oldVal, newVal));
        return (LinkedQueueNode<E>) oldVal;
    }
}
