/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

abstract class BaseLinkedQueuePad0<E> extends AbstractQueue<E> implements MessagePassingQueue<E>
{
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class BaseLinkedQueueProducerNodeRef<E> extends BaseLinkedQueuePad0<E>
{
    final static long P_NODE_OFFSET = fieldOffset(BaseLinkedQueueProducerNodeRef.class, "producerNode");

    private LinkedQueueNode<E> producerNode;

    final void spProducerNode(LinkedQueueNode<E> newValue)
    {
        producerNode = newValue;
    }

    @SuppressWarnings("unchecked")
    final LinkedQueueNode<E> lvProducerNode()
    {
        return (LinkedQueueNode<E>) UNSAFE.getObjectVolatile(this, P_NODE_OFFSET);
    }

    @SuppressWarnings("unchecked")
    final boolean casProducerNode(LinkedQueueNode<E> expect, LinkedQueueNode<E> newValue)
    {
        return UNSAFE.compareAndSwapObject(this, P_NODE_OFFSET, expect, newValue);
    }

    final LinkedQueueNode<E> lpProducerNode()
    {
        return producerNode;
    }
}

abstract class BaseLinkedQueuePad1<E> extends BaseLinkedQueueProducerNodeRef<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

//$gen:ordered-fields
abstract class BaseLinkedQueueConsumerNodeRef<E> extends BaseLinkedQueuePad1<E>
{
    private final static long C_NODE_OFFSET = fieldOffset(BaseLinkedQueueConsumerNodeRef.class,"consumerNode");

    private LinkedQueueNode<E> consumerNode;

    final void spConsumerNode(LinkedQueueNode<E> newValue)
    {
        consumerNode = newValue;
    }

    @SuppressWarnings("unchecked")
    final LinkedQueueNode<E> lvConsumerNode()
    {
        return (LinkedQueueNode<E>) UNSAFE.getObjectVolatile(this, C_NODE_OFFSET);
    }

    final LinkedQueueNode<E> lpConsumerNode()
    {
        return consumerNode;
    }
}

abstract class BaseLinkedQueuePad2<E> extends BaseLinkedQueueConsumerNodeRef<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

/**
 * A base data structure for concurrent linked queues. For convenience also pulled in common single consumer
 * methods since at this time there's no plan to implement MC.
 *
 * @param <E>
 * @author nitsanw
 */
abstract class BaseLinkedQueue<E> extends BaseLinkedQueuePad2<E>
{

    @Override
    public final Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    protected final LinkedQueueNode<E> newNode()
    {
        return new LinkedQueueNode<E>();
    }

    protected final LinkedQueueNode<E> newNode(E e)
    {
        return new LinkedQueueNode<E>(e);
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * This is an O(n) operation as we run through all the nodes and count them.<br>
     * The accuracy of the value returned by this method is subject to races with producer/consumer threads. In
     * particular when racing with the consumer thread this method may under estimate the size.<br>
     *
     * @see java.util.Queue#size()
     */
    @Override
    public final int size()
    {
        // Read consumer first, this is important because if the producer is node is 'older' than the consumer
        // the consumer may overtake it (consume past it) invalidating the 'snapshot' notion of size.
        LinkedQueueNode<E> chaserNode = lvConsumerNode();
        LinkedQueueNode<E> producerNode = lvProducerNode();
        int size = 0;
        // must chase the nodes all the way to the producer node, but there's no need to count beyond expected head.
        while (chaserNode != producerNode && // don't go passed producer node
            chaserNode != null && // stop at last node
            size < Integer.MAX_VALUE) // stop at max int
        {
            LinkedQueueNode<E> next;
            next = chaserNode.lvNext();
            // check if this node has been consumed, if so return what we have
            if (next == chaserNode)
            {
                return size;
            }
            chaserNode = next;
            size++;
        }
        return size;
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Queue is empty when producerNode is the same as consumerNode. An alternative implementation would be to
     * observe the producerNode.value is null, which also means an empty queue because only the
     * consumerNode.value is allowed to be null.
     *
     * @see MessagePassingQueue#isEmpty()
     */
    @Override
    public final boolean isEmpty()
    {
        return lvConsumerNode() == lvProducerNode();
    }

    protected E getSingleConsumerNodeValue(LinkedQueueNode<E> currConsumerNode, LinkedQueueNode<E> nextNode)
    {
        // we have to null out the value because we are going to hang on to the node
        final E nextValue = nextNode.getAndNullValue();

        // Fix up the next ref of currConsumerNode to prevent promoted nodes from keeping new ones alive.
        // We use a reference to self instead of null because null is already a meaningful value (the next of
        // producer node is null).
        currConsumerNode.soNext(currConsumerNode);
        spConsumerNode(nextNode);
        // currConsumerNode is now no longer referenced and can be collected
        return nextValue;
    }

    @Override
    public E relaxedPoll()
    {
        final LinkedQueueNode<E> currConsumerNode = lpConsumerNode();
        final LinkedQueueNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null)
        {
            return getSingleConsumerNodeValue(currConsumerNode, nextNode);
        }
        return null;
    }

    @Override
    public E relaxedPeek()
    {
        final LinkedQueueNode<E> nextNode = lpConsumerNode().lvNext();
        if (nextNode != null)
        {
            return nextNode.lpValue();
        }
        return null;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @Override
    public int drain(Consumer<E> c)
    {
        long result = 0;// use long to force safepoint into loop below
        int drained;
        do
        {
            drained = drain(c, 4096);
            result += drained;
        }
        while (drained == 4096 && result <= Integer.MAX_VALUE - 4096);
        return (int) result;
    }

    @Override
    public int drain(Consumer<E> c, int limit)
    {
        LinkedQueueNode<E> chaserNode = this.lpConsumerNode();
        for (int i = 0; i < limit; i++)
        {
            final LinkedQueueNode<E> nextNode = chaserNode.lvNext();

            if (nextNode == null)
            {
                return i;
            }
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = getSingleConsumerNodeValue(chaserNode, nextNode);
            chaserNode = nextNode;
            c.accept(nextValue);
        }
        return limit;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        LinkedQueueNode<E> chaserNode = this.lpConsumerNode();
        int idleCounter = 0;
        while (exit.keepRunning())
        {
            for (int i = 0; i < 4096; i++)
            {
                final LinkedQueueNode<E> nextNode = chaserNode.lvNext();
                if (nextNode == null)
                {
                    idleCounter = wait.idle(idleCounter);
                    continue;
                }

                idleCounter = 0;
                // we have to null out the value because we are going to hang on to the node
                final E nextValue = getSingleConsumerNodeValue(chaserNode, nextNode);
                chaserNode = nextNode;
                c.accept(nextValue);
            }
        }
    }

    @Override
    public int capacity()
    {
        return UNBOUNDED_CAPACITY;
    }

}
