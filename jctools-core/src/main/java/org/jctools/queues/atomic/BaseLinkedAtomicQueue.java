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

import org.jctools.queues.MessagePassingQueue;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

abstract class BaseLinkedAtomicQueue<E> extends AbstractQueue<E> {
    private final AtomicReference<LinkedQueueAtomicNode<E>> producerNode;
    private final AtomicReference<LinkedQueueAtomicNode<E>> consumerNode;
    public BaseLinkedAtomicQueue() {
        producerNode = new AtomicReference<LinkedQueueAtomicNode<E>>();
        consumerNode = new AtomicReference<LinkedQueueAtomicNode<E>>();
    }
    protected final LinkedQueueAtomicNode<E> lvProducerNode() {
        return producerNode.get();
    }
    protected final LinkedQueueAtomicNode<E> lpProducerNode() {
        return producerNode.get();
    }
    protected final void spProducerNode(LinkedQueueAtomicNode<E> node) {
        producerNode.lazySet(node);
    }
    protected final LinkedQueueAtomicNode<E> xchgProducerNode(LinkedQueueAtomicNode<E> node) {
        return producerNode.getAndSet(node);
    }
    protected final LinkedQueueAtomicNode<E> lvConsumerNode() {
        return consumerNode.get();
    }

    protected final LinkedQueueAtomicNode<E> lpConsumerNode() {
        return consumerNode.get();
    }
    protected final void spConsumerNode(LinkedQueueAtomicNode<E> node) {
        consumerNode.lazySet(node);
    }
    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method is only safe to call from the consumer thread!
     */
    @Override
    public final boolean remove(Object o) {
        if (null == o) {
            return false; // Null elements are not permitted, so null will never be removed.
        }

        final LinkedQueueAtomicNode<E> originalConsumerNode = lpConsumerNode();
        for (LinkedQueueAtomicNode<E> prevConsumerNode = originalConsumerNode, currConsumerNode = getNextConsumerNode(originalConsumerNode);
             currConsumerNode != null;
             prevConsumerNode = currConsumerNode, currConsumerNode = getNextConsumerNode(currConsumerNode)) {
            if (o.equals(currConsumerNode.lpValue())) {
                LinkedQueueAtomicNode<E> nextNode = getNextConsumerNode(currConsumerNode);
                if (nextNode != null && prevConsumerNode != originalConsumerNode) {
                    // We are removing an interior node.
                    prevConsumerNode.soNext(nextNode);

                    // Avoid GC nepotism because we are discarding the current node.
                    currConsumerNode.soNext(null);
                } else if (prevConsumerNode == originalConsumerNode) {
                    // We are removing the consumer node.
                    prevConsumerNode.soNext(null);
                    spConsumerNode(currConsumerNode);
                } else {
                    // We are removing the producer node.
                    prevConsumerNode.soNext(null);

                    // If the producer node is pointing to the node we are removing then we should try to atomically
                    // update the reference to move it to the previous node.
                    if (!producerNode.compareAndSet(currConsumerNode, prevConsumerNode)) {
                        // If the producer(s) have offered more items we need to remove the currConsumerNode link.
                        while ((nextNode = currConsumerNode.lvNext()) == null);
                        prevConsumerNode.soNext(nextNode);
                    }

                    // Avoid GC nepotism because we are discarding the current node.
                    currConsumerNode.soNext(null);
                }
                currConsumerNode.spValue(null);

                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }

    public final int size() {
        // Read consumer first, this is important because if the producer is node is 'older' than the consumer
        // the consumer may overtake it (consume past it). This will lead to an infinite loop below.
        LinkedQueueAtomicNode<E> chaserNode = lvConsumerNode();
        LinkedQueueAtomicNode<E> producerNode = lvProducerNode();
        int size = 0;
        // must chase the nodes all the way to the producer node, but there's no need to count beyond expected head.
        while (chaserNode != producerNode && // don't go passed producer node
               chaserNode != null && // stop at last node
               size < Integer.MAX_VALUE) // stop at max int
        {
            LinkedQueueAtomicNode<E> next;
            next = chaserNode.lvNext();
            // check if this node has been consumed, if so return what we have
            if (next == chaserNode) {
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
     * Queue is empty when producerNode is the same as consumerNode. An alternative implementation would be to observe
     * the producerNode.value is null, which also means an empty queue because only the consumerNode.value is allowed to
     * be null.
     *
     * @see MessagePassingQueue#isEmpty()
     */
    @Override
    public final boolean isEmpty() {
        return lvConsumerNode() == lvProducerNode();
    }

    abstract LinkedQueueAtomicNode<E> getNextConsumerNode(LinkedQueueAtomicNode<E> currConsumerNode);

    protected E getSingleConsumerNodeValue(LinkedQueueAtomicNode<E> currConsumerNode, LinkedQueueAtomicNode<E> nextNode) {
        // we have to null out the value because we are going to hang on to the node
        final E nextValue = nextNode.getAndNullValue();
        // fix up the next ref to prevent promoted nodes from keep new ones alive
        currConsumerNode.soNext(currConsumerNode);
        spConsumerNode(nextNode);
        return nextValue;
    }
}
