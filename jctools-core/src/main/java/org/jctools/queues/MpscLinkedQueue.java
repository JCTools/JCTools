package org.jctools.queues;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class MpscLinkedQueuePad0<E> extends AbstractQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueueProducerNodeRef<E> extends MpscLinkedQueuePad0<E> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<MpscLinkedQueueProducerNodeRef, LinkedQueueNode> UPDATER = AtomicReferenceFieldUpdater
            .newUpdater(MpscLinkedQueueProducerNodeRef.class, LinkedQueueNode.class, "producerNode");
    protected volatile LinkedQueueNode<E> producerNode;

    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal) {
        return (LinkedQueueNode<E>) UPDATER.getAndSet(this, newVal);
    }
}

abstract class MpscLinkedQueuePad1<E> extends MpscLinkedQueueProducerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueueConsumerNodeRef<E> extends MpscLinkedQueuePad1<E> {
    protected LinkedQueueNode<E> consumerNode;
}

/**
 * This is a direct Java port of the MPSC algorithm as presented <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on
 * 1024 Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory
 * model and layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>Use {@link AtomicReferenceFieldUpdater} to provide XCHG functionality to the best of the JDK ability.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references.
 * From this point follow the notes on offer/poll.
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public final class MpscLinkedQueue<E> extends MpscLinkedQueueConsumerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscLinkedQueue() {
        producerNode = new LinkedQueueNode<>();
        consumerNode = producerNode;
        consumerNode.soNext(null); // this ensures correct construction: StoreStore
    }

    /**
     * Offer is allowed from multiple threads.<br>
     * Offer allocates a new node and:
     * <ol>
     * <li>Swaps it atomically with current producer node (only one producer 'wins')
     * <li>Sets the new node as the node following from the swapped producer node
     * </ol>
     * This works because each producer is guaranteed to 'plant' a new node and link the old node. No 2
     * producers can get the same producer node as part of XCHG guarantee.
     * 
     * @see MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public boolean offer(E e) {
        if (e == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        LinkedQueueNode<E> n = new LinkedQueueNode<E>(e);
        LinkedQueueNode<E> prev = xchgProducerNode(n); // LOCK XCHG in JDK8, a CAS loop in JDK 7/6
        // Should a producer thread get interrupted here the chain WILL be broken until that thread is resumed
        // and completes the store in prev.next.
        prev.soNext(n); // StoreStore
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
        LinkedQueueNode<E> n = consumerNode.lvNext();
        if (n != null) {
            consumerNode = n;
            return n.evacuateValue();
        }
        return null;
    }

    @Override
    public E peek() {
        LinkedQueueNode<E> n = consumerNode.lvNext();
        if (n != null) {
            return n.lvValue();
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
        while ((temp = temp.lvNext()) != null) {
            size++;
        }
        return size;
    }
}
