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
package org.jctools.queues.intrusive;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * Intrusive MPSC queue implementation based on <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/intrusive-mpsc-node-based-queue">Intrusive
 * MPSC node-based queue</a> as presented on <a href="http://www.1024cores.net">1024cores</a> by Dmitry Vyukov.
 *
 * @see Node
 */
abstract class MpscIntrusiveLinkedQueuePad0 {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;
}

abstract class MpscIntrusiveLinkedQueueProducerNodeRef extends MpscIntrusiveLinkedQueuePad0 {
    private final static long P_NODE_OFFSET;
    static {
        try {
            P_NODE_OFFSET = UNSAFE
                    .objectFieldOffset(MpscIntrusiveLinkedQueueProducerNodeRef.class.getDeclaredField("producerNode"));
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile Node producerNode;

    protected final Node lvProducerNode() {
        return producerNode;
    }
    protected final Node xchgProducerNode(Node node) {
        // TODO: add support for JDK < 8 per org.jctools.queues.MpscLinkedQueue / MpscLinkedQueue8
        return (Node) UNSAFE.getAndSetObject(this, P_NODE_OFFSET, node);
    }
}

abstract class MpscIntrusiveLinkedQueuePad1 extends MpscIntrusiveLinkedQueueProducerNodeRef {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscIntrusiveLinkedQueueConsumerNodeRef extends MpscIntrusiveLinkedQueuePad1 {
    private final static long C_NODE_OFFSET;
    static {
        try {
            C_NODE_OFFSET = UNSAFE
                    .objectFieldOffset(MpscIntrusiveLinkedQueueConsumerNodeRef.class.getDeclaredField("consumerNode"));
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private Node consumerNode;

    protected final Node stub = new NodeImpl();

    protected final void spConsumerNode(Node node) {
        consumerNode = node;
    }

    protected final Node lvConsumerNode() {
        return (Node) UNSAFE.getObjectVolatile(this, C_NODE_OFFSET);
    }

    protected final Node lpConsumerNode() {
        return consumerNode;
    }
}
public class MpscIntrusiveLinkedQueue extends MpscIntrusiveLinkedQueueConsumerNodeRef {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpscIntrusiveLinkedQueue() {
        super();
        spConsumerNode(stub);
        xchgProducerNode(stub);
    }

    public boolean offer(Node node) {
        if (node == null) {
            throw new NullPointerException();
        }
        node.setNext(null);
        Node prev = xchgProducerNode(node);
        // bubble potential
        prev.setNext(node);
        return true;
    }

    public Node poll() {
        Node cNode = this.lpConsumerNode();
        Node next = cNode.getNext();

        if (cNode == this.stub) {
            // consumer is stub, and next is null means queue is empty
            if (next == null) {
                return null;
            }
            
            // next is not null, we see a way out of stub, cNode is swapped for next and start again
            this.spConsumerNode(next);
            cNode = next;
            next = next.getNext();
        }
        // cNode is not stub AND next is not null
        if (next != null) {
            this.spConsumerNode(next);
            // prevent GC nepotism, signal consumed to size
            cNode.setNext(stub);
            return cNode;
        }

        Node pNode = this.lvProducerNode();
        // doesn't this imply a bubble?
        if (cNode != pNode) {
            return null;
        }

        offer(stub);
        next = cNode.getNext();
        if (next != null) {
            this.spConsumerNode(next);
            // prevent GC nepotism, signal consumed to size
            cNode.setNext(stub);
            return cNode;
        }

        return null;
    }

    public Node peek() {
        final Node tail = this.lpConsumerNode();

        if (tail == stub) {
            return tail.getNext();
        } else {
            return tail;
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public void clear() {
        while (poll() != null);
    }

    /**
     * This is an O(n) operation as we run through all the nodes and count them.<br>
     * The accuracy of the value returned by this method is subject to races with producer/consumer threads. In
     * particular when racing with the consumer thread this method may under estimate the size.<br>
     * Note that passing nodes between queues, or concurrent requeuing of nodes can cause this method to return strange
     * values.
     */
    public int size() {
        // Read consumer first, this is important because if the producer is node is 'older' than the consumer
        // the consumer may overtake it (consume past it) invalidating the 'snapshot' notion of size.
        final Node stub = this.stub;
        Node chaserNode = lvConsumerNode();
        if (chaserNode == stub) {
            chaserNode = chaserNode.getNext();
        }

        final Node producerNode = lvProducerNode();
        int size = 0;
        // must chase the nodes all the way to the producer node, but there's no need to count beyond expected head.
        while (chaserNode != null && chaserNode != stub &&
               size < Integer.MAX_VALUE) // stop at max int
        {
            if (chaserNode == producerNode) {
                return size + 1;
            }
            chaserNode = chaserNode.getNext();
            size++;
        }
        return size;
    }

    public boolean isEmpty() {
        return peek() == null;
    }

}