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
package org.jctools.queues.atomic;

/**
 * This is a direct Java port of the MPSC algorithm as presented <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on 1024
 * Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory model and
 * layout:
 * <ol>
 * <li>Use XCHG functionality provided by AtomicReference (which is better in JDK 8+).
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references. From this
 * point follow the notes on offer/poll.
 *
 * @author nitsanw
 *
 * @param <E>
 */
public final class MpscLinkedAtomicQueue<E> extends BaseLinkedAtomicQueue<E> {

    public MpscLinkedAtomicQueue() {
        super();
        LinkedQueueAtomicNode<E> node = new LinkedQueueAtomicNode<E>();
        spConsumerNode(node);
        xchgProducerNode(node);// this ensures correct construction: StoreLoad
    }
    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from multiple threads.<br>
     * Offer allocates a new node and:
     * <ol>
     * <li>Swaps it atomically with current producer node (only one producer 'wins')
     * <li>Sets the new node as the node following from the swapped producer node
     * </ol>
     * This works because each producer is guaranteed to 'plant' a new node and link the old node. No 2 producers can
     * get the same producer node as part of XCHG guarantee.
     *
     * @see org.jctools.queues.MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public final boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }
        final LinkedQueueAtomicNode<E> nextNode = new LinkedQueueAtomicNode<E>(e);
        final LinkedQueueAtomicNode<E> prevProducerNode = xchgProducerNode(nextNode);
        // Should a producer thread get interrupted here the chain WILL be broken until that thread is resumed
        // and completes the store in prev.next.
        prevProducerNode.soNext(nextNode); // StoreStore
        return true;
    }


    /**
     * {@inheritDoc}
     * <p>
     * This method is only safe to call from the (single) consumer thread, and is subject to best effort when racing
     * with producers.
     */
    @Override
    public final boolean remove(Object o)
    {
        if (null == o)
        {
            return false; // Null elements are not permitted, so null will never be removed.
        }

        final LinkedQueueAtomicNode<E> originalConsumerNode = lpConsumerNode();
        LinkedQueueAtomicNode<E> prevConsumerNode = originalConsumerNode;
        LinkedQueueAtomicNode<E> currConsumerNode = getNextConsumerNode(originalConsumerNode);
        while (currConsumerNode != null)
        {
            if (o.equals(currConsumerNode.lpValue()))
            {
                LinkedQueueAtomicNode<E> nextNode = getNextConsumerNode(currConsumerNode);
                // e.g.: consumerNode -> node0 -> node1(o==v) -> node2 ... => consumerNode -> node0 -> node2
                if (nextNode != null)
                {
                    // We are removing an interior node.
                    prevConsumerNode.soNext(nextNode);

                }
                // This case reflects: prevConsumerNode != originalConsumerNode && nextNode == null
                // At rest, this would be the producerNode, but we must contend with racing. Changes to subclassed
                // queues need to consider remove() when implementing offer().
                else
                {
                    // producerNode is currConsumerNode, try to atomically update the reference to move it to the
                    // previous node.
                    prevConsumerNode.soNext(null);
                    if (!producerNode.compareAndSet(currConsumerNode, prevConsumerNode))
                    {
                        // If the producer(s) have offered more items we need to remove the currConsumerNode link.
                        while ((nextNode = currConsumerNode.lvNext()) == null)
                        {
                            ;
                        }
                        prevConsumerNode.soNext(nextNode);
                    }
                }

                // Avoid GC nepotism because we are discarding the current node.
                currConsumerNode.soNext(null);
                currConsumerNode.spValue(null);

                return true;
            }
            prevConsumerNode = currConsumerNode;
            currConsumerNode = getNextConsumerNode(currConsumerNode);
        }
        return false;
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is assumed empty (though it might not be).
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue. Because null
     * values are not allowed to be offered this is the only node with it's value set to null at any one time.
     *
     * @see org.jctools.queues.MessagePassingQueue#poll()
     * @see java.util.Queue#poll()
     */
    @Override
    public final E poll() {
        LinkedQueueAtomicNode<E> currConsumerNode = lpConsumerNode(); // don't load twice, it's alright
        LinkedQueueAtomicNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null) {
            return getSingleConsumerNodeValue(currConsumerNode, nextNode);
        }
        else if (currConsumerNode != lvProducerNode()) {
            // spin, we are no longer wait free
            while((nextNode = currConsumerNode.lvNext()) == null);
            // got the next node...

            return getSingleConsumerNodeValue(currConsumerNode, nextNode);
        }
        return null;
    }

    @Override
    public final E peek() {
        LinkedQueueAtomicNode<E> currConsumerNode = lpConsumerNode(); // don't load twice, it's alright
        LinkedQueueAtomicNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null) {
            return nextNode.lpValue();
        }
        else if (currConsumerNode != lvProducerNode()) {
            // spin, we are no longer wait free
            while((nextNode = currConsumerNode.lvNext()) == null);
            // got the next node...
            return nextNode.lpValue();
        }
        return null;
    }

    @Override
    LinkedQueueAtomicNode<E> getNextConsumerNode(LinkedQueueAtomicNode<E> currConsumerNode) {
        LinkedQueueAtomicNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode == null && currConsumerNode != lvProducerNode()) {
            // spin, we are no longer wait free
            while ((nextNode = currConsumerNode.lvNext()) == null);
        }
        return nextNode;
    }
}
