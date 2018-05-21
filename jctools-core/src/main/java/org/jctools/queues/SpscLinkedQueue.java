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

/**
 * This is a weakened version of the MPSC algorithm as presented
 * <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on
 * 1024 Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory
 * model and layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>As this is an SPSC we have no need for XCHG, an ordered store is enough.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references.
 * From this point follow the notes on offer/poll.
 *
 * @param <E>
 * @author nitsanw
 */
public class SpscLinkedQueue<E> extends BaseLinkedQueue<E>
{

    public SpscLinkedQueue()
    {
        LinkedQueueNode<E> node = newNode();
        spProducerNode(node);
        spConsumerNode(node);
        node.soNext(null); // this ensures correct construction: StoreStore
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from a SINGLE thread.<br>
     * Offer allocates a new node (holding the offered value) and:
     * <ol>
     * <li>Sets that node as the producerNode.next
     * <li>Sets the new node as the producerNode
     * </ol>
     * From this follows that producerNode.next is always null and for all other nodes node.next is not null.
     *
     * @see MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final LinkedQueueNode<E> nextNode = newNode(e);
        lpProducerNode().soNext(nextNode);
        spProducerNode(nextNode);
        return true;
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is empty.
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue.
     * Because null values are not allowed to be offered this is the only node with it's value set to null at
     * any one time.
     */
    @Override
    public E poll()
    {
        return relaxedPoll();
    }

    @Override
    public E peek()
    {
        return relaxedPeek();
    }

    @Override
    public int fill(Supplier<E> s)
    {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        do
        {
            fill(s, 4096);
            result += 4096;
        }
        while (result <= Integer.MAX_VALUE - 4096);
        return (int) result;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        if (limit == 0)
        {
            return 0;
        }
        LinkedQueueNode<E> tail = newNode(s.get());
        final LinkedQueueNode<E> head = tail;
        for (int i = 1; i < limit; i++)
        {
            final LinkedQueueNode<E> temp = newNode(s.get());
            tail.soNext(temp);
            tail = temp;
        }
        final LinkedQueueNode<E> oldPNode = lpProducerNode();
        oldPNode.soNext(head);
        spProducerNode(tail);
        return limit;
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        LinkedQueueNode<E> chaserNode = lpProducerNode();
        while (exit.keepRunning())
        {
            for (int i = 0; i < 4096; i++)
            {
                final LinkedQueueNode<E> nextNode = newNode(s.get());
                chaserNode.soNext(nextNode);
                chaserNode = nextNode;
                this.spProducerNode(chaserNode);
            }
        }
    }
}
