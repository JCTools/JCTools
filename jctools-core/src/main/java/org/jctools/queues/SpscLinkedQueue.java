package org.jctools.queues;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class SpscLinkedQueuePad0<E> extends AbstractQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class SpscLinkedQueueProducerNodeRef<E> extends SpscLinkedQueuePad0<E> {
    protected LinkedQueueNode<E> producerNode = new LinkedQueueNode<>();;
}

abstract class SpscLinkedQueuePad1<E> extends SpscLinkedQueueProducerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class SpscLinkedQueueConsumerNodeRef<E> extends SpscLinkedQueuePad1<E> {
    protected LinkedQueueNode<E> consumerNode = producerNode;
}

/**
 * This is a weakened version of the MPSC algorithm as presented <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on
 * 1024 Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory
 * model and layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>As this is an SPSC we have no need for XCHG, an ordered store is enough.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references.
 * From this point follow the notes on offer/poll.
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public final class SpscLinkedQueue<E> extends SpscLinkedQueueConsumerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpscLinkedQueue() {
        consumerNode.soNext(null); // this ensures correct construction: StoreStore
    }
    
    /**
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
    public boolean offer(final E nextValue) {
        if(nextValue == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        final LinkedQueueNode<E> nextNode = new LinkedQueueNode<E>(nextValue);
        producerNode.soNext(nextNode);
        producerNode = nextNode;
        return true;
    }

    /**
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is empty.
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue.
     * Because null values are not allowed to be offered this is the only node with it's value set to null at
     * any one time.
     * 
     * @see MessagePassingQueue#poll(Object)
     * @see java.util.Queue#poll(java.lang.Object)
     */
    @Override
    public E poll() {
        final LinkedQueueNode<E> nextNode = consumerNode.lvNext();
        if (nextNode != null) {
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = nextNode.evacuateValue();
            consumerNode = nextNode;
            return nextValue;
        }
        return null;
    }


    @Override
    public E peek() {
        final LinkedQueueNode<E> nextNode = consumerNode.lvNext();
        if (nextNode != null) {
            return nextNode.lvValue();
        } else {
            return null;
        }
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        LinkedQueueNode<E> temp = consumerNode;
        int size = 0;
        while ((temp = temp.lvNext()) != null){
            size++;
        }
        return size;
    }
}
