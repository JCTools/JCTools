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
    protected final static long P_NODE_OFFSET;

    static {
        try {
            P_NODE_OFFSET = UNSAFE
                    .objectFieldOffset(MpscIntrusiveLinkedQueueProducerNodeRef.class.getDeclaredField("producerNode"));
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected volatile Node producerNode;

    protected final Node lvProducerNode() {
        return producerNode;
    }
}

abstract class MpscIntrusiveLinkedQueuePad1 extends MpscIntrusiveLinkedQueueProducerNodeRef {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscIntrusiveLinkedQueueConsumerNodeRef extends MpscIntrusiveLinkedQueuePad1 {
    protected final static long C_NODE_OFFSET;

    static {
        try {
            C_NODE_OFFSET = UNSAFE
                    .objectFieldOffset(MpscIntrusiveLinkedQueueConsumerNodeRef.class.getDeclaredField("consumerNode"));
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected final Node stub = new NodeImpl();
    protected Node consumerNode;

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
        producerNode = consumerNode = stub;
    }

    public boolean offer(Node node) {
        if (node == null) {
            throw new NullPointerException();
        }
        node.setNext(null);
        Node prev = xchgHead(node);
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

    public int size() {
        Node tail = this.lpConsumerNode();
        if (tail == this.stub) {
            tail = tail.getNext();
        }

        int size = 0;
        while (tail != null) {
            tail = tail.getNext();
            size++;
        }
        return size;
    }

    public boolean isEmpty() {
        return peek() == null;
    }

    private Node xchgHead(Node node) {
        // TODO: add support for JDK < 8 per org.jctools.queues.MpscLinkedQueue / MpscLinkedQueue8
        return (Node) UNSAFE.getAndSetObject(this, P_NODE_OFFSET, node);
    }
}