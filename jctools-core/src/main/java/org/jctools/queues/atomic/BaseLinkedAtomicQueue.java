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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jctools.queues.MessagePassingQueue;

abstract class BaseLinkedAtomicQueue<E> extends AbstractQueue<E> {
    private static final AtomicReferenceFieldUpdater<BaseLinkedAtomicQueue, LinkedQueueAtomicNode> P_NODE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(BaseLinkedAtomicQueue.class, LinkedQueueAtomicNode.class, "producerNode");
    private static final AtomicReferenceFieldUpdater<BaseLinkedAtomicQueue, LinkedQueueAtomicNode> C_NODE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(BaseLinkedAtomicQueue.class, LinkedQueueAtomicNode.class, "consumerNode");
    
    private volatile LinkedQueueAtomicNode<E> producerNode;
    private volatile LinkedQueueAtomicNode<E> consumerNode;

    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
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
     * Queue is empty when producerNode is the same as consumerNode. An alternative implementation would be to
     * observe the producerNode.value is null, which also means an empty queue because only the
     * consumerNode.value is allowed to be null.
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
        // Fix up the next ref of currConsumerNode to prevent promoted nodes from keeping new ones alive.
        currConsumerNode.soNext(currConsumerNode);
        spConsumerNode(nextNode);
        return nextValue;
    }

    protected E relaxedPoll() {
        final LinkedQueueAtomicNode<E> currConsumerNode = lpConsumerNode();
        final LinkedQueueAtomicNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null) {
            return getSingleConsumerNodeValue(currConsumerNode, nextNode);
        }
        return null;
    }

    protected E relaxedPeek() {
        final LinkedQueueAtomicNode<E> nextNode = lpConsumerNode().lvNext();
        if (nextNode != null) {
            return nextNode.lpValue();
        }
        return null;
    }
    
    protected final LinkedQueueAtomicNode<E> lvProducerNode() {
        return producerNode;
    }
    
    protected final LinkedQueueAtomicNode<E> lpProducerNode() {
        return lvProducerNode();
    }
    
    protected final void spProducerNode(LinkedQueueAtomicNode<E> node) {
        P_NODE_UPDATER.lazySet(this, node);
    }
    
    protected final LinkedQueueAtomicNode<E> xchgProducerNode(LinkedQueueAtomicNode<E> node) {
        return P_NODE_UPDATER.getAndSet(this, node);
    }
    
    protected final LinkedQueueAtomicNode<E> lvConsumerNode() {
        return consumerNode;
    }

    protected final LinkedQueueAtomicNode<E> lpConsumerNode() {
        return lvConsumerNode();
    }
    
    protected final void spConsumerNode(LinkedQueueAtomicNode<E> node) {
        C_NODE_UPDATER.lazySet(this, node);
    }
}
