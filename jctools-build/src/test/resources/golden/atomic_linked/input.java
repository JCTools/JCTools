/*
 * Licensed under the Apache License, Version 2.0.
 */
package org.jctools.queues;

import java.util.AbstractQueue;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

/**
 * Synthetic MPSC linked queue exercising the atomic-linked generator's full feature set,
 * mirroring the real MpscLinkedQueue (public/concrete with a public no-arg ctor).
 */
public class MpscLinkedQueue<E> extends BaseLinkedQueue<E>
{
    public MpscLinkedQueue()
    {
    }

    public static <E> MpscLinkedQueue<E> newMpscLinkedQueue()
    {
        return null;
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final LinkedQueueNode<E> nextNode = new LinkedQueueNode<E>(e);
        final LinkedQueueNode<E> prevProducerNode = xchgProducerNode(nextNode);
        prevProducerNode.soNext(nextNode);
        return true;
    }
}

// $gen:ordered-fields
abstract class BaseLinkedQueueProducerNodeRef<E> extends AbstractQueue<E>
{
    private final static long P_NODE_OFFSET =
            fieldOffset(BaseLinkedQueueProducerNodeRef.class, "producerNode");

    private LinkedQueueNode<E> producerNode;

    protected final LinkedQueueNode<E> lvProducerNode()
    {
        return (LinkedQueueNode<E>) UNSAFE.getObject(this, P_NODE_OFFSET);
    }

    protected final boolean casProducerNode(LinkedQueueNode<E> expect, LinkedQueueNode<E> newValue)
    {
        return UNSAFE.compareAndSwapObject(this, P_NODE_OFFSET, expect, newValue);
    }

    protected final void soProducerNode(LinkedQueueNode<E> newValue)
    {
        UNSAFE.putOrderedObject(this, P_NODE_OFFSET, newValue);
    }

    protected final LinkedQueueNode<E> lpProducerNode()
    {
        return producerNode;
    }
}
