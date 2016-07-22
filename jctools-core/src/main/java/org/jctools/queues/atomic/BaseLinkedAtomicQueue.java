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

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import org.jctools.queues.MessagePassingQueue;

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

    protected E getSingleConsumerNodeValue(LinkedQueueAtomicNode<E> currConsumerNode, LinkedQueueAtomicNode<E> nextNode) {
        // we have to null out the value because we are going to hang on to the node
        final E nextValue = nextNode.getAndNullValue();
        // fix up the next ref to prevent promoted nodes from keep new ones alive
        currConsumerNode.soNext(currConsumerNode);
        spConsumerNode(nextNode);
        return nextValue;
    }
}
