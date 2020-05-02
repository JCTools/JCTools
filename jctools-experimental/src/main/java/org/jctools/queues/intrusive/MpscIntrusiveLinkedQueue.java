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
import static org.jctools.util.UnsafeAccess.fieldOffset;

/**
 * Intrusive MPSC queue implementation based on <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/intrusive-mpsc-node-based-queue">Intrusive
 * MPSC node-based queue</a> as presented on <a href="http://www.1024cores.net">1024cores</a> by Dmitry Vyukov.
 *
 * @see Node
 */
abstract class MpscIntrusiveLinkedQueuePad0 {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

abstract class MpscIntrusiveLinkedQueueProducerNodeRef extends MpscIntrusiveLinkedQueuePad0 {
    private final static long P_NODE_OFFSET = fieldOffset(MpscIntrusiveLinkedQueueProducerNodeRef.class, "producerNode");

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
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

abstract class MpscIntrusiveLinkedQueueConsumerNodeRef extends MpscIntrusiveLinkedQueuePad1 {
    private final static long C_NODE_OFFSET = fieldOffset(MpscIntrusiveLinkedQueueConsumerNodeRef.class, "consumerNode");

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
